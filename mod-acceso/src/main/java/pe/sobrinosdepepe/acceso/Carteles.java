package pe.sobrinosdepepe.acceso;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Los carteles que ve el que no entra. Son la unica explicacion que va a leer,
 * asi que cada uno dice que paso y que tiene que hacer, y ninguno dice "error".
 *
 * El link no se escribe a mano en ningun lado: sale de la config del servidor,
 * que a su vez sale de SITE_URL en el .env del backend.
 */
public final class Carteles {
	private Carteles() {}

	/** Cliente sin nuestro mod: el que entro por TLauncher o por el multijugador de siempre. */
	public static Component sinLauncher(String link) {
		return armar(
				"Este servidor se juega con el launcher.",
				"Descargalo acá, con las instrucciones:",
				link);
	}

	/**
	 * Tiene el mod pero abrio el juego por otro lado: copio la carpeta de mods, o
	 * abrio el launcher y despues entro desde el menu de otro Minecraft.
	 */
	public static Component sinTicket(String link) {
		return armar(
				"Abriste el juego sin el launcher.",
				"Cerrá Minecraft y entrá con el botón JUGAR del launcher.",
				link);
	}

	/** El launcher se lo dio hace rato y ya no vale. Se arregla apretando JUGAR otra vez. */
	public static Component vencido(String link) {
		return armar(
				"Tu permiso de entrada venció.",
				"Cerrá Minecraft y volvé a apretar JUGAR en el launcher.",
				link);
	}

	/** La firma no cierra: un ticket inventado, de otro jugador, o de otro servidor. */
	public static Component invalido(String link) {
		return armar(
				"Tu permiso de entrada no es válido.",
				"Cerrá Minecraft y volvé a apretar JUGAR en el launcher.",
				link);
	}

	/**
	 * Al servidor le falta la config. No es culpa del jugador y no tiene nada que
	 * hacer: el aviso es para que nos lo pueda contar.
	 */
	public static Component sinConfigurar() {
		return titulo()
				.append(Component.literal("El servidor está sin configurar y no deja entrar a nadie.\n\n")
						.withStyle(ChatFormatting.WHITE))
				.append(Component.literal("No es tu culpa. Avisale a Pepe.")
						.withStyle(ChatFormatting.GRAY));
	}

	private static Component armar(String motivo, String instruccion, String link) {
		MutableComponent cartel = titulo()
				.append(Component.literal(motivo + "\n\n").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(instruccion + "\n").withStyle(ChatFormatting.GRAY));

		if (link != null && !link.isBlank()) {
			cartel = cartel.append(Component.literal(link).withStyle(ChatFormatting.YELLOW));
		}

		return cartel;
	}

	/**
	 * El titulo va como hijo y no como estilo del componente de arriba porque en
	 * Minecraft los hijos heredan el estilo del padre: si la negrita estuviera en
	 * la raiz, el cartel entero saldria en negrita.
	 */
	private static MutableComponent titulo() {
		return Component.empty()
				.append(Component.literal("SOBRINOS DE PEPE\n\n")
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
	}
}
