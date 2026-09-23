package pe.sobrinosdepepe.duelos;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
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
 *
 * Y una cosa que no es un enganche sino un barrido: **adentro de la arena no hay
 * bichos hostiles**, haya duelo o no. Se mira media vez por segundo y se van.
 *
 * Lo que este mod **no** hace es sacar a nadie de la arena. El que quiere mirar
 * de cerca entra y se queda: no puede pegar, ni que le peguen, ni tocar un
 * bloque mientras se pelea. Dejar afuera a la gente de verdad es trabajo de una
 * pared de `barrier` en el borde del ring, que se pone una vez y no se discute
 * con nadie.
 */
public final class Duelos {
	private static final Logger LOG = LoggerFactory.getLogger("duelosdepepe");

	/** Cada cuantos ticks se barre la arena. 10 son media vez por segundo. */
	private static final int CADA_CUANTO_LIMPIO = 10;

	/** Una pelea que espera turno. */
	public record EnEspera(String uno, String otro, int apuesta, String clase,
			boolean deTorneo) {}

	private final Registro registro;
	private final Invitaciones invitaciones = new Invitaciones();
	private final Deque<EnEspera> cola = new ArrayDeque<>();

	/** La lista de espera: el que se anota sin rival y lo cruzamos con quien haya. */
	private final Cola espera = new Cola();

	/** Los que estan mirando una pelea de espectador, y a donde vuelven. */
	private final Mirones mirones = new Mirones();

	/** Las peleas que se estan armando a mano desde el menu. */
	private final Mesas mesas = new Mesas();

	/** Los mirones que hay que devolver en el tick siguiente al que entraron. */
	private final Deque<String> mironesPorDevolver = new ArrayDeque<>();
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

	/**
	 * El unico que hay, para que lo encuentre el mixin de Safe Zone.
	 *
	 * Un mixin corre adentro de la clase de otro mod, donde no hay ninguna
	 * instancia nuestra a mano ni forma de pasarle una. Y hay uno solo de verdad:
	 * lo arma `DuelosServidor` cuando arranca el juego y no lo arma nadie mas.
	 */
	private static Duelos elQueManda;

	public Duelos(Registro registro) {
		this.registro = registro;
		elQueManda = this;
	}

