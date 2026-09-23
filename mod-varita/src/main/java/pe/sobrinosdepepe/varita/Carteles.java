package pe.sobrinosdepepe.varita;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

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

	// -------------------------------------------------------------- los modos

	public static Component noEstasEnNinguna() {
		return marca()
				.append(Component.literal("No estás parado adentro de ninguna zona. ")
						.withStyle(ChatFormatting.RED))
				.append(Component.literal("Metete adentro y volvé a escribirlo.")
						.withStyle(ChatFormatting.GRAY));
	}

	public static Component modoCambiado(Zonas.Zona zona, boolean queda) {
		String que = queda ? "no van a entrar más bichos." : "vuelven a entrar bichos.";
		return marca()
				.append(Component.literal("Zona " + zona.id()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" (" + zona.dueno() + ", " + zona.ancho() + "x"
						+ zona.largo() + "): ").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(que)
						.withStyle(queda ? ChatFormatting.GREEN : ChatFormatting.GRAY));
	}

	public static Component losModos(List<Zonas.Zona> zonas) {
		if (zonas.isEmpty()) {
			return marca().append(Component.literal("Ninguna zona está sin bichos.")
					.withStyle(ChatFormatting.GRAY));
		}

		MutableComponent cartel = marca()
				.append(Component.literal("Las zonas sin bichos:").withStyle(ChatFormatting.GRAY));
		for (Zonas.Zona zona : zonas) {
			cartel.append(Component.literal("\n  " + zona.id()).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" (" + zona.dueno() + ")")
							.withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal("  " + zona.ancho() + "x" + zona.largo())
				.withStyle(ChatFormatting.DARK_GRAY));
		}
		return cartel;
	}

	private static MutableComponent marca() {
		return Component.literal("[Varita] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
	}
}
