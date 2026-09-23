package pe.sobrinosdepepe.lobby;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lo que el jugador lee, con los colores del servidor.
 *
 * Todo lo que se le muestra al jugador va aca y no desperdigado por el codigo,
 * igual que en el mod de equipos y en el de acceso.
 */
final class Carteles {

	private Carteles() {
	}

	/** Al caer en el lobby. */
	static void bienvenida(ServerPlayer quien) {
		quien.sendSystemMessage(Component.empty()
				.append(Component.literal("\n  SOBRINOS DE PEPE\n\n")
						.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
				.append(Component.literal("  Estás en el lobby. Tus cosas están guardadas.\n")
						.withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  Click derecho con la perla para abrir el menú,\n")
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal("  o apretá acá:\n\n")
						.withStyle(ChatFormatting.GRAY))
				.append(boton())
				.append(Component.literal("\n")));
	}

	/**
	 * Cuando no se pudo escribir el archivo con sus cosas. No se lo mueve y se le
	 * dice por que: es la unica forma de que alguien avise antes de que se pierda
	 * algo.
	 */
	static void noPudeGuardar(ServerPlayer quien) {
		quien.sendSystemMessage(titulo("No pude guardar tus cosas, así que te dejo "
				+ "donde estás. Avisale a Pepe.", ChatFormatting.RED));
	}

	/** El boton verde. Tambien anda escribiendo /survival a mano. */
	private static MutableComponent boton() {
		return Component.literal("  [ ENTRAR AL SURVIVAL ]")
				.withStyle(estilo -> estilo
						.withColor(ChatFormatting.GREEN)
						.withBold(true)
						.withClickEvent(new ClickEvent.RunCommand("/survival"))
						.withHoverEvent(new HoverEvent.ShowText(
								Component.literal("Te deja donde estabas la última vez"))));
	}

	/** Cuando /survival lo devuelve a donde estaba. */
	static void deVuelta(ServerPlayer quien) {
		quien.sendSystemMessage(titulo("Volviste al Survival, donde lo dejaste.",
				ChatFormatting.WHITE));
	}

	/** El que nunca jugo no tiene a donde volver: va al spawn. */
	static void alSpawn(ServerPlayer quien) {
		quien.sendSystemMessage(titulo("Bienvenido. Arrancás en el spawn.",
				ChatFormatting.WHITE));
	}

	/** Al llegar al coliseo. Por ahora es solo para mirar. */
	/**
	 * Lo primero que lee el que entra a recorrer, y lo unico que necesita saber.
	 *
	 * Va en dos renglones y el segundo en amarillo: de espectador la perla no abre
	 * el menu —el juego no deja usar items— asi que si no lee esto se queda
	 * adentro probando el click derecho.
	 */
	static void enElColiseo(ServerPlayer quien) {
		quien.sendSystemMessage(titulo("Estás recorriendo el coliseo, de espectador: "
				+ "volás y atravesás las paredes.", ChatFormatting.WHITE));
		quien.sendSystemMessage(titulo("Escribí /lobby para volver.", ChatFormatting.YELLOW));
	}

	/** Al que se fue volando del coliseo lo devolvemos, y se lo decimos. */
	static void elColiseoTermina(ServerPlayer quien) {
		quien.sendSystemMessage(titulo("Hasta acá llega el coliseo. Para salir, /lobby.",
				ChatFormatting.GRAY));
	}

	static void yaEstasEnColiseo(ServerPlayer quien) {
		quien.sendSystemMessage(titulo("Ya estás en el coliseo.", ChatFormatting.GRAY));
	}

	/** Del Survival no se sale derecho al coliseo: primero el lobby. */
	static void primeroAlLobby(ServerPlayer quien) {
		quien.sendSystemMessage(titulo("Al coliseo se va desde el lobby. Escribí /lobby.",
				ChatFormatting.GRAY));
	}

	static void yaEstas(ServerPlayer quien) {
		quien.sendSystemMessage(titulo("Ya estás en el lobby.", ChatFormatting.GRAY));
	}

	static void yaEstasEnSurvival(ServerPlayer quien) {
		quien.sendSystemMessage(titulo("Ya estás en el Survival.", ChatFormatting.GRAY));
	}

	/** El /lobby no es un boton de escape en medio de una pelea. */
	static void enCombate(ServerPlayer quien) {
		quien.sendSystemMessage(titulo("Estás en combate. Esperá a que se te pase.",
				ChatFormatting.RED));
	}

	private static MutableComponent titulo(String texto, ChatFormatting color) {
		return Component.literal("[SDP] ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
				.append(Component.literal(texto).withStyle(color));
	}
}
