package pe.sobrinosdepepe.duelos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Una pelea, de punta a punta. Un lado contra el otro.
 *
 * Los dos lados pueden ser de uno (1v1), de dos (2v2) o de cuantos sean (equipo
 * contra equipo). Cada lado tiene un nombre para mostrar —ver {@link Lado}— y por
 * eso todos los carteles siguen recibiendo dos String como cuando esto era solo
 * un 1v1.
 *
 * Las cuatro etapas y por que estan en ese orden:
 *
 *  1. **APUESTAS, 10 segundos.** Los que pelean ya estan parados en la arena,
 *     quietos y sin poderse tocar, mientras el resto mira y apuesta. Estan adentro
 *     y no afuera a proposito: la gente le apuesta a alguien que esta VIENDO, con
 *     el kit puesto, y no a un nombre en el chat.
 *  2. **CUENTA.** 3, 2, 1 en pantalla, con el pim de cada numero, y al toque el
 *     ¡PELEEN! en verde grande. Recien ahi se pueden mover.
 *  3. **PELEA.** Hasta que un lado se queda sin nadie en pie, se rinden, se
 *     desconectan, o se acaban los cinco minutos (ahi gana el lado con mas vida
 *     entre todos).
 *  4. **FINAL.** Cinco segundos de festejo con el ganador en pantalla, y despues
 *     se deshace todo: los inventarios vuelven, la arena vuelve, la barra se va.
 *
 * **Nadie muere de verdad en un duelo.** El golpe que mataria se cancela
 * (`ServerLivingEntityEvents.ALLOW_DEATH`) y en su lugar el jugador queda
 * **eliminado**: se le sana la vida, se lo pasa a espectador y mira el resto de la
 * pelea volando. Si la muerte pasara de verdad, el que pierde soltaria el kit en
 * el piso, el que gana cobraria los 10 shards de la kill y el 10% de la plata del
 * muerto, y dos amigos turnandose tendrian una maquina de shards.
 *
 * **A la arena entra el que quiere.** Los de afuera pasan, miran de cerca y no los
 * echa nadie: no pueden pegar, ni que les peguen, ni tocar un bloque mientras dura
 * la pelea.
 */
public final class Duelo {
	private static final Logger LOG = LoggerFactory.getLogger("duelosdepepe");

	public static final int TICKS_APUESTAS = 200;
	public static final int TICKS_CUENTA = 80;
	public static final int TICKS_PELEA = 6000;
	public static final int TICKS_FINAL = 100;

	/** Hasta donde llega la barra y los titulos para el que no pelea ni aposto. */
	private static final double RADIO_PUBLICO = 96.0;

	public enum Etapa { APUESTAS, CUENTA, PELEA, FINAL, LISTO }

	private final MinecraftServer servidor;
	private final Registro registro;
	private final Arena arena;

	public final Lado ladoA;
	public final Lado ladoB;

	/** Como se llama cada lado. Es lo que leen todos los carteles. */
	public final String uno;
	public final String otro;

	public final int apuesta;
	public final String clase;
	public final boolean deTorneo;

	private Etapa etapa = Etapa.APUESTAS;
	private int reloj = TICKS_APUESTAS;
	private Kits.Kit kit;
	private Copia foto;

	private java.util.List<java.util.UUID> decoracionCuidada = java.util.List.of();
	private final Apuestas apuestas = new Apuestas();
	private ServerBossEvent barra;

	/** Los que ya cayeron, en minuscula. Siguen adentro, pero de espectadores. */
	private final Set<String> eliminados = new HashSet<>();

	/** En que modo de juego estaba cada uno antes de pelear, para devolverselo. */
	private final Map<String, GameType> modoDeAntes = new HashMap<>();

	/**
	 * Los equipos a los que hubo que abrirles el fuego amigo porque adentro de la
	 * pelea hay companeros de equipo en lados contrarios. Se cierran al desarmar.
	 */
	private final List<PlayerTeam> equiposAbiertos = new ArrayList<>();

	private String ganador;
	private String perdedor;
	private Lado ladoGanador;
	private boolean empate;
	private String comoFue = "";

	/** Un 1v1, que es como se arman todos los duelos de `/pvp <jugador>`. */
	public Duelo(MinecraftServer servidor, Registro registro, Arena arena,
			String uno, String otro, int apuesta, String clase, boolean deTorneo) {
		this(servidor, registro, arena, Lado.de(uno), Lado.de(otro), apuesta, clase, deTorneo);
	}

	public Duelo(MinecraftServer servidor, Registro registro, Arena arena,
			Lado ladoA, Lado ladoB, int apuesta, String clase, boolean deTorneo) {
		this.servidor = servidor;
		this.registro = registro;
		this.arena = arena;
		this.ladoA = ladoA;
		this.ladoB = ladoB;
		this.uno = ladoA.nombre;
		this.otro = ladoB.nombre;
		this.apuesta = apuesta;
		this.clase = clase;
		this.deTorneo = deTorneo;
	}

	// -------------------------------------------------------------------- arrancar

