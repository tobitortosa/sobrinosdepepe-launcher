package pe.sobrinosdepepe.duelos;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La lista de espera: el que quiere pelear se anota y el servidor lo cruza con
 * quien aparezca.
 *
 * Es lo que faltaba para que el coliseo se use sin ponerse de acuerdo por voz.
 * Antes habia que retar a alguien por su nombre —`/pvp Chichon`— y esperar que
 * aceptara; ahora uno se anota desde el menu del lobby y, cuando hay con quien,
 * la pelea arranca sola.
 *
 * Hay tres listas, una por modo, y **cada uno esta en una sola**:
 *
 *   1v1       se cruzan de a dos, en el orden en que se anotaron.
 *   2v2       se cruzan de a cuatro: los dos primeros contra los dos siguientes.
 *             Los companeros salen por orden de llegada, no se eligen.
 *   equipos   se juntan por equipo de `/equipo` y pelea equipo contra equipo,
 *             con los que cada uno tenga anotados. El que no tiene equipo no se
 *             puede anotar a este.
 *
 * Los equipos se leen del **scoreboard** y no del mod de equipos: son dos mods
 * sueltos que no se hablan, pero `/equipo` espeja cada equipo en un equipo del
 * scoreboard llamado `sdp_<nombre>`, y eso lo ve cualquiera.
 */
public final class Cola {

	/** El prefijo con el que el mod de equipos nombra sus equipos del scoreboard. */
	private static final String PREFIJO_EQUIPOS = "sdp_";

	public enum Modo {
		UNO("1v1", 2),
		DOS("2v2", 4),
		EQUIPOS("equipos", 2);

		public final String comoSeLlama;
		/** Cuantos hacen falta para que arranque: jugadores, o equipos distintos. */
		public final int cupos;

		Modo(String comoSeLlama, int cupos) {
			this.comoSeLlama = comoSeLlama;
			this.cupos = cupos;
		}

		public static Modo porNombre(String nombre) {
			if (nombre == null) return UNO;
			for (Modo modo : values()) {
				if (modo.comoSeLlama.equalsIgnoreCase(nombre) || modo.name().equalsIgnoreCase(nombre)) {
					return modo;
				}
			}
			return null;
		}
	}

	/** Dos lados listos para pelear. */
	public record Par(Lado a, Lado b) {}

	/**
	 * Cada anotacion es un grupo de gente que entra JUNTA del mismo lado.
	 *
	 * Antes era una lista de nombres sueltos, y alcanzaba mientras el companero
	 * saliera por orden de llegada. Desde que el duo se arma a mano (ver `Mesa`)
	 * hace falta que los dos que se anotaron juntos caigan del mismo lado, y eso
	 * solo se sabe si la lista se acuerda de que vinieron juntos.
	 *
	 * Un anotado suelto es un grupo de uno, asi que no hay dos caminos.
	 */
	private final Map<Modo, List<List<String>>> anotados = new EnumMap<>(Modo.class);

	public Cola() {
		for (Modo modo : Modo.values()) anotados.put(modo, new ArrayList<>());
	}

	/** Lo anota solo. Devuelve cuantos hay en su lista. */
	public int anotar(String nombre, Modo modo) {
		return anotarLado(List.of(nombre), modo);
	}

	/** Anota a un grupo entero, que va a pelear del mismo lado. */
	public int anotarLado(List<String> quienes, Modo modo) {
		for (String quien : quienes) sacar(quien);
		anotados.get(modo).add(new ArrayList<>(quienes));
		return cuantos(modo);
	}

	/** Lo saca de donde este. Devuelve true si estaba en alguna. */
	public boolean sacar(String nombre) {
		boolean estaba = false;
		for (List<List<String>> lista : anotados.values()) {
			for (List<String> grupo : lista) {
				estaba |= grupo.removeIf(quien -> quien.equalsIgnoreCase(nombre));
			}
			// El grupo que se quedo sin nadie no espera a nadie.
			estaba |= lista.removeIf(List::isEmpty);
		}
		return estaba;
	}

	/** En que lista esta anotado, o null. */
	public Modo modoDe(String nombre) {
		for (Map.Entry<Modo, List<List<String>>> lista : anotados.entrySet()) {
			for (List<String> grupo : lista.getValue()) {
				if (grupo.stream().anyMatch(quien -> quien.equalsIgnoreCase(nombre))) {
					return lista.getKey();
				}
			}
		}
		return null;
	}

	public int cuantos(Modo modo) {
		return gente(modo).size();
	}

	public List<String> gente(Modo modo) {
		List<String> todos = new ArrayList<>();
		for (List<String> grupo : anotados.get(modo)) todos.addAll(grupo);
		return todos;
	}

	/**
	 * Arma la proxima pelea si hay con quien, y saca de la lista a los que entran.
	 * Devuelve null si todavia no alcanza.
	 *
	 * Antes de cruzar a nadie se limpia a los que se desconectaron: en una lista de
	 * espera pasan minutos entre que uno se anota y le toca.
	 */
	public Par armar(MinecraftServer servidor) {
		limpiarLosQueNoEstan(servidor);

		Par par = armarSueltos(Modo.UNO, 1);
		if (par != null) return par;
		par = armarSueltos(Modo.DOS, 2);
		if (par != null) return par;
		return armarEquipos(servidor);
	}

