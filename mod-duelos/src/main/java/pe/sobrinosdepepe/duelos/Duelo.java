package pe.sobrinosdepepe.duelos;

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
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Una pelea, de punta a punta.
 *
 * Las cuatro etapas y por que estan en ese orden:
 *
 *  1. **APUESTAS, 20 segundos.** Los dos ya estan parados en la arena, quietos
 *     y sin poderse tocar, mientras el resto mira y apuesta. Estan adentro y no
 *     afuera a proposito: la gente le apuesta a alguien que esta VIENDO, con el
 *     kit puesto, y no a un nombre en el chat. Son veinte segundos y no mas:
 *     quietos en pantalla, medio minuto se hace largo.
 *  2. **CUENTA.** 3, 2, 1 en pantalla, con el pim de cada numero, y al toque el
 *     ¡PELEEN! en verde grande. Recien ahi se pueden mover.
 *  3. **PELEA.** Hasta que uno cae, se rinde, se desconecta, o se acaban los
 *     cinco minutos (ahi gana el que tiene mas vida).
 *  4. **FINAL.** Cinco segundos de festejo con el ganador en pantalla, y despues
 *     se deshace todo: los inventarios vuelven, la arena vuelve, la barra se va.
 *
 * **Nadie muere de verdad en un duelo.** El golpe que mataria se cancela
 * (`ServerLivingEntityEvents.ALLOW_DEATH`) y en su lugar se termina la pelea. Eso
 * no es cosmetico: si la muerte pasara de verdad, el que pierde soltaria el kit
 * en el piso, el que gana cobraria los 10 shards de la kill y el 10% de la plata
 * del muerto, y dos amigos turnandose tendrian una maquina de shards. Cancelando
 * la muerte no pasa nada de eso y el duelo se queda con lo unico que se juega: la
 * apuesta.
 */
public final class Duelo {
	private static final Logger LOG = LoggerFactory.getLogger("duelosdepepe");

	/** Cuanto dura cada etapa, en ticks. 20 ticks son un segundo. */
	public static final int TICKS_APUESTAS = 400;
	public static final int TICKS_CUENTA = 80;
	public static final int TICKS_PELEA = 6000;
	public static final int TICKS_FINAL = 100;

	/** Desde cuan lejos se ve la barra de arriba y los titulos de la pelea. */
	private static final double RADIO_PUBLICO = 96.0;

	public enum Etapa { APUESTAS, CUENTA, PELEA, FINAL, LISTO }

	private final MinecraftServer servidor;
	private final Registro registro;
	private final Arena arena;

	public final String uno;
	public final String otro;
	public final int apuesta;
	/** La clase que eligio el que reto, o null si sale al azar. */
	public final String clase;
	/** Si esta pelea es una llave de torneo, para que el torneo sepa el resultado. */
	public final boolean deTorneo;

	private Etapa etapa = Etapa.APUESTAS;
	private int reloj = TICKS_APUESTAS;
	private Kits.Kit kit;
	private Copia foto;
	/** La decoracion de la arena a la que le pusimos proteccion mientras se pelea. */
	private java.util.List<java.util.UUID> decoracionCuidada = java.util.List.of();
	private final Apuestas apuestas = new Apuestas();
	private ServerBossEvent barra;
	private int avisoDeAfuera;

	private String ganador;
	private String perdedor;
	private boolean empate;
	private String comoFue = "";

	public Duelo(MinecraftServer servidor, Registro registro, Arena arena,
			String uno, String otro, int apuesta, String clase, boolean deTorneo) {
		this.servidor = servidor;
		this.registro = registro;
		this.arena = arena;
		this.uno = uno;
		this.otro = otro;
		this.apuesta = apuesta;
		this.clase = clase;
		this.deTorneo = deTorneo;
	}

	// -------------------------------------------------------------------- arrancar

