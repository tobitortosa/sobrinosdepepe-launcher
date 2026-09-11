package pe.sobrinosdepepe.varita;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Lo que lee el que usa la varita. Son de una linea y van al chat: la varita se
 * usa con el juego abierto y mirando al bicho, no leyendo un menu.
 *
 * El nombre del bicho va como componente y no como texto: asi Minecraft lo
 * traduce al idioma de cada uno, y un caballo con nombre propio se lee con su
 * nombre.
 */
public final class Carteles {
	private Carteles() {}

	public static Component cambio(Component bicho, boolean protegido) {
		return marca()
				.append(bicho.copy().withStyle(ChatFormatting.WHITE))
				.append(protegido
						? Component.literal(" ya no lo puede matar nadie.").withStyle(ChatFormatting.GREEN)
						: Component.literal(" vuelve a ser un bicho normal.").withStyle(ChatFormatting.GRAY));
	}

	public static Component jugadorNo() {
		return marca()
				.append(Component.literal("A un jugador no. ").withStyle(ChatFormatting.RED))
				.append(Component.literal("Acá se puede matar a cualquiera, y así tiene que ser.")
						.withStyle(ChatFormatting.GRAY));
	}

	private static net.minecraft.network.chat.MutableComponent marca() {
		return Component.literal("[Varita] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
	}
}