	/**
	 * 1v1 y 2v2: se cruzan por orden de llegada, respetando a los que vinieron
	 * juntos.
	 *
	 * Se arma un lado y despues el otro. Si el segundo no se puede completar, el
	 * primero **vuelve a la lista**: sacar media pelea de la cola dejaria a un duo
	 * esperando para siempre sin figurar en ningun lado.
	 */
	private Par armarSueltos(Modo modo, int porLado) {
		List<List<String>> lista = anotados.get(modo);
		List<List<String>> sacadas = new ArrayList<>();

		List<String> ladoA = tomar(lista, porLado, sacadas);
		if (ladoA == null) return null;
		List<String> ladoB = tomar(lista, porLado, sacadas);
		if (ladoB == null) {
			lista.addAll(0, sacadas);
			return null;
		}
		return new Par(Lado.de(ladoA), Lado.de(ladoB));
	}

	/**
	 * Saca de la lista los grupos que sumen exactamente el cupo de un lado.
	 *
	 * Un grupo que no entra se saltea en vez de cortarse: si hay un duo primero y
	 * el modo es de a uno, pasa el siguiente. Cortar un duo seria justo lo que
	 * esto viene a evitar.
	 */
	private static List<String> tomar(List<List<String>> lista, int cupo,
			List<List<String>> sacadas) {
		List<String> junta = new ArrayList<>();
		List<List<String>> usadas = new ArrayList<>();
		for (List<String> grupo : lista) {
			if (junta.size() + grupo.size() > cupo) continue;
			junta.addAll(grupo);
			usadas.add(grupo);
			if (junta.size() == cupo) {
				lista.removeAll(usadas);
				sacadas.addAll(usadas);
				return junta;
			}
		}
		return null;
	}

	/**
	 * Equipo contra equipo: se juntan los anotados por su equipo del scoreboard y
	 * pelean los dos primeros equipos que aparezcan.
	 */
	private Par armarEquipos(MinecraftServer servidor) {
		List<String> lista = gente(Modo.EQUIPOS);
		if (lista.size() < 2) return null;

		Map<String, List<String>> porEquipo = new LinkedHashMap<>();
		Scoreboard scoreboard = servidor.getScoreboard();
		for (String nombre : lista) {
			PlayerTeam equipo = scoreboard.getPlayersTeam(nombre);
			if (equipo == null || !equipo.getName().startsWith(PREFIJO_EQUIPOS)) continue;
			porEquipo.computeIfAbsent(equipo.getName(), cual -> new ArrayList<>()).add(nombre);
		}
		if (porEquipo.size() < 2) return null;

		List<String> nombres = new ArrayList<>(porEquipo.keySet());
		String unEquipo = nombres.get(0);
		String otroEquipo = nombres.get(1);
		List<String> deUno = porEquipo.get(unEquipo);
		List<String> deOtro = porEquipo.get(otroEquipo);

		deUno.forEach(this::sacar);
		deOtro.forEach(this::sacar);
		return new Par(
				Lado.deEquipo(deUno, comoSeLlama(scoreboard, unEquipo)),
				Lado.deEquipo(deOtro, comoSeLlama(scoreboard, otroEquipo)));
	}

	/** El nombre lindo del equipo, o el del scoreboard sin el prefijo. */
	private static String comoSeLlama(Scoreboard scoreboard, String equipo) {
		PlayerTeam team = scoreboard.getPlayerTeam(equipo);
		if (team != null && team.getDisplayName() != null) {
			String lindo = team.getDisplayName().getString();
			if (!lindo.isBlank()) return lindo;
		}
		return equipo.substring(PREFIJO_EQUIPOS.length());
	}

	/** El equipo de alguien, o null si no tiene. Lo usa el comando para avisar. */
	public static String equipoDe(MinecraftServer servidor, String nombre) {
		PlayerTeam equipo = servidor.getScoreboard().getPlayersTeam(nombre);
		if (equipo == null || !equipo.getName().startsWith(PREFIJO_EQUIPOS)) return null;
		return comoSeLlama(servidor.getScoreboard(), equipo.getName());
	}

	private void limpiarLosQueNoEstan(MinecraftServer servidor) {
		for (List<List<String>> lista : anotados.values()) {
			for (List<String> grupo : lista) {
				grupo.removeIf(nombre -> servidor.getPlayerList().getPlayerByName(nombre) == null);
			}
			lista.removeIf(List::isEmpty);
		}
	}

	/** Cuantos hay anotados en cada lista, para el menu y el cartel. */
	public Map<Modo, Integer> comoEsta() {
		Map<Modo, Integer> cuenta = new EnumMap<>(Modo.class);
		for (Modo modo : Modo.values()) cuenta.put(modo, cuantos(modo));
		return cuenta;
	}

	public static String enMinuscula(String nombre) {
		return nombre.toLowerCase(Locale.ROOT);
	}

	/** Los que estan anotados en cualquier lista. */
	public boolean estaAnotado(String nombre) {
		return modoDe(nombre) != null;
	}

	/** Los jugadores conectados de una lista, para avisarles. */
	public List<ServerPlayer> conectadosDe(MinecraftServer servidor, Modo modo) {
		List<ServerPlayer> gente = new ArrayList<>();
		for (String nombre : gente(modo)) {
			ServerPlayer quien = servidor.getPlayerList().getPlayerByName(nombre);
			if (quien != null) gente.add(quien);
		}
		return gente;
	}
}
