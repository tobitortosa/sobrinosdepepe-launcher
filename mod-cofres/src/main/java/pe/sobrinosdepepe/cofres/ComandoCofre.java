package pe.sobrinosdepepe.cofres;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * `/cofre <jugador>`, el unico comando del mod.
 *
 * Abre el cofre de ender de cualquiera y se puede sacar y meter cosas: es el
 * cofre de verdad, no una foto. Con el jugador conectado se trabaja sobre su
 * cofre en vivo. Con el desconectado —el caso que importa, el del baneado— se
 * lee su archivo, y al cerrar la ventana se vuelve a escribir.
 *
 * **Lo que sacas se lo sacas.** Esa es toda la gracia de escribir el archivo de
 * vuelta: si la ventana trabajara sobre una copia, sacar un item seria fabricar
 * uno nuevo y el baneado se quedaria igual con el suyo. Aca no hay item que
 * aparezca de la nada.
 *
 * Cuatro cosas que tienen su trampa:
 *
 * - **El UUID se calcula del nombre.** El servidor esta en offline-mode, asi que
 *   el UUID de un jugador es MD5("OfflinePlayer:" + nombre) y eso es exactamente
 *   lo que hace `UUIDUtil.createOfflinePlayerUUID`. La cache de perfiles no
 *   sirve: tiene tambien UUID reales de Mojang, de cuando alguien entro con
 *   cuenta premium, y esos no corresponden a ningun archivo.
 *
 * - **Al guardar se relee el archivo.** No se escribe el NBT que se cargo al
 *   abrir la ventana: se vuelve a leer y se le cambia unicamente `EnderItems`.
 *   Si no, todo lo demas que el archivo guarda (la posicion, la vida, la
 *   mochila) volveria al estado que tenia cuando se abrio.
 *
 * - **Si el jugador se conecto mientras tanto, no se guarda nada.** Su archivo
 *   pasa a mandarlo el servidor y escribirlo por debajo seria pisarle cosas; al
 *   desconectarse lo sobreescribiria igual. Se avisa y se deja como estaba.
 *
 * - **Se escribe a un temporal y recien despues se renombra.** Es el archivo del
 *   jugador: una escritura cortada a la mitad lo deja corrupto y se pierde todo
 *   lo que tenga esa cuenta.
 */
public final class ComandoCofre {
	private static final int RANURAS = 27;
	private static final int FILAS = 3;

	private ComandoCofre() {
	}

