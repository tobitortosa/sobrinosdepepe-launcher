package pe.sobrinosdepepe.duelos;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Todo lo que un jugador tenia antes de entrar a la arena, para devolverselo
 * despues tal cual.
 *
 * Esto es lo mas delicado de todo el mod: adentro de la arena se pelea con un kit
 * prestado, asi que al empezar el duelo se le vacia el inventario de verdad. Si
 * esta copia se pierde, el jugador pierde todo lo que tenia, y en un servidor
 * donde la netherita se compra con shards eso no se arregla con un perdon.
 *
 * Por eso la copia **tambien se escribe en el archivo** en cuanto empieza el
 * duelo, y se borra recien cuando el inventario ya volvio a su dueño. Si el
 * servidor se cae en el medio de una pelea —que en este servidor pasa, se queda
 * sin RAM— al arrancar de nuevo las copias siguen ahi y cada uno recupera lo suyo
 * al conectarse.
 *
 * Los efectos de pocion son lo unico que NO va al archivo. Se guardan en memoria
 * y vuelven al terminar el duelo, pero una caida del servidor se los lleva. Es la
 * unica cosa de esta lista que se puede volver a tomar.
 */
public final class Guardado {
	private static final Logger LOG = LoggerFactory.getLogger("duelosdepepe");

	public final String jugador;
	public final List<ItemStack> inventario;
	public final ResourceKey<Level> mundo;
	public final Vec3 donde;
	public final float giro;
	public final float mira;
	public final float vida;
	public final int comida;
	public final float saturacion;
	public final int nivelXp;
	public final float progresoXp;
	public final int totalXp;
	/** Solo en memoria: no se escribe en el archivo. */
	public final List<MobEffectInstance> efectos;

	private Guardado(String jugador, List<ItemStack> inventario, ResourceKey<Level> mundo, Vec3 donde,
			float giro, float mira, float vida, int comida, float saturacion,
			int nivelXp, float progresoXp, int totalXp, List<MobEffectInstance> efectos) {
		this.jugador = jugador;
		this.inventario = inventario;
		this.mundo = mundo;
		this.donde = donde;
		this.giro = giro;
		this.mira = mira;
		this.vida = vida;
		this.comida = comida;
		this.saturacion = saturacion;
		this.nivelXp = nivelXp;
		this.progresoXp = progresoXp;
		this.totalXp = totalXp;
		this.efectos = efectos;
	}

	/**
	 * La foto de como esta el jugador ahora mismo.
	 *
	 * El inventario se recorre por indice y no por las listas de adentro: en 26.1
	 * `Inventory.getContainerSize()` cuenta los 36 casilleros mas la armadura y la
	 * mano de atras, y `getItem(i)` sabe a cual de las dos partes va cada indice.
	 * Recorrer `getNonEquipmentItems()` se comeria la armadura puesta, que es
	 * justo lo que mas caro sale perder.
	 */
	public static Guardado de(ServerPlayer jugador) {
		List<ItemStack> copia = new ArrayList<>();
		for (int i = 0; i < jugador.getInventory().getContainerSize(); i++) {
			copia.add(jugador.getInventory().getItem(i).copy());
		}

		List<MobEffectInstance> efectos = new ArrayList<>();
		for (MobEffectInstance efecto : jugador.getActiveEffects()) {
			efectos.add(new MobEffectInstance(efecto));
		}

		return new Guardado(jugador.getScoreboardName(), copia,
				jugador.level().dimension(), jugador.position(),
				jugador.getYRot(), jugador.getXRot(),
				jugador.getHealth(), jugador.getFoodData().getFoodLevel(),
				jugador.getFoodData().getSaturationLevel(),
				jugador.experienceLevel, jugador.experienceProgress, jugador.totalExperience,
				efectos);
	}

