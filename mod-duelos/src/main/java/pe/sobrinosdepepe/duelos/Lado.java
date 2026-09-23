package pe.sobrinosdepepe.duelos;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Uno de los dos lados de una pelea: quienes son y como se llama el lado.
 *
 * El **nombre** es lo unico que se muestra, y por eso existe esta clase. Todo el
 * mod estaba escrito para dos jugadores: `Carteles` recibe dos String, la barra
 * dice "PEPE vs Chichon", las apuestas van a favor de uno o del otro. Dandole un
 * nombre a cada lado, un 2v2 y un equipo contra equipo entran por la misma puerta
 * que un 1v1 y no hubo que reescribir las 860 lineas de carteles:
 *
 *   1v1       "PEPE"                  vs "Chichon"
 *   2v2       "PEPE y Chichon"        vs "Felix1256 y Titit0N"
 *   equipos   "Los Pibes"             vs "Los Otros"
 *
 * Un lado esta **vivo** mientras le quede alguien que no fue eliminado y que siga
 * conectado. Cuando se queda sin nadie, pierde.
 */
public final class Lado {

	/** Los nombres de los jugadores, en el orden en el que entran a la arena. */
	public final List<String> jugadores;

	/** Como se muestra en los carteles, la barra y el chat. */
	public final String nombre;

	public Lado(List<String> jugadores, String nombre) {
		this.jugadores = List.copyOf(jugadores);
		this.nombre = nombre;
	}

	/** Un lado de uno solo: el nombre del lado es el del jugador. */
	public static Lado de(String jugador) {
		return new Lado(List.of(jugador), jugador);
	}

	/**
	 * Un lado armado con varios, sin equipo: se llama con los nombres de todos.
	 *
	 * Con dos queda "PEPE y Chichon". Con mas, "PEPE y 2 mas", porque en una barra
	 * de jefe cuatro nombres completos no entran y el que mira necesita leerlo de
	 * un vistazo, no estudiarlo.
	 */
	public static Lado de(List<String> jugadores) {
		if (jugadores.size() == 1) return de(jugadores.get(0));
		String nombre = jugadores.size() == 2
				? jugadores.get(0) + " y " + jugadores.get(1)
				: jugadores.get(0) + " y " + (jugadores.size() - 1) + " más";
		return new Lado(jugadores, nombre);
	}

	/** Un lado que es un equipo de `/equipo`: se llama como el equipo. */
	public static Lado deEquipo(List<String> jugadores, String equipo) {
		return new Lado(jugadores, equipo);
	}

	public boolean tiene(String jugador) {
		return jugadores.stream().anyMatch(n -> n.equalsIgnoreCase(jugador));
	}

	/** Los que estan conectados ahora mismo, eliminados o no. */
	public List<ServerPlayer> conectados(MinecraftServer servidor) {
		List<ServerPlayer> gente = new ArrayList<>();
		for (String nombre : jugadores) {
			ServerPlayer quien = servidor.getPlayerList().getPlayerByName(nombre);
			if (quien != null) gente.add(quien);
		}
		return gente;
	}

	/** Los que siguen en pie: conectados y no eliminados. */
	public List<ServerPlayer> vivos(MinecraftServer servidor, java.util.Set<String> eliminados) {
		List<ServerPlayer> gente = new ArrayList<>();
		for (String nombre : jugadores) {
			if (eliminados.contains(nombre.toLowerCase(Locale.ROOT))) continue;
			ServerPlayer quien = servidor.getPlayerList().getPlayerByName(nombre);
			if (quien != null) gente.add(quien);
		}
		return gente;
	}

	public int cuantos() {
		return jugadores.size();
	}

	@Override
	public String toString() {
		return nombre + " " + jugadores;
	}
}