	/**
	 * Los mete a todos en la arena. Devuelve false si falta alguno, y en ese caso
	 * no toco nada de nadie.
	 */
	public boolean arrancar() {
		ServerLevel nivel = arena.nivel(servidor);
		if (nivel == null) return false;

		List<ServerPlayer> deA = ladoA.conectados(servidor);
		List<ServerPlayer> deB = ladoB.conectados(servidor);
		if (deA.size() != ladoA.cuantos() || deB.size() != ladoB.cuantos()) return false;

		// Nadie entra a la arena debiendole un inventario a nadie. Si alguno tiene
		// una copia sin devolver —de un duelo que no cerro bien, o de una caida del
		// servidor— el duelo no arranca. Guardarle el kit prestado encima de su
		// copia de verdad seria perderle todo.
		for (String quien : todos()) {
			if (registro.pendienteDe(quien) != null) {
				LOG.error("No arranco el duelo {} vs {}: {} tiene un inventario sin devolver",
						uno, otro, quien);
				for (ServerPlayer jugador : conectadosDeLosDos()) {
					jugador.sendSystemMessage(Carteles.inventarioSinDevolver());
				}
				return false;
			}
		}

		// Las copias se guardan todas juntas y antes de tocarle el inventario a
		// nadie. Si una sola falla, se olvidan las que ya se habian anotado: dejar a
		// medio guardar es lo unico de todo el mod que no se puede deshacer.
		List<String> anotados = new ArrayList<>();
		for (ServerPlayer jugador : conectadosDeLosDos()) {
			if (!registro.anotar(Guardado.de(jugador))) {
				for (String quien : anotados) registro.olvidar(quien);
				return false;
			}
			anotados.add(jugador.getScoreboardName());
		}

		// La foto de la arena se saca ANTES de que entre nadie, y despues de limpiar
		// lo que haya quedado tirado de antes: asi lo que se devuelve al final es el
		// coliseo y no el coliseo con la basura de la pelea anterior adentro.
		Copia.limpiar(nivel, arena);
		foto = Copia.sacar(nivel, arena);
		decoracionCuidada = Copia.cuidarLaDecoracion(nivel, arena);

		RandomSource azar = nivel.getRandom();
		kit = Kits.armar(servidor.registryAccess(), azar, clase);

		// El cobro de la apuesta va antes de tocar el inventario: si algo fallara
		// aca, nadie perdio todavia nada de lo suyo. Cada uno paga lo suyo.
		if (apuesta > 0) {
			for (String quien : todos()) Puntos.sumarShards(servidor, quien, -apuesta);
		}

		meterLado(deA, arena.punto1, arena.giro1, nivel);
		meterLado(deB, arena.punto2, arena.giro2, nivel);
		abrirElFuegoAmigo();

		barra = new ServerBossEvent(java.util.UUID.randomUUID(),
				Carteles.barraApuestas(uno, otro, 0, 0),
				BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

		servidor.getPlayerList().broadcastSystemMessage(
				// El boton de VER LA PELEA va siempre: mirar es de espectador y no hace
				// falta que nadie haya marcado un asiento en ningun lado.
				Carteles.arrancaElDuelo(uno, otro, apuesta, kit.nombre(), TICKS_APUESTAS / 20,
					true), false);

		titulo(publico(), Component.literal(""), Carteles.elKit(kit.nombre(), kit.comoSePelea()),
				5, 60, 10);
		sonarParaTodos();

		LOG.info("Duelo: {} vs {} ({} contra {}, {} shards, kit {})",
				uno, otro, ladoA.cuantos(), ladoB.cuantos(), apuesta, kit.nombre());
		return true;
	}

	/**
	 * Si dos que pelean en lados contrarios son companeros de equipo, el duelo le
	 * gana al equipo.
	 *
	 * Un equipo de `/equipo` es un equipo del scoreboard con `friendlyFire` en
	 * false, y eso lo mira el juego en `Player.canHarmPlayer` **antes** de que
	 * llegue a correr nuestro enganche del daño: dos companeros entraban a la arena,
	 * sonaba el ¡PELEEN! y no se podian tocar. Los dos mods diciendo cosas distintas
	 * sobre el mismo golpe, y ganaba el que corre primero.
	 *
	 * Manda el duelo, que es el que todos pidieron: se le abre el fuego amigo a los
	 * equipos involucrados mientras dura la pelea y se les vuelve a cerrar al
	 * desarmar. Se toca el equipo y no a los jugadores porque sacarlos del equipo
	 * les apagaria el color, el `[TAG]` y la barra de arriba en el medio de su
	 * propia pelea.
	 */
	private void abrirElFuegoAmigo() {
		Scoreboard scoreboard = servidor.getScoreboard();
		for (String deAca : ladoA.jugadores) {
			PlayerTeam suyo = scoreboard.getPlayersTeam(deAca);
			if (suyo == null || suyo.isAllowFriendlyFire()) continue;
			for (String deAlla : ladoB.jugadores) {
				if (suyo != scoreboard.getPlayersTeam(deAlla)) continue;
				suyo.setAllowFriendlyFire(true);
				equiposAbiertos.add(suyo);
				LOG.info("Duelo entre companeros: le abro el fuego amigo a {} hasta que termine",
						suyo.getName());
				break;
			}
		}
	}

	/** Los equipos vuelven a ser equipos. Corre siempre, gane quien gane. */
	private void cerrarElFuegoAmigo() {
		for (PlayerTeam equipo : equiposAbiertos) equipo.setAllowFriendlyFire(false);
		equiposAbiertos.clear();
	}

	/**
	 * Planta a todo un lado adentro del ring, en linea y mirando al otro lado.
	 *
	 * Antes el primero iba a la esquina marcada y los companeros caian **al azar**
	 * por el ring. Se veia mal: un 2v2 con los cuatro desparramados no se lee desde
	 * las gradas, y el que aparecia de espaldas arrancaba perdiendo. Ahora los de un
	 * lado salen repartidos sobre la perpendicular al eje de la pelea, que es lo que
	 * hace que un 2v2 se vea como un 2v2 (ver `Arena.puestos`).
	 *
	 * Todos miran para el mismo lado: el giro de la esquina marcada ya apunta al
	 * otro lado de la cancha, asi que sirve para los companeros igual.
	 */
	private void meterLado(List<ServerPlayer> gente, Vec3 esquina, float giro, ServerLevel nivel) {
		boolean primerLado = esquina == arena.punto1;
		List<Vec3> lugares = arena.puestos(nivel, primerLado, gente.size());
		for (int i = 0; i < gente.size(); i++) {
			Vec3 donde = i < lugares.size() ? lugares.get(i) : esquina;
			meterEnLaArena(gente.get(i), donde, giro, nivel);
		}
	}

	/**
	 * Un lugar cualquiera del ring con piso y lugar para pararse.
	 *
	 * Busca de arriba para abajo el primer bloque que frene: asi cae sobre el piso
	 * del coliseo y no adentro de una pared ni arriba del techo. Si despues de
	 * veinte intentos no encontro ninguno —una cancha sin piso, o llena— devuelve la
	 * esquina del lado, que siempre sirve.
	 */
	private Vec3 unLugarDelRing(ServerLevel nivel, Vec3 siNoHay) {
		AABB caja = arena.caja();
		RandomSource azar = nivel.getRandom();
		for (int intento = 0; intento < 20; intento++) {
			double x = caja.minX + 1 + azar.nextDouble() * Math.max(1, caja.getXsize() - 2);
			double z = caja.minZ + 1 + azar.nextDouble() * Math.max(1, caja.getZsize() - 2);
			for (int y = (int) caja.maxY - 1; y > caja.minY; y--) {
				BlockPos piso = BlockPos.containing(x, y, z);
				if (!nivel.getBlockState(piso).blocksMotion()) continue;
				if (nivel.getBlockState(piso.above()).blocksMotion()) break;
				if (nivel.getBlockState(piso.above(2)).blocksMotion()) break;
				return new Vec3(x, y + 1.0, z);
			}
		}
		return siNoHay;
	}

	/** Para que el que cae en cualquier lado no aparezca mirando a una pared. */
	private float mirandoAlCentro(Vec3 desde) {
		Vec3 centro = arena.centro();
		double dx = centro.x - desde.x;
		double dz = centro.z - desde.z;
		return (float) (Math.toDegrees(Math.atan2(-dx, dz)));
	}

	/**
	 * Lo deja pelado, le pone el kit y lo planta en su esquina.
	 *
	 * Cuando esto corre, la copia de su inventario ya esta guardada y escrita en el
	 * archivo: lo hace `arrancar()` antes de llamar a esto, y si no pudo, el duelo
	 * ni empieza. Vaciarle el inventario a alguien sin tener la copia guardada es
	 * lo unico de todo el mod que no se puede deshacer.
	 */
	private void meterEnLaArena(ServerPlayer quien, Vec3 donde, float giro, ServerLevel nivel) {
		modoDeAntes.put(quien.getScoreboardName().toLowerCase(Locale.ROOT), quien.gameMode());
		if (quien.gameMode() != GameType.SURVIVAL) quien.setGameMode(GameType.SURVIVAL);

		quien.closeContainer();
		for (int i = 0; i < quien.getInventory().getContainerSize(); i++) {
			quien.getInventory().setItem(i, ItemStack.EMPTY);
		}
		quien.removeAllEffects();
		quien.setHealth(quien.getMaxHealth());
		quien.setAbsorptionAmount(0);
		quien.getFoodData().setFoodLevel(20);
		quien.getFoodData().setSaturation(20);
		quien.clearFire();
		quien.resetFallDistance();
		quien.setDeltaMovement(0, 0, 0);
		quien.teleportTo(nivel, donde.x, donde.y, donde.z, Set.<Relative>of(), giro, 0, false);

		Kits.dar(quien, kit);
		congelar(quien);
		marcarEnPelea(quien);
	}

	/**
	 * La marca de pelea del datapack: mientras dura el duelo no anda /home, ni
	 * /spawn, ni /rtp, ni /tpa. No hay que programar nada de eso, ya esta hecho.
	 */
	private void marcarEnPelea(ServerPlayer quien) {
		Puntos.marcarEnPelea(servidor, quien, 300);
	}

	// ------------------------------------------------------------------- cada tick

	public void tick() {
		if (etapa == Etapa.LISTO) return;

		// Si un lado entero se desconecto, pierde. En FINAL ya no importa.
		if (etapa != Etapa.FINAL) {
			boolean faltaA = ladoA.conectados(servidor).isEmpty();
			boolean faltaB = ladoB.conectados(servidor).isEmpty();
			if (faltaA || faltaB) {
				seFue(faltaA ? ladoA.jugadores.get(0) : ladoB.jugadores.get(0));
				return;
			}
		}

		switch (etapa) {
			case APUESTAS -> tickApuestas();
			case CUENTA -> tickCuenta();
			case PELEA -> tickPelea();
			case FINAL -> tickFinal();
			default -> { }
		}

		if (etapa == Etapa.LISTO) return;
		// Una vez por segundo alcanza: el reloj de la marca dura quince.
		if (servidor.getTickCount() % 20 == 0) {
			for (ServerPlayer quien : conectadosDeLosDos()) marcarEnPelea(quien);
		}
	}

	private void tickApuestas() {
		quietos();
		refrescarBarra(Carteles.barraApuestas(uno, otro, apuestas.total(true), apuestas.total(false)),
				(float) reloj / TICKS_APUESTAS, BossEvent.BossBarColor.PURPLE);

		if (--reloj > 0) return;

		etapa = Etapa.CUENTA;
		reloj = TICKS_CUENTA;
		if (apuestas.hay()) {
			servidor.getPlayerList().broadcastSystemMessage(
					Carteles.aviso("Cerraron las apuestas: " + apuestas.cuantosApostaron()
							+ " le pusieron shards a esta pelea."), false);
		}
	}

	/**
	 * La cuenta regresiva. Los numeros salen a los 60, 40 y 20 ticks que quedan, o
	 * sea uno por segundo, y el ¡PELEEN! en el cero.
	 */
	private void tickCuenta() {
		quietos();
		refrescarBarra(Carteles.barraPelea(uno, otro), 1.0f, BossEvent.BossBarColor.RED);

		switch (reloj) {
			case 60, 40, 20 -> {
				int numero = reloj / 20;
				titulo(publico(), Carteles.numeroDeLaCuenta(numero), Component.empty(), 0, 20, 0);
				sonar(SoundEvents.NOTE_BLOCK_PLING, 1.6f,
						numero == 3 ? 1.0f : numero == 2 ? 1.2f : 1.5f);
			}
			case 0 -> arrancarLaPelea();
			default -> { }
		}
		reloj--;
	}

	private void arrancarLaPelea() {
		etapa = Etapa.PELEA;
		reloj = TICKS_PELEA;
		for (ServerPlayer quien : conectadosDeLosDos()) descongelar(quien);

		titulo(publico(), Carteles.aPelear(), Component.empty(), 0, 25, 10);
		for (ServerPlayer quien : ladoA.conectados(servidor)) {
			quien.connection.send(new ClientboundSetSubtitleTextPacket(Carteles.contra(otro)));
		}
		for (ServerPlayer quien : ladoB.conectados(servidor)) {
			quien.connection.send(new ClientboundSetSubtitleTextPacket(Carteles.contra(uno)));
		}
		sonar(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
		sonar(SoundEvents.RAID_HORN, 1.2f, 1.2f);
	}

	private void tickPelea() {
		for (ServerPlayer quien : vivosDeLosDos()) adentroDeLaArena(quien);
		refrescarBarra(Carteles.barraPelea(uno, otro), (float) reloj / TICKS_PELEA,
				BossEvent.BossBarColor.RED);

		if (--reloj > 0) return;

		// Se acabo el tiempo: gana el lado que llego con mas vida sumada. Es la
		// unica forma de cortar una pelea de gente que se esconde y no se busca.
		float vidaA = vidaDe(ladoA);
		float vidaB = vidaDe(ladoB);
		if (Math.abs(vidaA - vidaB) < 0.01f) {
			terminarEnEmpate();
		} else {
			boolean ganaA = vidaA > vidaB;
			terminar(ganaA ? ladoA : ladoB, ganaA ? ladoB : ladoA,
					"Se acabaron los cinco minutos: gano el que llego con mas vida.");
		}
	}

	private void tickFinal() {
		quietos();
		if (--reloj <= 0) desarmar();
	}

	private float vidaDe(Lado lado) {
		float suma = 0;
		for (ServerPlayer quien : lado.vivos(servidor, eliminados)) suma += quien.getHealth();
		return suma;
	}

	// ------------------------------------------------------------------ resultados

	/**
	 * Lo llama el enganche de la muerte: este se iba a morir y no lo dejamos.
	 *
	 * En un 1v1 el duelo termina ahi mismo. En un 2v2 o en equipos **no**: el que
	 * cae queda eliminado y mira el resto de la pelea de espectador, y la pelea
	 * sigue hasta que un lado se queda sin nadie en pie.
	 */
	public void caido(ServerPlayer quien) {
		String muerto = quien.getScoreboardName();
		Lado suyo = ladoDe(muerto);
		if (suyo == null || etapa == Etapa.FINAL || etapa == Etapa.LISTO) return;

		eliminar(quien, "Lo dejaron en cero.");
		if (!suyo.vivos(servidor, eliminados).isEmpty()) return;

		Lado gana = suyo == ladoA ? ladoB : ladoA;
		terminar(gana, suyo, suyo.cuantos() == 1 ? "Lo dejo en cero."
				: "Se quedaron sin nadie en pie.");
	}

	/**
	 * Lo saca de la pelea sin sacarlo del coliseo: espectador y a mirar volando.
	 *
	 * Espectador y no teletransportado afuera porque el que cae en un 2v2 quiere
	 * ver como termina lo suyo, y de espectador no puede pegar, ni que le peguen,
	 * ni tocar un bloque: no hay forma de que meta la mano.
	 */
	private void eliminar(ServerPlayer quien, String porQue) {
		eliminados.add(quien.getScoreboardName().toLowerCase(Locale.ROOT));
		quien.setHealth(quien.getMaxHealth());
		quien.setAbsorptionAmount(0);
		quien.clearFire();
		quien.removeAllEffects();
		quien.setGameMode(GameType.SPECTATOR);
		quien.sendSystemMessage(Carteles.aviso("Caíste. " + porQue + " Mirá cómo termina."));

		if (etapa == Etapa.PELEA && (ladoA.cuantos() > 1 || ladoB.cuantos() > 1)) {
			servidor.getPlayerList().broadcastSystemMessage(
					Carteles.aviso(quien.getScoreboardName() + " quedó afuera: "
							+ ladoA.vivos(servidor, eliminados).size() + " vs "
							+ ladoB.vivos(servidor, eliminados).size()), false);
		}
	}

	public void rendirse(String quien) {
		Lado suyo = ladoDe(quien);
		if (suyo == null) return;
		ServerPlayer jugador = jugador(quien);
		if (jugador != null && suyo.cuantos() > 1) {
			eliminar(jugador, quien + " se rindió.");
			if (!suyo.vivos(servidor, eliminados).isEmpty()) return;
		}
		terminar(suyo == ladoA ? ladoB : ladoA, suyo, quien + " se rindio.");
	}

	/** Se desconecto en el medio. Pierde, que es lo unico que no premia irse. */
	public void seFue(String quien) {
		if (etapa == Etapa.FINAL || etapa == Etapa.LISTO) return;
		Lado suyo = ladoDe(quien);
		if (suyo == null) return;

		if (etapa == Etapa.APUESTAS) {
			// Todavia no habia empezado nada: no hay ganador ni perdedor, vuelve todo.
			cancelar(Carteles.seFueElOtro(quien));
			return;
		}

		eliminados.add(quien.toLowerCase(Locale.ROOT));
		if (!suyo.vivos(servidor, eliminados).isEmpty()) {
			servidor.getPlayerList().broadcastSystemMessage(
					Carteles.aviso(quien + " se desconectó y dejó a los suyos en desventaja."),
					false);
			return;
		}
		terminar(suyo == ladoA ? ladoB : ladoA, suyo, quien + " se desconecto.");
	}

	private void terminar(Lado gana, Lado pierde, String comoFue) {
		if (etapa == Etapa.FINAL || etapa == Etapa.LISTO) return;
		this.ladoGanador = gana;
		this.ganador = gana.nombre;
		this.perdedor = pierde.nombre;
		this.comoFue = comoFue;
		this.empate = false;
		etapa = Etapa.FINAL;
		reloj = TICKS_FINAL;

		for (ServerPlayer quien : conectadosDeLosDos()) {
			sanar(quien);
			congelar(quien);
		}

		// La apuesta la puso cada uno, asi que cada ganador cobra la suya y la del
		// rival que le tocaba enfrente. Con lados desparejos igual se reparte entre
		// los del lado que gano: lo que entro es apuesta * (cuantos son los dos).
		if (apuesta > 0) {
			int pozo = apuesta * (ladoA.cuantos() + ladoB.cuantos());
			int porCabeza = pozo / gana.cuantos();
			for (String quien : gana.jugadores) Puntos.sumarShards(servidor, quien, porCabeza);
		}
		for (String quien : gana.jugadores) Puntos.sumarGanado(servidor, quien);
		for (String quien : pierde.jugadores) Puntos.sumarPerdido(servidor, quien);

		pagarLasApuestas(gana == ladoA);

		servidor.getPlayerList().broadcastSystemMessage(
				Carteles.resultado(ganador, perdedor, apuesta, comoFue), false);
		festejo();
		LOG.info("Duelo terminado: gano {} a {} ({})", ganador, perdedor, comoFue);
	}

	private void terminarEnEmpate() {
		if (etapa == Etapa.FINAL || etapa == Etapa.LISTO) return;
		empate = true;
		etapa = Etapa.FINAL;
		reloj = TICKS_FINAL;

		for (ServerPlayer quien : conectadosDeLosDos()) {
			sanar(quien);
			congelar(quien);
		}

		if (apuesta > 0) {
			for (String quien : todos()) Puntos.sumarShards(servidor, quien, apuesta);
		}
		devolverLasApuestas();

		servidor.getPlayerList().broadcastSystemMessage(Carteles.empate(uno, otro), false);
		titulo(publico(), Carteles.tituloEmpate(), Component.empty(), 5, 60, 20);
		sonar(SoundEvents.ANVIL_LAND, 1.0f, 0.7f);
		LOG.info("Duelo terminado en empate: {} vs {}", uno, otro);
	}

	/** Se corta sin ganador: nadie cobra nada y vuelve todo lo apostado. */
	private void cancelar(Component porQue) {
		empate = true;
		etapa = Etapa.FINAL;
		reloj = 1;
		if (apuesta > 0) {
			for (String quien : todos()) Puntos.sumarShards(servidor, quien, apuesta);
		}
		devolverLasApuestas();
		servidor.getPlayerList().broadcastSystemMessage(porQue, false);
	}

	private void pagarLasApuestas(boolean ganoUno) {
		if (!apuestas.hay()) return;
		Apuestas.Reparto reparto = apuestas.repartir(ganoUno);
		for (Map.Entry<String, Integer> quien : reparto.cobran().entrySet()) {
			Puntos.sumarShards(servidor, quien.getKey(), quien.getValue());
			ServerPlayer conectado = jugador(quien.getKey());
			if (conectado != null) {
				conectado.sendSystemMessage(Carteles.cobraste(quien.getValue(),
						apuestas.loQuePuso(quien.getKey())));
			}
		}
		// Lo que del lado de los que erraron no encontro con quien cruzarse. Vuelve,
		// y con su propio cartel: es plata que nadie le acepto, no un premio.
		for (Map.Entry<String, Integer> quien : reparto.devuelven().entrySet()) {
			Puntos.sumarShards(servidor, quien.getKey(), quien.getValue());
			ServerPlayer conectado = jugador(quien.getKey());
			if (conectado != null) {
				conectado.sendSystemMessage(Carteles.noSeCruzo(quien.getValue(),
						apuestas.loQuePuso(quien.getKey())));
			}
		}
		// Lo que no dio entero se lo lleva el primero del lado que gano. Tiene que ir
		// a algun lado o los shards se irian evaporando de a uno por duelo.
		if (reparto.sobra() > 0 && ladoGanador != null) {
			Puntos.sumarShards(servidor, ladoGanador.jugadores.get(0), reparto.sobra());
		}
		LOG.info("Apuestas del duelo: cobran {} / vuelven {}",
				Carteles.repartoParaElLog(reparto.cobran()),
				Carteles.repartoParaElLog(reparto.devuelven()));
	}

	private void devolverLasApuestas() {
		if (!apuestas.hay()) return;
		apuestas.devolverTodo().forEach((quien, cuanto) -> {
			Puntos.sumarShards(servidor, quien, cuanto);
			ServerPlayer conectado = jugador(quien);
			if (conectado != null) conectado.sendSystemMessage(Carteles.teDevolvemos(cuanto));
		});
	}

	private void festejo() {
		for (ServerPlayer quien : publico()) {
			String nombre = quien.getScoreboardName();
			boolean esDelGanador = ladoGanador != null && ladoGanador.tiene(nombre);
			boolean esDelPerdedor = !esDelGanador && esDuelista(nombre);
			Component grande = esDelGanador ? Carteles.tituloGanaste()
					: esDelPerdedor ? Carteles.tituloPerdiste()
					: Carteles.tituloGano(ganador);
			Component chico = esDelGanador || esDelPerdedor
					? Component.empty() : Carteles.subtituloGano();
			titulo(List.of(quien), grande, chico, 5, 70, 20);
		}

		sonar(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
		sonar(SoundEvents.FIREWORK_ROCKET_BLAST, 2.0f, 1.0f);

		if (ladoGanador != null) {
			for (ServerPlayer gano : ladoGanador.conectados(servidor)) {
				ServerLevel nivel = (ServerLevel) gano.level();
				nivel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
						gano.getX(), gano.getY() + 1.0, gano.getZ(), 80, 0.6, 0.8, 0.6, 0.3);
				nivel.sendParticles(ParticleTypes.END_ROD,
						gano.getX(), gano.getY() + 2.2, gano.getZ(), 40, 0.4, 0.4, 0.4, 0.05);
			}
			Lado perdio = ladoGanador == ladoA ? ladoB : ladoA;
			for (ServerPlayer quien : perdio.conectados(servidor)) {
				((ServerLevel) quien.level()).sendParticles(ParticleTypes.SMOKE,
						quien.getX(), quien.getY() + 1.0, quien.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
			}
		}
	}

	/**
	 * Deshace todo. Es lo ultimo que corre y no puede fallar a medias: primero se
	 * les devuelve lo suyo a los jugadores y recien despues se arregla la arena,
	 * porque lo que no se puede perder son los inventarios.
	 */
	private void desarmar() {
		etapa = Etapa.LISTO;

		for (String quien : todos()) devolverLoSuyo(quien);
		cerrarElFuegoAmigo();

		ServerLevel nivel = arena.nivel(servidor);
		if (nivel != null) {
			Copia.soltarLaDecoracion(nivel, decoracionCuidada);
			decoracionCuidada = java.util.List.of();
			if (foto != null) {
				int arreglados = foto.devolver(nivel);
				if (arreglados > 0) {
					LOG.info("La arena volvio a como estaba: {} bloques", arreglados);
				}
			}
		}

		if (barra != null) {
			barra.removeAllPlayers();
			barra.setVisible(false);
			barra = null;
		}
	}

	/**
	 * Le devuelve a uno lo suyo y le saca la copia del archivo.
	 *
	 * Si no esta conectado no se hace nada: la copia se queda en el archivo y la
	 * recupera al entrar (`Duelos.alEntrar`). Esa es la unica razon por la que las
	 * copias se guardan en disco.
	 */
	private void devolverLoSuyo(String quien) {
		ServerPlayer conectado = jugador(quien);
		Guardado copia = registro.pendienteDe(quien);
		if (conectado == null || copia == null) return;

		// El modo de juego se devuelve SIEMPRE, aunque la copia falle: el que quedo
		// eliminado esta de espectador y dejarlo asi seria dejarlo sin poder jugar.
		GameType antes = modoDeAntes.get(quien.toLowerCase(Locale.ROOT));
		conectado.setGameMode(antes == null ? GameType.SURVIVAL : antes);

		descongelar(conectado);
		Puntos.sacarDePelea(servidor, conectado);
		copia.devolverA(conectado, servidor);
		registro.olvidar(quien);
	}

	// -------------------------------------------------------------------- apuestas

	/** Devuelve el texto del error, o null si la apuesta entro. */
	public Component apostar(ServerPlayer quien, String aQuien, int cuanto) {
		if (etapa != Etapa.APUESTAS) return Carteles.apuestasCerradas();

		String nombre = quien.getScoreboardName();
		if (esDuelista(nombre)) return Carteles.losQuePeleanNoApuestan();

		// Se le puede apostar al nombre del lado ("Los Pibes") o al de cualquiera de
		// los que pelean en el: los dos quieren decir lo mismo.
		boolean aFavorDeUno;
		if (aQuien.equalsIgnoreCase(uno) || ladoA.tiene(aQuien)) aFavorDeUno = true;
		else if (aQuien.equalsIgnoreCase(otro) || ladoB.tiene(aQuien)) aFavorDeUno = false;
		else return Carteles.eseNoPelea(aQuien);

		Boolean yaLeAposto = apuestas.aQuienLeAposto(nombre);
		if (yaLeAposto != null && yaLeAposto != aFavorDeUno) {
			return Carteles.yaApostaste(yaLeAposto ? uno : otro);
		}

		int tiene = Puntos.shards(servidor, nombre);
		if (cuanto > tiene) return Carteles.sinShards(cuanto, tiene);

		Puntos.sumarShards(servidor, nombre, -cuanto);
		apuestas.poner(nombre, aFavorDeUno, cuanto);
		if (barra != null) barra.addPlayer(quien);

		quien.sendSystemMessage(Carteles.apostaste(aFavorDeUno ? uno : otro, cuanto,
				apuestas.total(aFavorDeUno), apuestas.total(!aFavorDeUno)));
		quien.level().playSound(null, quien.getX(), quien.getY(), quien.getZ(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7f, 1.4f);
		return null;
	}

	// ------------------------------------------------------------------- consultas

	public Etapa etapa() {
		return etapa;
	}

	public boolean terminado() {
		return etapa == Etapa.LISTO;
	}

	public boolean esDuelista(String nombre) {
		return ladoA.tiene(nombre) || ladoB.tiene(nombre);
	}

	/** En que lado pelea, o null si no pelea. */
	public Lado ladoDe(String nombre) {
		if (ladoA.tiene(nombre)) return ladoA;
		if (ladoB.tiene(nombre)) return ladoB;
		return null;
	}

	/** Si los dos pelean del mismo lado: entre companeros no se pega. */
	public boolean sonCompaneros(String unNombre, String otroNombre) {
		Lado deUno = ladoDe(unNombre);
		return deUno != null && deUno == ladoDe(otroNombre);
	}

	/** Al que ya cayo no lo toca nadie: esta de espectador. */
	public boolean estaEliminado(String nombre) {
		return eliminados.contains(nombre.toLowerCase(Locale.ROOT));
	}

	/** Mientras no se pelea de verdad, a nadie lo toca nada ni nadie. */
	public boolean intocables() {
		return etapa != Etapa.PELEA;
	}

	public String ganador() {
		return ganador;
	}

	public String perdedor() {
		return perdedor;
	}

	public boolean fueEmpate() {
		return empate;
	}

	public Arena arena() {
		return arena;
	}

	public String nombreDelKit() {
		return kit == null ? "" : kit.nombre();
	}

	/**
	 * Le devuelve el modo de juego que tenia antes de pelear.
	 *
	 * Lo llama `Duelos.alSalir` para el que se desconecta: el que cayo en un 2v2
	 * esta de espectador, y si se va asi queda guardado de espectador para siempre.
	 */
	public void devolverElModo(ServerPlayer quien) {
		GameType antes = modoDeAntes.get(quien.getScoreboardName().toLowerCase(Locale.ROOT));
		quien.setGameMode(antes == null ? GameType.SURVIVAL : antes);
	}

	/** Todos los que pelean, de los dos lados. */
	public List<String> todos() {
		List<String> gente = new ArrayList<>(ladoA.jugadores);
		gente.addAll(ladoB.jugadores);
		return gente;
	}

	// ----------------------------------------------------------------- lo de adentro

	private List<ServerPlayer> conectadosDeLosDos() {
		List<ServerPlayer> gente = new ArrayList<>(ladoA.conectados(servidor));
		gente.addAll(ladoB.conectados(servidor));
		return gente;
	}

	private List<ServerPlayer> vivosDeLosDos() {
		List<ServerPlayer> gente = new ArrayList<>(ladoA.vivos(servidor, eliminados));
		gente.addAll(ladoB.vivos(servidor, eliminados));
		return gente;
	}

	private ServerPlayer jugador(String nombre) {
		return nombre == null ? null : servidor.getPlayerList().getPlayerByName(nombre);
	}

	private void quietos() {
		for (ServerPlayer quien : conectadosDeLosDos()) quien.setDeltaMovement(0, 0, 0);
	}

	/**
	 * El freno: lentitud 255 no deja caminar y salto 128 no deja saltar. Los dos
	 * sin particulas ni icono, asi la pantalla queda limpia para la cuenta.
	 *
	 * Se frena con efectos y no teletransportandolo a la posicion cada tick porque
	 * con el teletransporte el jugador tampoco puede mirar para los costados, y
	 * mirar al otro mientras corre la cuenta es medio la gracia.
	 */
	private void congelar(ServerPlayer quien) {
		if (quien.gameMode() == GameType.SPECTATOR) return;
		int cuanto = TICKS_APUESTAS + TICKS_CUENTA + 200;
		quien.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cuanto, 255, false, false, false));
		quien.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, cuanto, 128, false, false, false));
		quien.setDeltaMovement(0, 0, 0);
	}