	public void alArrancar(MinecraftServer servidor) {
		this.servidor = servidor;
		Puntos.preparar(servidor);
		mirones.alArrancar(servidor);
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

	public Cola espera() {
		return espera;
	}

	public Mirones mirones() {
		return mirones;
	}

	public Mesas mesas() {
		return mesas;
	}

	/**
	 * Arranca una pelea armada a mano, salteando la cola.
	 *
	 * Saltear la cola es a proposito: los que van a pelear ya se pusieron de
	 * acuerdo y estan esperando con la pantalla abierta. Si la arena esta ocupada
	 * no se puede, y devuelve false para que el que apreto se entere en el momento
	 * en vez de quedarse mirando un boton que no hizo nada.
	 */
	public boolean arrancarDeMesa(Cola.Par par, int apuesta, String clase) {
		if (actual != null || servidor == null) return false;
		Arena arena = registro.arena();
		if (arena == null || arena.nivel(servidor) == null) return false;

		Duelo duelo = new Duelo(servidor, registro, arena, par.a(), par.b(),
				apuesta, clase, false);
		if (!duelo.arrancar()) return false;
		actual = duelo;
		return true;
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
		while (!mironesPorDevolver.isEmpty()) devolverAlMiron(mironesPorDevolver.poll());

		limpiarLosBichos();
		// El que mira esta de espectador y vuela sin frenos: si no se lo frena, se
		// va del coliseo y aparece a 500.000 bloques del spawn sin forma de volver.
		mirones.noSeVayan(servidor, registro.arena());
		mesas.tick(servidor, this);

		if (actual != null) {
			actual.tick();
			if (actual.terminado()) {
				Duelo termino = actual;
				actual = null;
				// Los que miraban vuelven a donde estaban, con su modo de juego. Va
				// aca y no adentro del duelo porque los mirones no son de la pelea:
				// son del coliseo, y el coliseo sigue estando despues.
				mirones.devolverATodos();
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
			Duelo duelo = new Duelo(servidor, registro, arena, proximo.uno(), proximo.otro(),
					proximo.apuesta(), proximo.clase(), proximo.deTorneo());
			if (duelo.arrancar()) {
				actual = duelo;
			} else {
				avisar(proximo, Carteles.aviso("El duelo se cayo: alguno de los dos no esta."));
				if (proximo.deTorneo() && torneo != null) torneo.noPudoArrancar(proximo);
			}
		}

		// Y si la arena sigue libre, se mira la lista de espera: los que se anotaron
		// sin rival. Va despues de la cola de retos a proposito, porque un reto es
		// entre dos que se pusieron de acuerdo y eso vale mas que un turno.
		if (actual == null) arrancarDeLaEspera();
	}

	/**
	 * Cruza a los que estan anotados en la lista de espera y arranca la pelea.
	 *
	 * Si el duelo no puede arrancar —a alguno le quedo un inventario sin devolver,
	 * o se fue justo— no se los vuelve a anotar: que se anoten de nuevo. Reintentar
	 * solo seria repetir el mismo error cada tick.
	 */
	private void arrancarDeLaEspera() {
		Arena arena = registro.arena();
		if (arena == null || servidor == null || arena.nivel(servidor) == null) return;

		Cola.Par par = espera.armar(servidor);
		if (par == null) return;

		Duelo duelo = new Duelo(servidor, registro, arena, par.a(), par.b(), 0, null, false);
		if (duelo.arrancar()) {
			actual = duelo;
			return;
		}
		for (String quien : duelo.todos()) {
			ServerPlayer conectado = servidor.getPlayerList().getPlayerByName(quien);
			if (conectado != null) {
				conectado.sendSystemMessage(
						Carteles.aviso("No se pudo armar la pelea. Anotate de nuevo."));
			}
		}
	}

	/** Al que entro y habia quedado de espectador se lo devuelve a donde estaba. */
	private void devolverAlMiron(String nombre) {
		ServerPlayer quien = servidor.getPlayerList().getPlayerByName(nombre);
		if (quien != null) mirones.devolver(quien);
	}

	/**
	 * La arena no tiene bichos. Nunca, haya duelo o no.
	 *
	 * Un coliseo es un lugar oscuro y grande, o sea una guarderia de zombis, y un
	 * duelo con un esqueleto tirando flechas desde las gradas deja de decidir
	 * nada. Los phantoms son peores todavia: bajan igual aunque la pelea este
	 * empezada, y bajan justo sobre el que hace rato que no duerme.
	 *
	 * Se barre en vez de prohibirles aparecer porque el que aparece no es el
	 * unico problema: el phantom se genera arriba del techo y el zombi entra
	 * caminando desde una cueva de al lado, y los dos llegan igual. Media vez por
	 * segundo alcanza para que nadie vea a ninguno, y no le pegan a los que
	 * pelean ni aunque lleguen: `dejarPegar` ya cancela cualquier golpe que no
	 * venga del otro duelista.
	 *
	 * Se van solo los hostiles, y **solo los que no estan protegidos**. Los
	 * caballos, los lobos y las vacas que alguien haya dejado ahi son de alguien;
	 * y al que se protegio a mano con la varita no lo toca esto ni aunque sea un
	 * hostil: `discard()` se lleva puesto al invulnerable igual, asi que si no se
	 * preguntara, un esqueleto de adorno protegido con la varita se borraria solo
	 * medio segundo despues de ponerlo.
	 */
	private void limpiarLosBichos() {
		if (servidor == null || servidor.getTickCount() % CADA_CUANTO_LIMPIO != 0) return;
		Arena arena = registro.arena();
		if (arena == null) return;
		ServerLevel nivel = arena.nivel(servidor);
		if (nivel == null) return;

		for (Mob bicho : nivel.getEntitiesOfClass(Mob.class, arena.caja(),
				b -> b instanceof Enemy && !b.isInvulnerable())) {
			bicho.discard();
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
		// Entre companeros del mismo lado no se pega: en un 2v2 el kit es igual para
		// los cuatro y un golpe mal dado adentro del propio bando decidiria la pelea.
		if (victimaPelea && culpablePelea && culpable instanceof Player pegador
				&& quien instanceof Player victima
				&& actual.sonCompaneros(pegador.getScoreboardName(), victima.getScoreboardName())) {
			return false;
		}
		// Al que ya cayo no lo toca nadie: esta mirando de espectador.
		if (victimaPelea && quien instanceof Player caido
				&& actual.estaEliminado(caido.getScoreboardName())) {
			return false;
		}
		// Uno de los dos esta en el duelo y el otro no: el golpe no pasa, venga del
		// lado que venga. Los golpes sin dueño (caida, fuego, lava) pasan igual.
		if (victimaPelea && culpable != null && !culpablePelea) return false;
		if (culpablePelea && quien instanceof Player && !victimaPelea) return false;
		return true;
	}

	/**
	 * Lo mismo que `puedeTocarLaArena` pero al reves y desde afuera: si a este
	 * jugador hay que dejarlo construir ahi aunque Safe Zone diga que no.
	 *
	 * Lo pregunta `ClaimManagerMixin`, que es lo unico que puede abrirle un
	 * agujero a una zona protegida. Las tres condiciones son las tres juntas y
	 * ninguna sobra:
	 *
	 *  - **hay un duelo ahora**, asi que entre pelea y pelea el coliseo esta tan
	 *    protegido como el resto del spawn;
	 *  - **es uno de los dos que pelean**, asi que el de las gradas no rompe nada
	 *    aunque este parado adentro;
	 *  - **es adentro de la caja de la arena**, que es lo unico que se fotografia
	 *    antes del duelo y vuelve entero al terminar. Un bloque puesto un metro
	 *    afuera de la caja se quedaria ahi para siempre, y desde adentro se llega:
	 *    el brazo de un jugador pasa la pared.
	 */
	public static boolean dejaConstruir(ServerPlayer quien, net.minecraft.core.BlockPos donde) {
		Duelos duelos = elQueManda;
		if (duelos == null || duelos.actual == null) return false;
		if (!duelos.actual.esDuelista(quien.getScoreboardName())) return false;

		Arena arena = duelos.actual.arena();
		return arena.esDe(quien.level()) && arena.contiene(donde);
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
		// Si el servidor se cayo mientras miraba una pelea, vuelve a donde estaba y
		// deja de ser espectador. Va antes que el inventario porque es lo que lo
		// tiene atrapado adentro del coliseo.
		if (mirones.estaMirando(quien)) mironesPorDevolver.add(quien.getScoreboardName());

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
		espera.sacar(nombre);
		mesas.sacar(nombre);

		// El que se va mirando vuelve a su lugar ANTES de que el servidor escriba su
		// archivo. Esto es LEAVE y no DISCONNECT: corre en el hilo del servidor y
		// antes del guardado, asi que lo que se le cambia queda escrito.
		//
		// Se le toca la posicion y el modo de juego, nunca el inventario: de mirar
		// se sale igual que se entro. Y aunque `alEntrar` tambien sabe devolver al
		// que quedo mirando —es lo que salva al que se lo lleva por delante una
		// caida del servidor— esto se queda, porque volver de espectador al entrar
		// pelea con el mod del lobby, que en el mismo momento lo esta mandando al
		// patio.
		if (mirones.estaMirando(quien)) mirones.devolver(quien);
		// Las de torneo NO se sacan de la cola: se dejan fallar al arrancar, y ahi
		// el torneo hace pasar de ronda al que si esta. Sacandolas aca, el torneo se
		// quedaria esperando el resultado de una pelea que ya no existe.
		cola.removeIf(espera -> !espera.deTorneo()
				&& (espera.uno().equalsIgnoreCase(nombre) || espera.otro().equalsIgnoreCase(nombre)));
		if (torneo != null) torneo.seFue(nombre);
		if (actual != null && actual.esDuelista(nombre)) {
			actual.seFue(nombre);
		}
	}

	// -------------------------------------------------------------------- la cola

	/** Mete la pelea en la cola. Devuelve en que lugar quedo (1 es la proxima). */
	public int encolar(String uno, String otro, int apuesta, String clase, boolean deTorneo) {
		invitaciones.olvidar(uno);
		invitaciones.olvidar(otro);
		cola.add(new EnEspera(uno, otro, apuesta, clase, deTorneo));
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
