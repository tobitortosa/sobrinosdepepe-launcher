package pe.sobrinosdepepe.duelos;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Los items que dibujan las pantallas: cabezas de jugador y botones.
 *
 * La cabeza lleva la cara de verdad aunque el servidor sea offline-mode, porque
 * esta **skinrestorer** instalado: el sirve las skins por nombre y el cliente las
 * dibuja igual. Sin ese mod serian todas Steve y la lista no se podria leer de un
 * vistazo, que es justo para lo que existe.
 */
public final class Caras {

	private Caras() {}

	/** Lo mismo que hace el generador de menus: un boton no muestra estadisticas. */
	private static final TooltipDisplay SIN_ADORNOS = new TooltipDisplay(false,
			new LinkedHashSet<>(List.of(
					DataComponents.ATTRIBUTE_MODIFIERS,
					DataComponents.UNBREAKABLE,
					DataComponents.ENCHANTMENTS)));

	/** La cabeza de alguien, con su nombre arriba y lo que se le quiera decir abajo. */
	public static ItemStack cabeza(ServerPlayer quien, ChatFormatting color, String... lineas) {
		ItemStack pila = new ItemStack(Items.PLAYER_HEAD);
		pila.set(DataComponents.PROFILE, ResolvableProfile.createResolved(quien.getGameProfile()));
		ponerCartel(pila, quien.getScoreboardName(), color, lineas);
		return pila;
	}

	/** Un boton comun. */
	public static ItemStack boton(Item que, String nombre, ChatFormatting color, String... lineas) {
		ItemStack pila = new ItemStack(que);
		ponerCartel(pila, nombre, color, lineas);
		return pila;
	}

	/** El vidrio del marco, sin cartel: si no, el juego dibuja un cuadrito vacio. */
	public static ItemStack relleno() {
		ItemStack pila = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
		pila.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		pila.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));
		return pila;
	}

	private static void ponerCartel(ItemStack pila, String nombre, ChatFormatting color,
			String... lineas) {
		pila.set(DataComponents.CUSTOM_NAME,
				Component.literal(nombre).withStyle(color, ChatFormatting.BOLD)
						.withStyle(estilo -> estilo.withItalic(false)));
		List<Component> lore = new ArrayList<>();
		for (String linea : lineas) {
			lore.add(Component.literal("  " + linea).withStyle(ChatFormatting.GRAY)
					.withStyle(estilo -> estilo.withItalic(false)));
		}
		pila.set(DataComponents.LORE, new ItemLore(lore));
		pila.set(DataComponents.TOOLTIP_DISPLAY, SIN_ADORNOS);
	}
}
