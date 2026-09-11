package pe.sobrinosdepepe.acceso;

import java.util.List;
import java.util.Locale;

/**
 * Los mods con los que no se juega aca: los clientes de trampas.
 *
 * El cliente le pasa al servidor la lista de los mods que tiene cargados, y esto
 * busca los que no van. Es la lista corta y a mano de los que existen de verdad y
 * se bajan de un video de YouTube en dos minutos: Meteor, Wurst, los xray y los
 * que apuntan solos. No es un antivirus ni pretende serlo.
 *
 * **Por que una lista de prohibidos y no una de permitidos.** La de permitidos
 * seria mas fuerte, pero tendria que salir del pack publicado, y el dia que se
 * publica un pack nuevo el servidor se quedaria con la lista vieja y echaria a
 * todos los que ya se actualizaron. Una lista de prohibidos nunca hace eso: lo
 * peor que puede pasar es que no reconozca un mod nuevo, y para eso esta el log,
 * que anota los mods de todos los que entran.
 *
 * **Lo que esto no puede hacer.** La lista la manda el cliente, asi que el que se
 * tome el trabajo de tocar nuestro mod para que mienta, pasa. Eso no es un
 * descuido: no hay forma de que el servidor sepa que hay del otro lado sin
 * preguntarle al otro lado. Lo que si es imposible de esquivar, porque vive
 * entero adentro del servidor, es el anti-xray y el anticheat de movimiento.
 *
 * Si aparece uno nuevo se agrega aca y se vuelve a compilar y subir el mod. No
 * esta en la config a proposito: es conocimiento, no una preferencia.
 */
public final class Tramposos {
	private Tramposos() {}

	/**
	 * Alcanza con que el nombre del mod contenga alguno de estos. Son palabras que
	 * no aparecen en ningun mod legitimo, asi que no hay riesgo de echar a nadie
	 * por casualidad: "xray" no esta adentro de "sodium" ni de "sound_physics".
	 */
	private static final List<String> PEDAZOS = List.of(
			"xray", "killaura", "aimbot", "autoclicker", "wallhack", "nuker",
			"seedcracker", "baritone", "freecam", "autototem", "autocrystal",
			"bhop", "noclip", "speedhack", "scaffold", "aurabot");

	/**
	 * Estos tienen que ser el nombre entero del mod. Van aparte de los de arriba
	 * porque como pedazo sueltos si podrian aparecer en un mod que no tiene nada
	 * que ver: hay mods honestos que se llaman "impact-sounds" o "future-blocks".
	 */
	private static final List<String> ENTEROS = List.of(
			"meteor-client", "wurst", "impact", "sigma", "liquidbounce", "rusherhack",
			"future", "trollhack", "diamond-scanner", "x_ray", "x-to-xray",
			"cheat", "cheats", "hack", "hacks");

	/**
	 * El primer mod prohibido de la lista que mando el cliente, o null si esta
	 * limpia. Devuelve el nombre y no un booleano porque el cartel que ve el
	 * jugador le dice cual tiene que sacar: "no entras" a secas no se arregla solo.
	 *
	 * @param lista los nombres separados por coma, tal como los manda el cliente.
	 */
	public static String buscar(String lista) {
		if (lista == null || lista.isBlank()) return null;

		for (String nombre : lista.split(",")) {
			String mod = nombre.trim().toLowerCase(Locale.ROOT);
			if (mod.isEmpty()) continue;

			if (ENTEROS.contains(mod)) return mod;

			for (String pedazo : PEDAZOS) {
				if (mod.contains(pedazo)) return mod;
			}
		}

		return null;
	}
}