	/**
	 * Los mete a los dos en la arena. Devuelve false si alguno ya no esta, y en
	 * ese caso no toco nada de nadie.
	 */
	public boolean arrancar() {
		ServerPlayer primero = jugador(uno);
		ServerPlayer segundo = jugador(otro);
		ServerLevel nivel = arena.nivel(servidor);
		if (primero == null || segundo == null || nivel == null) return false;

		// Nadie entra a la arena debiendole un inventario a nadie. Si alguno tiene
		// una copia sin devolver —de un duelo que no cerro bien, o de una caida del
		// servidor— se le devuelve primero y el duelo se cae. Guardarle el kit
		// prestado encima de su copia de verdad seria perderle todo.
		if (registro.pendienteDe(uno) != null || registro.pendienteDe(otro) != null) {
			LOG.error("No arranco el duelo {} vs {}: alguno tiene un inventario sin devolver",
					uno, otro);
			primero.sendSystemMessage(Carteles.inventarioSinDevolver());
			segundo.sendSystemMessage(Carteles.inventarioSinDevolver());
			return false;
		}

		// Las dos copias se guardan aca, juntas y antes de tocarle el inventario a
		// nadie. Guardarlas adentro de meterEnLaArena dejaria una ventana en la que
		// al primero ya se le vacio el inventario y al segundo todavia no se le
		// guardo el suyo.
		if (!registro.anotar(Guardado.de(primero))) return false;
		if (!registro.anotar(Guardado.de(segundo))) {
			registro.olvidar(uno);
			return false;
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
		// aca, nadie perdio todavia nada de lo suyo.
		if (apuesta > 0) {
			Puntos.sumarShards(servidor, uno, -apuesta);
			Puntos.sumarShards(servidor, otro, -apuesta);
		}

		meterEnLaArena(primero, arena.punto1, arena.giro1, nivel);
		meterEnLaArena(segundo, arena.punto2, arena.giro2, nivel);

		barra = new ServerBossEvent(java.util.UUID.randomUUID(),
				Carteles.barraApuestas(uno, otro, 0, 0),
				BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

		servidor.getPlayerList().broadcastSystemMessage(
				Carteles.arrancaElDuelo(uno, otro, apuesta, kit.nombre(), TICKS_APUESTAS / 20), false);

		titulo(publico(), Component.literal(""), Carteles.elKit(kit.nombre(), kit.comoSePelea()),
				5, 60, 10);
		sonarParaTodos();

		LOG.info("Duelo: {} vs {} ({} shards, kit {})", uno, otro, apuesta, kit.nombre());
		return true;
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
	 *
	 * Se repone porque el reloj baja un tick por tick en `sdp:tick` y un duelo dura
	 * mas que los quince segundos que dura la marca. Y va con reloj y no con la
	 * etiqueta sola: ver `Puntos.marcarEnPelea`.
	 */
	private void marcarEnPelea(ServerPlayer quien) {
		Puntos.marcarEnPelea(servidor, quien, 300);
	}

	// ------------------------------------------------------------------- cada tick

	public void tick() {
		if (etapa == Etapa.LISTO) return;

		ServerPlayer primero = jugador(uno);
		ServerPlayer segundo = jugador(otro);

		// Que alguien se vaya en el medio termina el duelo, en cualquier etapa.
		if (primero == null || segundo == null) {
			if (etapa == Etapa.FINAL) {
				if (--reloj <= 0) desarmar();
				return;
			}
			seFue(primero == null ? uno : otro);
			return;
		}

		switch (etapa) {
			case APUESTAS -> tickApuestas(primero, segundo);
			case CUENTA -> tickCuenta(primero, segundo);
			case PELEA -> tickPelea(primero, segundo);
			case FINAL -> tickFinal(primero, segundo);
			default -> { }
		}

		if (etapa == Etapa.LISTO) return;
		// Una vez por segundo alcanza: el reloj de la marca dura quince.
		if (servidor.getTickCount() % 20 == 0) {
			marcarEnPelea(primero);
			marcarEnPelea(segundo);
		}
		// Ya se deshizo todo: la arena vuelve a ser un lugar como cualquier otro.
		sacarALosDeAfuera();
	}

	private void tickApuestas(ServerPlayer primero, ServerPlayer segundo) {
		quietos(primero, segundo);
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
	 * La cuenta regresiva. Los numeros salen a los 60, 40 y 20 ticks que quedan,
	 * o sea uno por segundo, y el ¡PELEEN! en el cero.
	 *
	 * El `stay` de cada titulo es de 20 ticks justos para que el numero se vaya
	 * cuando entra el siguiente y no se pisen dos en pantalla.
	 */
	private void tickCuenta(ServerPlayer primero, ServerPlayer segundo) {
		quietos(primero, segundo);
		refrescarBarra(Carteles.barraPelea(uno, otro), 1.0f, BossEvent.BossBarColor.RED);

		switch (reloj) {
			case 60, 40, 20 -> {
				int numero = reloj / 20;
				titulo(publico(), Carteles.numeroDeLaCuenta(numero), Component.empty(), 0, 20, 0);
				// El pim: la misma nota tres veces, cada vez mas aguda. Es lo que hace
				// que la cuenta se sienta aunque no se mire la pantalla.
				sonar(SoundEvents.NOTE_BLOCK_PLING, 1.6f, numero == 3 ? 1.0f : numero == 2 ? 1.2f : 1.5f);
			}
			case 0 -> arrancarLaPelea(primero, segundo);
			default -> { }
		}
		reloj--;
	}

	private void arrancarLaPelea(ServerPlayer primero, ServerPlayer segundo) {
		etapa = Etapa.PELEA;
		reloj = TICKS_PELEA;
		descongelar(primero);
		descongelar(segundo);

		titulo(publico(), Carteles.aPelear(), Component.empty(), 0, 25, 10);
		primero.connection.send(new ClientboundSetSubtitleTextPacket(Carteles.contra(otro)));
		segundo.connection.send(new ClientboundSetSubtitleTextPacket(Carteles.contra(uno)));
		sonar(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
		sonar(SoundEvents.RAID_HORN, 1.2f, 1.2f);
	}

	private void tickPelea(ServerPlayer primero, ServerPlayer segundo) {
		adentroDeLaArena(primero);
		adentroDeLaArena(segundo);
		refrescarBarra(Carteles.barraPelea(uno, otro), (float) reloj / TICKS_PELEA,
				BossEvent.BossBarColor.RED);

		if (--reloj > 0) return;

		// Se acabo el tiempo: gana el que llego con mas vida. Es la unica forma de
		// cortar una pelea de dos que se esconden y no se buscan.
		float vidaUno = primero.getHealth();
		float vidaOtro = segundo.getHealth();
		if (Math.abs(vidaUno - vidaOtro) < 0.01f) {
			terminarEnEmpate();
		} else {
			boolean ganaUno = vidaUno > vidaOtro;
			terminar(ganaUno ? uno : otro, ganaUno ? otro : uno,
					"Se acabaron los cinco minutos: gano el que llego con mas vida.");
		}
	}

	private void tickFinal(ServerPlayer primero, ServerPlayer segundo) {
		quietos(primero, segundo);
		if (--reloj <= 0) desarmar();
	}

	// ------------------------------------------------------------------ resultados

	/** Lo llama el enganche de la muerte: este se iba a morir y no lo dejamos. */
	public void caido(ServerPlayer quien) {
		String muerto = quien.getScoreboardName();
		terminar(muerto.equalsIgnoreCase(uno) ? otro : uno, muerto, "Lo dejo en cero.");
	}

	public void rendirse(String quien) {
		terminar(quien.equalsIgnoreCase(uno) ? otro : uno, quien, quien + " se rindio.");
	}

	/** Se desconecto en el medio. Pierde, que es lo unico que no premia irse. */
	public void seFue(String quien) {
		if (etapa == Etapa.FINAL || etapa == Etapa.LISTO) return;
		if (etapa == Etapa.APUESTAS) {
			// Todavia no habia empezado nada: no hay ganador ni perdedor, vuelve todo.
			cancelar(Carteles.seFueElOtro(quien));
			return;
		}
		terminar(quien.equalsIgnoreCase(uno) ? otro : uno, quien, quien + " se desconecto.");
	}

	private void terminar(String ganador, String perdedor, String comoFue) {
		if (etapa == Etapa.FINAL || etapa == Etapa.LISTO) return;
		this.ganador = ganador;
		this.perdedor = perdedor;
		this.comoFue = comoFue;
		this.empate = false;
		etapa = Etapa.FINAL;
		reloj = TICKS_FINAL;

		sanar(jugador(ganador));
		sanar(jugador(perdedor));
		congelarSiEsta(ganador);
		congelarSiEsta(perdedor);

		if (apuesta > 0) Puntos.sumarShards(servidor, ganador, apuesta * 2);
		Puntos.sumarGanado(servidor, ganador);
		Puntos.sumarPerdido(servidor, perdedor);

		pagarLasApuestas(ganador.equalsIgnoreCase(uno));

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

		sanar(jugador(uno));
		sanar(jugador(otro));
		congelarSiEsta(uno);
		congelarSiEsta(otro);

		if (apuesta > 0) {
			Puntos.sumarShards(servidor, uno, apuesta);
			Puntos.sumarShards(servidor, otro, apuesta);
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
			Puntos.sumarShards(servidor, uno, apuesta);
			Puntos.sumarShards(servidor, otro, apuesta);
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
		// Lo que no dio entero se lo lleva el que peleo y gano. Tiene que ir a algun
		// lado o los shards se irian evaporando de a uno por duelo.
		if (reparto.sobra() > 0) Puntos.sumarShards(servidor, ganador, reparto.sobra());
		LOG.info("Apuestas del duelo: {}", Carteles.repartoParaElLog(reparto.cobran()));
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
		ServerPlayer gano = jugador(ganador);
		ServerPlayer perdio = jugador(perdedor);

		for (ServerPlayer quien : publico()) {
			boolean esElGanador = quien.getScoreboardName().equalsIgnoreCase(ganador);
			boolean esElPerdedor = quien.getScoreboardName().equalsIgnoreCase(perdedor);
			Component grande = esElGanador ? Carteles.tituloGanaste()
					: esElPerdedor ? Carteles.tituloPerdiste()
					: Carteles.tituloGano(ganador);
			Component chico = esElGanador || esElPerdedor
					? Component.empty() : Carteles.subtituloGano();
			titulo(List.of(quien), grande, chico, 5, 70, 20);
		}

		sonar(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
		sonar(SoundEvents.FIREWORK_ROCKET_BLAST, 2.0f, 1.0f);
		if (gano != null) {
			ServerLevel nivel = (ServerLevel) gano.level();
			nivel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
					gano.getX(), gano.getY() + 1.0, gano.getZ(), 80, 0.6, 0.8, 0.6, 0.3);
			nivel.sendParticles(ParticleTypes.END_ROD,
					gano.getX(), gano.getY() + 2.2, gano.getZ(), 40, 0.4, 0.4, 0.4, 0.05);
		}
		if (perdio != null) {
			((ServerLevel) perdio.level()).sendParticles(ParticleTypes.SMOKE,
					perdio.getX(), perdio.getY() + 1.0, perdio.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
		}
	}

	/**
	 * Deshace todo. Es lo ultimo que corre y no puede fallar a medias: primero se
	 * les devuelve lo suyo a los jugadores y recien despues se arregla la arena,
	 * porque lo que no se puede perder son los inventarios.
	 */
	private void desarmar() {
		etapa = Etapa.LISTO;

		devolverLoSuyo(uno);
		devolverLoSuyo(otro);

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

		boolean aFavorDeUno;
		if (aQuien.equalsIgnoreCase(uno)) aFavorDeUno = true;
		else if (aQuien.equalsIgnoreCase(otro)) aFavorDeUno = false;
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
		return nombre.equalsIgnoreCase(uno) || nombre.equalsIgnoreCase(otro);
	}

	/** Mientras no se pelea de verdad, a los dos no los toca nada ni nadie. */
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

	// ----------------------------------------------------------------- lo de adentro

	private ServerPlayer jugador(String nombre) {
		return nombre == null ? null : servidor.getPlayerList().getPlayerByName(nombre);
	}

	private void quietos(ServerPlayer primero, ServerPlayer segundo) {
		primero.setDeltaMovement(0, 0, 0);
		segundo.setDeltaMovement(0, 0, 0);
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
		int cuanto = TICKS_APUESTAS + TICKS_CUENTA + 200;
		quien.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, cuanto, 255, false, false, false));
		quien.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, cuanto, 128, false, false, false));
		quien.setDeltaMovement(0, 0, 0);
	}

	private void congelarSiEsta(String quien) {
		ServerPlayer conectado = jugador(quien);
		if (conectado != null) congelar(conectado);
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

	/** Al que se fue de la caja lo devuelve al borde por donde se iba. */
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

		Vec3 vuelta = arena.devolverAdentro(quien.position());
		quien.teleportTo((ServerLevel) quien.level(), vuelta.x, vuelta.y, vuelta.z,
				Set.<Relative>of(), quien.getYRot(), quien.getXRot(), false);
		quien.setDeltaMovement(0, 0, 0);
		quien.resetFallDistance();
	}

	/**
	 * Saca de la arena a cualquiera que no sea uno de los dos.
	 *
	 * Es lo que hace que una pelea sea una pelea y no una montonera: sin esto, el
	 * primero que salta adentro le pega al que va ganando y el duelo deja de
	 * decidir nada. Se los manda dos bloques afuera de la pared mas cercana, que
	 * es de donde vinieron, y el aviso va cada dos segundos para no llenarle el
	 * chat al que esta saltando en el borde.
	 */
	private void sacarALosDeAfuera() {
		ServerLevel nivel = arena.nivel(servidor);
		if (nivel == null) return;
		avisoDeAfuera--;

		for (ServerPlayer quien : nivel.players()) {
			if (esDuelista(quien.getScoreboardName())) continue;
			if (!arena.contiene(quien.position())) continue;

			Vec3 afuera = arena.sacarAfuera(nivel, quien.position());
			quien.teleportTo(nivel, afuera.x, afuera.y, afuera.z,
					Set.<Relative>of(), quien.getYRot(), quien.getXRot(), false);
			quien.setDeltaMovement(0, 0, 0);
			quien.resetFallDistance();
			if (avisoDeAfuera <= 0) {
				quien.sendSystemMessage(Carteles.teSacamosDeLaArena());
				quien.level().playSound(null, quien.getX(), quien.getY(), quien.getZ(),
						SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.2f, 1.8f);
			}
		}
		if (avisoDeAfuera <= 0) avisoDeAfuera = 40;
	}

	/**
	 * Quienes ven la barra y los titulos: los dos que pelean, los que estan cerca
	 * de la arena y los que apostaron.
	 *
	 * Los que apostaron aunque esten lejos, porque tienen shards adentro de esa
	 * pelea y el resultado les importa mas que a nadie.
	 */
	private List<ServerPlayer> publico() {
		Set<ServerPlayer> gente = new LinkedHashSet<>();
		ServerPlayer primero = jugador(uno);
		ServerPlayer segundo = jugador(otro);
		if (primero != null) gente.add(primero);
		if (segundo != null) gente.add(segundo);

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