	/**
	 * Le devuelve todo: el inventario, donde estaba parado, la vida, la comida y
	 * la experiencia.
	 *
	 * El inventario se escribe casillero por casillero y no con `add`, asi cada
	 * cosa vuelve al lugar exacto en el que estaba. Que la espada vuelva al slot 1
	 * no es un detalle: el que sale de una pelea quiere su barra como la tenia.
	 */
	public void devolverA(ServerPlayer jugador, MinecraftServer servidor) {
		for (int i = 0; i < jugador.getInventory().getContainerSize(); i++) {
			jugador.getInventory().setItem(i,
					i < inventario.size() ? inventario.get(i) : ItemStack.EMPTY);
		}
		jugador.getInventory().setChanged();
		jugador.containerMenu.broadcastChanges();
		jugador.inventoryMenu.broadcastChanges();

		jugador.removeAllEffects();
		for (MobEffectInstance efecto : efectos) {
			jugador.addEffect(new MobEffectInstance(efecto));
		}

		jugador.setHealth(Math.max(1.0f, Math.min(vida, jugador.getMaxHealth())));
		jugador.getFoodData().setFoodLevel(comida);
		jugador.getFoodData().setSaturation(saturacion);
		jugador.setRemainingFireTicks(0);
		jugador.setDeltaMovement(0, 0, 0);
		jugador.fallDistance = 0;

		// La experiencia se pone de cero: `giveExperienceLevels` suma sobre lo que
		// haya, y aca hay que dejar exactamente lo que habia.
		jugador.experienceLevel = nivelXp;
		jugador.experienceProgress = progresoXp;
		jugador.totalExperience = totalXp;
		jugador.setExperienceLevels(nivelXp);
		jugador.setExperiencePoints(0);
		jugador.experienceProgress = progresoXp;
		jugador.totalExperience = totalXp;

		ServerLevel destino = servidor.getLevel(mundo);
		if (destino != null) {
			jugador.teleportTo(destino, donde.x, donde.y, donde.z, Set.<Relative>of(), giro, mira, false);
		}
	}

	// -------------------------------------------------------------------- archivo

	public JsonObject aJson(HolderLookup.Provider registros) {
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registros);

		// Un item que no se pueda escribir se avisa FUERTE y se guarda como vacio.
		// El silencio seria lo peligroso: la copia del archivo es el respaldo contra
		// una caida del servidor, y un respaldo con agujeros que nadie miro es peor
		// que no tener respaldo. En la vida normal esto no pasa —la copia que se usa
		// al terminar el duelo es la de memoria, que son los ItemStack tal cual— y si
		// pasa, el log dice de quien y en que casillero.
		JsonArray items = new JsonArray();
		for (int i = 0; i < inventario.size(); i++) {
			final int casillero = i;
			final ItemStack pila = inventario.get(i);
			JsonElement escrito = ItemStack.OPTIONAL_CODEC.encodeStart(ops, pila)
					.resultOrPartial(porQue -> LOG.error(
							"No pude escribir el casillero {} de {} ({}): {}",
							indice(casillero), jugador, pila, porQue))
					.orElse(new JsonObject());
			items.add(escrito);
		}

