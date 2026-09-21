package pe.sobrinosdepepe.duelos;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * El que manda: una pelea a la vez, los que esperan turno, y los enganches del
 * juego que hacen que un duelo no se parezca a una pelea cualquiera.
 *
 * **Una sola pelea a la vez, y es a proposito.** Hay una arena y el coliseo es
 * para mirar: si hubiera tres duelos simultaneos no habria a que apostarle ni a
 * quien mirar. Los demas hacen cola y les toca cuando termina el de adelante.
 *
 * Los cuatro enganches del juego, y por que cada uno:
 *
 *  - **la muerte** se cancela y en su lugar termina el duelo, asi el que pierde
 *    no suelta nada y el que gana no cobra la kill;
 *  - **el daño** no pasa ni de adentro para afuera ni de afuera para adentro: el
 *    de las gradas no le puede tirar una flecha al que va ganando, y el que pelea
 *    tampoco le pega a nadie de afuera;
 *  - **romper y poner bloques** adentro de la arena es solo de los dos que
 *    pelean, asi nadie tapa el agujero desde afuera ni le abre el piso a uno;
 *  - **entrar y salir del servidor**: el que se va pierde, y el que vuelve con un
 *    inventario a medio devolver lo recupera al entrar.
 */
public final class Duelos {
	private static final Logger LOG = LoggerFactory.getLogger("duelosdepepe");

	/** Una pelea que espera turno. */
	public record EnEspera(String uno, String otro, int apuesta, boolean deTorneo) {}

	private final Registro registro;
	private final Invitaciones invitaciones = new Invitaciones();
	private final Deque<EnEspera> cola = new ArrayDeque<>();
	/**
	 * Los que acaban de entrar y tienen un inventario esperandolos.
	 *
	 * Se les devuelve en el tick siguiente y no adentro del evento de entrada: ahi
	 * el juego todavia le esta mandando al cliente su posicion y su inventario de
	 * arranque, y devolverselo en el medio de eso es pelearse con el propio
	 * servidor por quien escribe ultimo.
	 */
	private final Deque<String> porDevolver = new ArrayDeque<>();
	private MinecraftServer servidor;
	private Duelo actual;
	private Torneo torneo;

	public Duelos(Registro registro) {
		this.registro = registro;
	}

	public void alArrancar(MinecraftServer servidor) {
		this.servidor = servidor;
		Puntos.preparar(servidor);
	}

	public void conTorneo(Torneo torneo) {
		this.torneo = torneo;
	}

	public Invitaciones invitaciones() {
		return invitaciones;
	}

	public Registro registro() {
		return registro;
	}

	public Duelo actual() {
		return actual;
	}

	// ------------------------------------------------------------------- enganches

	public void registrarEventos() {
		ServerTickEvents.END_SERVER_TICK.register(this::tick);
		ServerLivingEntityEvents.ALLOW_DEATH.register(this::dejarMorir);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(this::dejarPegar);
		ServerPlayerEvents.JOIN.register(this::alEntrar);
		ServerPlayerEvents.LEAVE.register(this::alSalir);
		PlayerBlockBreakEvents.BEFORE.register((mundo, quien, donde, estado, bloque) ->
				puedeTocarLaArena(quien, donde));
		UseBlockCallback.EVENT.register((quien, mundo, mano, golpe) ->
				puedeTocarLaArena(quien, golpe.getBlockPos())
						? InteractionResult.PASS : InteractionResult.FAIL);
	}

	private void tick(MinecraftServer servidor) {
		while (!porDevolver.isEmpty()) devolverLoQueQuedo(porDevolver.poll());

		if (actual != null) {
			actual.tick();
			if (actual.terminado()) {
				Duelo termino = actual;
				actual = null;
				if (termino.deTorneo && torneo != null) torneo.termino(termino);
			}
			return;
		}

		// La arena esta libre: entra el proximo de la cola que todavia se pueda.
		while (actual == null && !cola.isEmpty()) {
			EnEspera proximo = cola.poll();
			Arena arena = registro.arena();
			if (arena == null || arena.nivel(servidor) == null) {
				avisar(proximo, Carteles.sinArena());
				continue;
			}
			Duelo duelo = new Duelo(servidor, registro, arena,
					proximo.uno(), proximo.otro(), proximo.apuesta(), proximo.deTorneo());
			if (duelo.arrancar()) {
				actual = duelo;
			} else {
				avisar(proximo, Carteles.aviso("El duelo se cayo: alguno de los dos no esta."));
				if (proximo.deTorneo() && torneo != null) torneo.noPudoArrancar(proximo);
			}
		}
	}

