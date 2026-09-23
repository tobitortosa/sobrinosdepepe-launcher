package pe.sobrinosdepepe.lobby;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * La perla del menu: lo unico que se tiene en la mano adentro del lobby.
 *
 * Click derecho y abre el menu de juegos. No se lanza: el click derecho se cancela
 * antes de que el juego la tire, asi que la perla no se gasta ni te teletransporta.
 *
 * El menu es un menu de cofre de Inventory Menu, el mismo sistema con el que estan
 * hechos los otros doce menus del servidor (`servidor/generar-menus.py`). Se abre
 * corriendo `/menu juegos` en nombre del jugador y con todos los permisos, para no
 * depender de lo que LuckPerms le haya dado al grupo default: el jugador no
 * escribio ningun comando, apreto un boton.
 */
final class Perla {

	/**
	 * El menu que abre, en el datapack: data/sdp/menu/juegos.json.
	 *
	 * Va con el namespace adelante. Sin el, Inventory Menu contesta "the menu
	 * doesn't exist": los ids de los menus son `sdp:<nombre>`, igual que en los
	 * comandos de Melius (`servidor/comandos/*.json`) y en los botones de volver.
	 */
	/**
	 * El menu de siempre, el que se abre estando en el lobby.
	 *
	 * Va con el namespace adelante. Sin el, Inventory Menu contesta "the menu
	 * doesn't exist": los ids de los menus son `sdp:<nombre>`, igual que en los
	 * comandos de Melius (`servidor/comandos/*.json`) y en los botones de volver.
	 */
	private static final String MENU = "menu sdp:juegos";

	/**
	 * El mismo menu pero con el boton de volver al lobby, para el que NO esta en
	 * el lobby.
	 *
	 * Son dos archivos y no uno con el boton escondido porque los menus de cofre
	 * son JSON fijo: no saben donde esta parado el que los abre. Lo unico que
	 * sabe eso es este mod, asi que la eleccion se hace aca, que es el unico lugar
	 * donde se puede hacer.
	 */
	private static final String MENU_AFUERA = "menu sdp:juegos_afuera";

	/** El nombre es lo que la distingue de una perla de verdad. */
	private static final String NOMBRE = "Menú de juegos";

	private Perla() {
	}

	/** La que se le pone en la mano al entrar al lobby. */
	static ItemStack nueva() {
		ItemStack perla = new ItemStack(Items.ENDER_PEARL);
		perla.set(DataComponents.CUSTOM_NAME, Component.literal(NOMBRE)
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
		perla.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("Click derecho para abrirlo")
						.withStyle(ChatFormatting.GRAY))));
		return perla;
	}

	/**
	 * Si esta pila es la perla del menu, y no una perla de ender de verdad.
	 *
	 * **Se mira el nombre.** Antes alcanzaba con que fuera una ender pearl, con el
	 * argumento de que adentro del lobby el inventario esta vacio y entonces
	 * cualquier perla es esta. El argumento vale adentro del lobby y en ningun
	 * otro lado, y esto se pregunta en otros lados: `alSurvival` le saca "las
	 * perlas" al que vuelve sin copia guardada, y asi le sacaba las perlas de
	 * ender de verdad, que en un servidor de PvP son de las cosas mas caras que
	 * uno lleva encima.
	 */
	static boolean es(ItemStack pila) {
		if (!pila.is(Items.ENDER_PEARL)) return false;
		Component nombre = pila.get(DataComponents.CUSTOM_NAME);
		return nombre != null && NOMBRE.equals(nombre.getString());
	}

	static void abrirMenu(ServerPlayer quien) {
		MinecraftServer servidor = quien.level().getServer();
		if (servidor == null) {
			return;
		}
		servidor.getCommands().performPrefixedCommand(
				quien.createCommandSourceStack()
						.withPermission(PermissionSet.ALL_PERMISSIONS)
						.withSuppressedOutput(),
				LobbyServidor.enLobby(quien) ? MENU : MENU_AFUERA);
	}
}