		JsonObject o = new JsonObject();
		o.addProperty("jugador", jugador);
		o.add("inventario", items);
		o.addProperty("mundo", mundo.identifier().toString());
		o.addProperty("x", donde.x);
		o.addProperty("y", donde.y);
		o.addProperty("z", donde.z);
		o.addProperty("giro", giro);
		o.addProperty("mira", mira);
		o.addProperty("vida", vida);
		o.addProperty("comida", comida);
		o.addProperty("saturacion", saturacion);
		o.addProperty("nivelXp", nivelXp);
		o.addProperty("progresoXp", progresoXp);
		o.addProperty("totalXp", totalXp);
		return o;
	}

	public static Guardado deJson(HolderLookup.Provider registros, JsonObject o) {
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registros);

		String quien = o.get("jugador").getAsString();
		List<ItemStack> items = new ArrayList<>();
		for (JsonElement elemento : o.getAsJsonArray("inventario")) {
			int casillero = items.size();
			items.add(ItemStack.OPTIONAL_CODEC.parse(ops, elemento)
					.resultOrPartial(porQue -> LOG.error(
							"No pude leer el casillero {} de {}: {}. Va vacio.",
							indice(casillero), quien, porQue))
					.orElse(ItemStack.EMPTY));
		}

		return new Guardado(
				quien, items,
				ResourceKey.create(Registries.DIMENSION,
						Identifier.parse(o.get("mundo").getAsString())),
				new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble()),
				o.get("giro").getAsFloat(), o.get("mira").getAsFloat(),
				o.get("vida").getAsFloat(), o.get("comida").getAsInt(),
				o.get("saturacion").getAsFloat(),
				o.get("nivelXp").getAsInt(), o.get("progresoXp").getAsFloat(),
				o.get("totalXp").getAsInt(),
				Collections.emptyList());
	}

	/**
	 * Se guarda y se vuelve a leer, y se compara contra lo que el jugador tiene
	 * ahora mismo. Devuelve la lista de casilleros que no volvieron identicos, o
	 * vacia si esta todo bien. **No le toca nada a nadie.**
	 *
	 * Es lo que corre `/pvp probar`. La copia que se usa al terminar un duelo
	 * normal es la de memoria y no puede fallar —son los ItemStack tal cual— pero
	 * la del archivo es la que trae todo de vuelta despues de una caida del
	 * servidor, y esa si pasa por el codec de los items. Esto la prueba con el
	 * inventario de verdad de alguien, en el servidor de verdad, que es la unica
	 * forma de estar seguro: en 26.1 los componentes de los items son data-driven,
	 * asi que fuera del juego ni siquiera se puede armar un ItemStack.
	 */
	public static List<String> probarLaIdaYVuelta(ServerPlayer quien, MinecraftServer servidor) {
		Guardado ahora = de(quien);
		String texto = new com.google.gson.GsonBuilder().create()
				.toJson(ahora.aJson(servidor.registryAccess()));
		Guardado vuelta = deJson(servidor.registryAccess(),
				com.google.gson.JsonParser.parseString(texto).getAsJsonObject());

		List<String> problemas = new ArrayList<>();
		if (vuelta.inventario.size() != ahora.inventario.size()) {
			problemas.add("volvieron " + vuelta.inventario.size() + " casilleros de "
					+ ahora.inventario.size());
			return problemas;
		}
		for (int i = 0; i < ahora.inventario.size(); i++) {
			ItemStack fue = ahora.inventario.get(i);
			ItemStack volvio = vuelta.inventario.get(i);
			if (ItemStack.matches(fue, volvio)) continue;
			problemas.add("casillero " + indice(i) + ": "
					+ (fue.isEmpty() ? "(vacio)" : fue.getCount() + "x " + fue.getHoverName().getString())
					+ " volvio como "
					+ (volvio.isEmpty() ? "(VACIO)"
							: volvio.getCount() + "x " + volvio.getHoverName().getString()));
		}
		if (vuelta.vida != ahora.vida) problemas.add("la vida volvio distinta");
		if (vuelta.comida != ahora.comida) problemas.add("la comida volvio distinta");
		if (vuelta.nivelXp != ahora.nivelXp) problemas.add("la experiencia volvio distinta");
		if (!vuelta.mundo.equals(ahora.mundo)) problemas.add("el mundo volvio distinto");
		if (vuelta.donde.distanceTo(ahora.donde) > 0.001) problemas.add("la posicion volvio distinta");
		return problemas;
	}

	/** Cuantos casilleros tiene el inventario de alguien, para el cartel. */
	public static int cuantosCasilleros(ServerPlayer quien) {
		return quien.getInventory().getContainerSize();
	}

	/** Cuantos de esos casilleros tienen algo. */
	public static int cuantosLlenos(ServerPlayer quien) {
		int llenos = 0;
		for (int i = 0; i < quien.getInventory().getContainerSize(); i++) {
			if (!quien.getInventory().getItem(i).isEmpty()) llenos++;
		}
		return llenos;
	}

	/**
	 * Como se llama un casillero para el que lee el log. Los 36 primeros son el
	 * inventario y los de atras la armadura y la mano de atras, que son los que de
	 * verdad importa saber si se perdieron.
	 */
	private static String indice(int i) {
		return i < 36 ? String.valueOf(i) : i + " (armadura o mano de atras)";
	}
}