	public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("cofre")
				.requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
				.then(Commands.argument("jugador", StringArgumentType.word())
						.suggests((contexto, sugerencias) -> SharedSuggestionProvider.suggest(
								candidatos(contexto.getSource().getServer()), sugerencias))
						.executes(ComandoCofre::abrir)));
	}

	/** Los conectados y los baneados, que son a quienes se les mira el cofre. */
	private static List<String> candidatos(MinecraftServer servidor) {
		List<String> nombres = new ArrayList<>(List.of(servidor.getPlayerNames()));
		Collections.addAll(nombres, servidor.getPlayerList().getBans().getUserList());
		return nombres;
	}

	private static int abrir(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer admin = contexto.getSource().getPlayerOrException();
		MinecraftServer servidor = contexto.getSource().getServer();
		String nombre = StringArgumentType.getString(contexto, "jugador");

		ServerPlayer conectado = servidor.getPlayerList().getPlayerByName(nombre);
		if (conectado != null) {
			// El cofre de verdad, el mismo objeto que usa el jugador: lo que se
			// saque se lo saca en el momento y lo guarda el servidor solo.
			abrirVentana(admin, nombre + " (conectado)",
					(id, inventario) -> new ChestMenu(
							MenuType.GENERIC_9x3, id, inventario, conectado.getEnderChestInventory(), FILAS));
			return 1;
		}

		SimpleContainer contenido = new SimpleContainer(RANURAS);
		if (!copiarDelArchivo(servidor, nombre, contenido)) {
			contexto.getSource().sendFailure(Component.literal(
					"No hay nada guardado de '" + nombre + "'. Ojo con las mayusculas."));
			return 0;
		}
		abrirVentana(admin, nombre + " (desconectado)",
				(id, inventario) -> new Editor(id, inventario, contenido, servidor, nombre, admin));
		return 1;
	}

	private interface Armador {
		ChestMenu armar(int id, Inventory inventario);
	}

	private static void abrirVentana(ServerPlayer admin, String titulo, Armador armador) {
		admin.openMenu(new SimpleMenuProvider(
				(id, inventario, jugador) -> armador.armar(id, inventario),
				Component.literal("Cofre de " + titulo).withStyle(ChatFormatting.DARK_PURPLE)));
	}

	/** Devuelve false si el jugador nunca entro o el nombre esta mal escrito. */
	private static boolean copiarDelArchivo(MinecraftServer servidor, String nombre, SimpleContainer destino) {
		Path archivo = archivoDe(servidor, nombre);
		if (!Files.isRegularFile(archivo)) {
			return false;
		}
		try {
			CompoundTag datos = NbtIo.readCompressed(archivo, NbtAccounter.unlimitedHeap());
			RegistryOps<Tag> ops = servidor.registryAccess().createSerializationContext(NbtOps.INSTANCE);
			ListTag items = datos.getListOrEmpty("EnderItems");
			for (int i = 0; i < items.size(); i++) {
				CompoundTag guardado = items.getCompoundOrEmpty(i);
				int ranura = guardado.getByteOr("Slot", (byte) -1) & 255;
				if (ranura >= RANURAS) {
					continue;
				}
				ItemStack.CODEC.parse(ops, guardado).result()
						.ifPresent(item -> destino.setItem(ranura, item));
			}
			return true;
		} catch (Exception e) {
			// Un archivo a medio escribir o de una version vieja: mejor decir que
			// no hay nada que dejar al operador mirando una ventana vacia sin saber
			// si el cofre estaba vacio o si fallo la lectura.
			CofresServidor.LOG.warn("No se pudo leer el cofre de {}", nombre, e);
			return false;
		}
	}

	private static Path archivoDe(MinecraftServer servidor, String nombre) {
		UUID uuid = UUIDUtil.createOfflinePlayerUUID(nombre);
		return servidor.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat");
	}

	/**
	 * Un cofre grande que, al cerrarse, escribe lo que quedo en el archivo del
	 * jugador desconectado.
	 */
	private static final class Editor extends ChestMenu {
		private final SimpleContainer contenido;
		private final MinecraftServer servidor;
		private final String nombre;
		private final ServerPlayer admin;

		private Editor(int id, Inventory inventario, SimpleContainer contenido,
				MinecraftServer servidor, String nombre, ServerPlayer admin) {
			super(MenuType.GENERIC_9x3, id, inventario, contenido, FILAS);
			this.contenido = contenido;
			this.servidor = servidor;
			this.nombre = nombre;
			this.admin = admin;
		}

		@Override
		public void removed(Player jugador) {
			super.removed(jugador);
			guardar();
		}

		private void guardar() {
			if (servidor.getPlayerList().getPlayerByName(nombre) != null) {
				avisar("Se conecto mientras mirabas el cofre, asi que no toque nada de "
						+ nombre + ". Volve a abrirlo.");
				return;
			}
			Path archivo = archivoDe(servidor, nombre);
			Path temporal = archivo.resolveSibling(archivo.getFileName() + ".tmp");
			try {
				// Se relee para cambiarle unicamente EnderItems: el resto del
				// archivo es el jugador entero y no es asunto de esta ventana.
				CompoundTag datos = NbtIo.readCompressed(archivo, NbtAccounter.unlimitedHeap());
				datos.put("EnderItems", comoLista());
				NbtIo.writeCompressed(datos, temporal);
				Files.move(temporal, archivo,
						StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
				avisar("Guardado el cofre de " + nombre + ".");
			} catch (Exception e) {
				CofresServidor.LOG.error("No se pudo guardar el cofre de {}", nombre, e);
				avisar("NO se pudo guardar el cofre de " + nombre + ": quedo como estaba.");
			}
		}

		private ListTag comoLista() {
			RegistryOps<Tag> ops = servidor.registryAccess().createSerializationContext(NbtOps.INSTANCE);
			ListTag lista = new ListTag();
			for (int ranura = 0; ranura < RANURAS; ranura++) {
				ItemStack item = contenido.getItem(ranura);
				if (item.isEmpty()) {
					continue;
				}
				int donde = ranura;
				ItemStack.CODEC.encodeStart(ops, item).result().ifPresent(escrito -> {
					CompoundTag compuesto = (CompoundTag) escrito;
					compuesto.putByte("Slot", (byte) donde);
					lista.add(compuesto);
				});
			}
			return lista;
		}

		private void avisar(String texto) {
			admin.sendSystemMessage(Component.literal(texto).withStyle(ChatFormatting.GRAY));
		}
	}
}