	/**
	 * El golpe que lo mataria no lo mata: termina el duelo.
	 *
	 * Devolver false cancela la muerte pero **no le devuelve la vida**: el juego ya
	 * le puso la vida en cero antes de preguntar. Por eso hay que sanarlo a mano
	 * aca mismo, o queda parado en cero corazones y se muere con el proximo golpe
	 * de cualquier cosa.
	 */
	private boolean dejarMorir(LivingEntity quien, net.minecraft.world.damagesource.DamageSource de,
			float cuanto) {
		if (actual == null || !(quien instanceof ServerPlayer jugador)) return true;
		if (!actual.esDuelista(jugador.getScoreboardName())) return true;

		jugador.setHealth(jugador.getMaxHealth());
		jugador.setAbsorptionAmount(0);
		jugador.clearFire();
		actual.caido(jugador);
		return false;
	}

	/**
	 * Quien le puede pegar a quien mientras hay un duelo.
	 *
	 * Antes de la cuenta y despues del final los dos son intocables: en esos
	 * momentos estan quietos y no se pueden defender, asi que cualquier golpe seria
	 * gratis. Durante la pelea solo se pueden pegar entre ellos.
	 */
	private boolean dejarPegar(LivingEntity quien, net.minecraft.world.damagesource.DamageSource de,
			float cuanto) {
		if (actual == null) return true;

		Entity culpable = de.getEntity();
		boolean victimaPelea = quien instanceof Player victima
				&& actual.esDuelista(victima.getScoreboardName());
		boolean culpablePelea = culpable instanceof Player pegador
				&& actual.esDuelista(pegador.getScoreboardName());

		if (victimaPelea && actual.intocables()) return false;
		// Uno de los dos esta en el duelo y el otro no: el golpe no pasa, venga del
		// lado que venga. Los golpes sin dueño (caida, fuego, lava) pasan igual.
		if (victimaPelea && culpable != null && !culpablePelea) return false;
		if (culpablePelea && quien instanceof Player && !victimaPelea) return false;
		return true;
	}

	/** Adentro de la arena, mientras hay duelo, solo tocan bloques los que pelean. */
	private boolean puedeTocarLaArena(Player quien, net.minecraft.core.BlockPos donde) {
		if (actual == null) return true;
		Arena arena = actual.arena();
		if (!arena.esDe(quien.level()) || !arena.contiene(donde)) return true;
		return actual.esDuelista(quien.getScoreboardName());
	}

	/**
	 * Le quedo un inventario de un duelo que no termino bien: se desconecto en el
	 * medio, o el servidor se cayo con la pelea empezada. Se lo devolvemos apenas
	 * entra, en el tick siguiente.
	 */
	private void alEntrar(ServerPlayer quien) {
		if (registro.pendienteDe(quien.getScoreboardName()) == null) return;
		porDevolver.add(quien.getScoreboardName());
	}

	private void devolverLoQueQuedo(String nombre) {
		ServerPlayer quien = servidor.getPlayerList().getPlayerByName(nombre);
		Guardado copia = registro.pendienteDe(nombre);
		if (quien == null || copia == null) return;
		if (actual != null && actual.esDuelista(nombre)) return;

		copia.devolverA(quien, servidor);
		registro.olvidar(nombre);
		Puntos.sacarDePelea(servidor, quien);
		quien.sendSystemMessage(Carteles.teDevolvimosLoTuyo());
		LOG.info("Le devolvi a {} el inventario de un duelo sin terminar", nombre);
	}

	private void alSalir(ServerPlayer quien) {
		String nombre = quien.getScoreboardName();
		invitaciones.olvidar(nombre);
		// Las de torneo NO se sacan de la cola: se dejan fallar al arrancar, y ahi
		// el torneo hace pasar de ronda al que si esta. Sacandolas aca, el torneo se
		// quedaria esperando el resultado de una pelea que ya no existe.
		cola.removeIf(espera -> !espera.deTorneo()
				&& (espera.uno().equalsIgnoreCase(nombre) || espera.otro().equalsIgnoreCase(nombre)));
		if (torneo != null) torneo.seFue(nombre);
		if (actual != null && actual.esDuelista(nombre)) actual.seFue(nombre);
	}

	// -------------------------------------------------------------------- la cola

	/** Mete la pelea en la cola. Devuelve en que lugar quedo (1 es la proxima). */
	public int encolar(String uno, String otro, int apuesta, boolean deTorneo) {
		invitaciones.olvidar(uno);
		invitaciones.olvidar(otro);
		cola.add(new EnEspera(uno, otro, apuesta, deTorneo));
		return cola.size();
	}

	public boolean estaOcupado(String nombre) {
		if (actual != null && actual.esDuelista(nombre)) return true;
		return cola.stream().anyMatch(espera -> espera.uno().equalsIgnoreCase(nombre)
				|| espera.otro().equalsIgnoreCase(nombre));
	}

	public boolean hayArena() {
		return registro.arena() != null && servidor != null
				&& registro.arena().nivel(servidor) != null;
	}

	private void avisar(EnEspera espera, Component que) {
		for (String quien : new String[] {espera.uno(), espera.otro()}) {
			ServerPlayer conectado = servidor.getPlayerList().getPlayerByName(quien);
			if (conectado != null) conectado.sendSystemMessage(que);
		}
	}
}
