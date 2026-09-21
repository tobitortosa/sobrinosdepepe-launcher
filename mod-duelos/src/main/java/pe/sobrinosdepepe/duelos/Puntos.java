package pe.sobrinosdepepe.duelos;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Comparator;
import java.util.List;

/**
 * Los shards y las cuentas de duelos, que viven en el scoreboard.
 *
 * **Todo lo que se apuesta es en shards y nada en plata**, y no es una
 * preferencia: la plata de EconomyCraft no vive en ningun scoreboard y sus
 * comandos devuelven exito aunque el jugador no tenga un peso
 * (`eco removemoney` y `pay` devuelven 1 siempre). O sea que cobrarle una
 * apuesta en plata a alguien sin saldo saldria bien y el servidor regalaria la
 * diferencia. El objetivo `Shards` se lee y se escribe con un numero exacto, y
 * eso es lo que hace que una apuesta sea una apuesta.
 *
 * Los shards de un duelo no se crean ni se destruyen: los que pone el que pierde
 * son exactamente los que cobra el que gana. Lo mismo con las apuestas de la
 * gente. Es la unica forma de meter una casa de apuestas en un servidor sin
 * inflarle la economia.
 *
 * Todo se toca por NOMBRE (`ScoreHolder.forNameOnly`) y no por el jugador
 * conectado: el que apostó puede haberse ido del servidor antes de que termine
 * la pelea, y su parte lo tiene que estar esperando igual cuando vuelva.
 */
public final class Puntos {
	private Puntos() {}

	/** El mismo objetivo que ya paga el datapack por matar y por tiempo jugado. */
	public static final String SHARDS = "Shards";
	public static final String GANADOS = "sdp_duelos_g";
	public static final String PERDIDOS = "sdp_duelos_p";
	/** El reloj de la marca de pelea, que baja un tick por tick en `sdp:tick`. */
	public static final String COMBATE = "sdp_combate";

	/** Deja creados los objetivos propios. Se llama una vez, al arrancar. */
	public static void preparar(MinecraftServer servidor) {
		objetivo(servidor, GANADOS, "Duelos ganados");
		objetivo(servidor, PERDIDOS, "Duelos perdidos");
		objetivo(servidor, SHARDS, "Shards");
	}

	public static int shards(MinecraftServer servidor, String jugador) {
		return leer(servidor, jugador, SHARDS);
	}

	/**
	 * Le pone la marca de pelea del datapack, con su reloj.
	 *
	 * **La etiqueta sola no alcanza y esto costo entenderlo:** `sdp:tick` corre
	 * todos los ticks `execute as @a[tag=sdp_combate,scores={sdp_combate=..0}] run
	 * function sdp:combate_salir`, o sea que a cualquiera que tenga la etiqueta con
	 * el reloj en cero se la saca al instante. Un jugador que ya peleo alguna vez
	 * tiene el objetivo en cero, asi que poner solo la etiqueta la perdia en el
	 * tick siguiente y ademas le tiraba el cartel de "saliste de la pelea" en la
	 * cara apenas entraba a la arena.
	 */
	public static void marcarEnPelea(MinecraftServer servidor, ServerPlayer quien, int ticks) {
		quien.addTag("sdp_combate");
		servidor.getScoreboard()
				.getOrCreatePlayerScore(quien, objetivo(servidor, COMBATE, COMBATE))
				.set(ticks);
	}

	/** Le saca la marca sin el cartel de "saliste de la pelea": el duelo termino. */
	public static void sacarDePelea(MinecraftServer servidor, ServerPlayer quien) {
		quien.removeTag("sdp_combate");
		servidor.getScoreboard()
				.getOrCreatePlayerScore(quien, objetivo(servidor, COMBATE, COMBATE))
				.set(0);
	}

	public static void sumarShards(MinecraftServer servidor, String jugador, int cuanto) {
		sumar(servidor, jugador, SHARDS, cuanto);
	}

	public static void sumarGanado(MinecraftServer servidor, String jugador) {
		sumar(servidor, jugador, GANADOS, 1);
	}

	public static void sumarPerdido(MinecraftServer servidor, String jugador) {
		sumar(servidor, jugador, PERDIDOS, 1);
	}

	public static int ganados(MinecraftServer servidor, String jugador) {
		return leer(servidor, jugador, GANADOS);
	}

	public static int perdidos(MinecraftServer servidor, String jugador) {
		return leer(servidor, jugador, PERDIDOS);
	}

	/** Los que mas duelos ganaron, de mayor a menor. Para /pvp top. */
	public static List<PlayerScoreEntry> mejores(MinecraftServer servidor, int cuantos) {
		Objective objetivo = objetivo(servidor, GANADOS, "Duelos ganados");
		return servidor.getScoreboard().listPlayerScores(objetivo).stream()
				.filter(e -> e.value() > 0)
				.sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed())
				.limit(cuantos)
				.toList();
	}

	// ----------------------------------------------------------------- por adentro

	private static int leer(MinecraftServer servidor, String jugador, String cual) {
		Scoreboard scoreboard = servidor.getScoreboard();
		Objective objetivo = objetivo(servidor, cual, cual);
		return scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(jugador), objetivo).get();
	}

	private static void sumar(MinecraftServer servidor, String jugador, String cual, int cuanto) {
		Scoreboard scoreboard = servidor.getScoreboard();
		Objective objetivo = objetivo(servidor, cual, cual);
		scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(jugador), objetivo).add(cuanto);
	}

	/**
	 * El objetivo, creado si todavia no estaba.
	 *
	 * `Shards` lo crea el datapack la primera vez que alguien cobra, y los dos de
	 * duelos los crea esto. Que los cree el mod y no un script es lo que hace que
	 * el mod funcione solo en un mundo recien hecho.
	 */
	private static Objective objetivo(MinecraftServer servidor, String nombre, String titulo) {
		Scoreboard scoreboard = servidor.getScoreboard();
		Objective objetivo = scoreboard.getObjective(nombre);
		if (objetivo != null) return objetivo;
		return scoreboard.addObjective(nombre, ObjectiveCriteria.DUMMY, Component.literal(titulo),
				ObjectiveCriteria.RenderType.INTEGER, false, null);
	}
}