	private void descongelar(ServerPlayer quien) {
		quien.removeEffect(MobEffects.SLOWNESS);
		quien.removeEffect(MobEffects.JUMP_BOOST);
	}

	private void sanar(ServerPlayer quien) {
		if (quien == null) return;
		quien.setHealth(quien.getMaxHealth());
		quien.setAbsorptionAmount(0);
		quien.clearFire();
		quien.resetFallDistance();
		quien.setDeltaMovement(0, 0, 0);
	}

	/**
	 * Al que se fue de la caja lo devuelve al borde por donde se iba.
	 *
	 * **El que se fue por ABAJO no va al borde: va a su esquina.** Con dinamita se
	 * puede volar el piso, y entonces el borde de abajo es aire: devolverlo ahi es
	 * devolverlo cayendo, o sea devolverlo otra vez el tick siguiente, y otra, y
	 * otra. Paso de verdad en un 1v1 y el jugador quedo trabado temblando en el
	 * aire hasta que termino la pelea. La esquina siempre tiene piso.
	 */
	private void adentroDeLaArena(ServerPlayer quien) {
		if (!arena.esDe(quien.level())) {
			ServerLevel nivel = arena.nivel(servidor);
			if (nivel != null) {
				quien.teleportTo(nivel, arena.punto1.x, arena.punto1.y, arena.punto1.z,
						Set.<Relative>of(), quien.getYRot(), quien.getXRot(), false);
			}
			return;
		}
		if (arena.contiene(quien.position())) return;

		if (quien.getY() < arena.min.getY()) {
			Vec3 suEsquina = ladoDe(quien.getScoreboardName()) == ladoB
					? arena.punto2 : arena.punto1;
			quien.teleportTo((ServerLevel) quien.level(), suEsquina.x, suEsquina.y, suEsquina.z,
					Set.<Relative>of(), quien.getYRot(), quien.getXRot(), false);
			quien.setDeltaMovement(0, 0, 0);
			quien.resetFallDistance();
			return;
		}

		Vec3 vuelta = arena.devolverAdentro(quien.position());
		quien.teleportTo((ServerLevel) quien.level(), vuelta.x, vuelta.y, vuelta.z,
				Set.<Relative>of(), quien.getYRot(), quien.getXRot(), false);
		quien.setDeltaMovement(0, 0, 0);
		quien.resetFallDistance();
	}

	/**
	 * Quienes ven la barra y los titulos: los que pelean, los que estan cerca de la
	 * arena y los que apostaron.
	 *
	 * Los que apostaron aunque esten lejos, porque tienen shards adentro de esa
	 * pelea y el resultado les importa mas que a nadie.
	 */
	private List<ServerPlayer> publico() {
		Set<ServerPlayer> gente = new LinkedHashSet<>(conectadosDeLosDos());

		ServerLevel nivel = arena.nivel(servidor);
		if (nivel != null) {
			Vec3 centro = arena.centro();
			for (ServerPlayer quien : nivel.players()) {
				if (quien.position().closerThan(centro, RADIO_PUBLICO)) gente.add(quien);
			}
		}
		for (ServerPlayer quien : servidor.getPlayerList().getPlayers()) {
			if (apuestas.aQuienLeAposto(quien.getScoreboardName()) != null) gente.add(quien);
		}
		return new ArrayList<>(gente);
	}

	private void refrescarBarra(Component texto, float cuanto, BossEvent.BossBarColor color) {
		if (barra == null) return;
		barra.setName(texto);
		barra.setProgress(Math.max(0f, Math.min(1f, cuanto)));
		barra.setColor(color);

		List<ServerPlayer> deberian = publico();
		for (ServerPlayer quien : new ArrayList<>(barra.getPlayers())) {
			if (!deberian.contains(quien)) barra.removePlayer(quien);
		}
		for (ServerPlayer quien : deberian) barra.addPlayer(quien);
	}

	private void titulo(List<ServerPlayer> aQuienes, Component grande, Component chico,
			int entra, int queda, int sale) {
		for (ServerPlayer quien : aQuienes) {
			quien.connection.send(new ClientboundSetTitlesAnimationPacket(entra, queda, sale));
			quien.connection.send(new ClientboundSetSubtitleTextPacket(chico));
			quien.connection.send(new ClientboundSetTitleTextPacket(grande));
		}
	}

	private void sonar(net.minecraft.sounds.SoundEvent sonido, float volumen, float tono) {
		ServerLevel nivel = arena.nivel(servidor);
		if (nivel == null) return;
		Vec3 centro = arena.centro();
		nivel.playSound(null, centro.x, centro.y, centro.z, sonido, SoundSource.MASTER, volumen, tono);
	}

	private void sonar(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sonido,
			float volumen, float tono) {
		ServerLevel nivel = arena.nivel(servidor);
		if (nivel == null) return;
		Vec3 centro = arena.centro();
		nivel.playSound(null, centro.x, centro.y, centro.z, sonido, SoundSource.MASTER, volumen, tono);
	}

	private void sonarParaTodos() {
		ServerLevel nivel = arena.nivel(servidor);
		if (nivel == null) return;
		for (ServerPlayer quien : servidor.getPlayerList().getPlayers()) {
			quien.level().playSound(null, quien.getX(), quien.getY(), quien.getZ(),
					SoundEvents.BELL_BLOCK, SoundSource.MASTER, 0.5f, 1.4f);
		}
	}
}
