package pe.sobrinosdepepe.lobby;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * El lobby: al entrar caen todos ahi, con las manos vacias, y al Survival se va
 * a mano desde el menu.
 *
 * Por que un mod y no la config de EasyAuth. `hide-player-coords` mandaba al lobby
 * al que no estaba logueado, pero lo devolvia a su casa apenas escribia `/login`.
 * Queremos lo otro: que **todos** caigan en el lobby —esten logueados o no, entren
 * con el launcher o sin el— y que al Survival se entre apretando un boton. Eso
 * pide guardarle al jugador donde estaba y que tenia, y eso no lo hace ninguna
 * config.
 *
 * Como se ve desde adentro del juego:
 *
 *   1. Entras y caes en el patio del lobby, con el inventario vacio y una perla
 *      en la mano. Tus cosas NO estan perdidas: estan guardadas (ver Guardado).
 *   2. Si no estas autenticado, EasyAuth no te deja mover hasta que escribas
 *      `/register` o `/login`. Eso no lo hace este mod y no hay que tocarlo.
 *   3. Click derecho con la perla y se abre el menu de juegos. Hoy tiene un solo
 *      modo, el Survival SMP.
 *   4. Lo elegis y aparecas **exactamente donde estabas**, con todo lo tuyo: misma
 *      dimension, misma posicion, mismo inventario, misma vida y misma experiencia.
 *   5. `/lobby` te trae de vuelta cuando quieras, salvo que estes en combate.
 *
 * En el lobby no se rompe, no se pone y no se pega: son tres eventos de Fabric,
 * abajo. Lo que NO esta tapado es tirar items al piso; si alguien tira la perla,
 * le queda `/survival`, que hace lo mismo que el boton del menu.
 *
 * El que se cae del borde lo sube `sdp:tick`, que es del datapack y no de aca:
 * el vacio es del mundo, no del mod.
 *
 * ## El lobby y el coliseo son cajas, no dimensiones
 *
 * Hasta la mudanza del 2026-09-22 cada uno era una dimension propia (`sdp:lobby`
 * y `sdp:coliseo`). Eso rompia el login: al conectarse alguien que habia quedado
 * guardado adentro de una, el servidor lo agregaba dos veces al mundo
 * ("Force-added player with duplicate UUID") y al cliente le quedaba el mapa vacio
 * cargando para siempre. Se probaron las tres formas de evitarlo y las tres
 * fallan, porque el problema es que existan dos niveles capaces de discutir de
 * quien es el UUID.
 *
 * Ahora los dos viven en el **overworld**, a 500.000 bloques del spawn y por
 * arriba de las nubes, y "estar en el lobby" se pregunta por posicion. Con un solo
 * nivel el problema no puede volver.
 */
public final class LobbyServidor implements DedicatedServerModInitializer {

	public static final String ID = "lobbydepepe";
	static final Logger LOG = LoggerFactory.getLogger(ID);

	/**
	 * Un pedazo de overworld, mirado desde arriba.
	 *
	 * No lleva altura a proposito: es toda la columna, de la roca madre al cielo.
	 * El que se cae del lobby tiene que seguir contando como que esta en el lobby
	 * mientras cae, o se quedaria sin proteccion justo cuando el datapack lo va a
	 * subir de vuelta al patio.
	 */
	record Caja(int x1, int z1, int x2, int z2) {
		boolean tiene(Entity quien) {
			if (quien == null || !quien.level().dimension().equals(Level.OVERWORLD)) return false;
			return quien.getX() >= x1 && quien.getX() <= x2
					&& quien.getZ() >= z1 && quien.getZ() <= z2;
		}
	}

	// Las dos cajas, con margen de sobra alrededor de lo construido: el lobby ocupa
	// x 499.970..500.031 z -30..30 y el coliseo x 501.145..501.205 z 11.590..11.650.
	// Estan a 11.600 bloques una de otra, asi que no hay forma de confundirlas.
	static final Caja LOBBY = new Caja(499950, -50, 500050, 50);
	static final Caja COLISEO = new Caja(501125, 11570, 501225, 11670);

	// Las gradas del coliseo, que es el punto que ya estaba marcado con
	// `/pvp arena gradas` y mira a la cancha. Lo mismo que hay en la seccion
	// "gradas" de config/duelos-de-pepe.json; el mod de duelos es el dueño de ese
	// punto y aca esta copiado porque los dos mods no se hablan.
	static final double CX = 501163.42;
	static final double CY = 267.0;
	static final double CZ = 11631.92;
	static final float CGIRO = -139.35f;
	static final float CMIRA = 8.61f;

	// El centro del patio. El piso solido esta en y=249, asi que se para en y=250.
	// Esto mismo esta en SPAWN de servidor/subir-lobby.py; si se mueve, en los dos.
	static final double X = 500000.5;
	static final double Y = 250.0;
	static final double Z = 0.5;
	static final float GIRO = 0.0f;
	static final float MIRA = 0.0f;

	/** La marca que el datapack le pone al que esta peleando. */
	static final String COMBATE = "sdp_combate";

	private Vuelta vuelta;
	private Mirador mirador;

	@Override
	public void onInitializeServer() {
		vuelta = new Vuelta(LOG);
		mirador = new Mirador();

		ServerLifecycleEvents.SERVER_STARTED.register(servidor -> vuelta.alArrancar(servidor));

		// El que recorre el coliseo de espectador no puede irse volando: a 500.000
		// del spawn, el que cruza la pared no vuelve nunca.
		ServerTickEvents.END_SERVER_TICK.register(servidor -> mirador.tick(servidor));

		// Al entrar, al lobby, pero en el tick SIGUIENTE y no adentro del propio
		// evento. Esto costo un bug feo y no es un detalle de estilo: mover al
		// jugador de dimension mientras el login todavia esta en curso lo agrega dos
		// veces al mundo ("Force-added player with duplicate UUID" en el log) y al
		// cliente le queda el mapa entero vacio. servidor.execute() corre la tarea
		// cuando el tick actual termino, con el jugador ya adentro del mundo.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, servidor) -> {
			ServerPlayer quien = handler.getPlayer();
			servidor.execute(() -> {
				// Entre el JOIN y este tick el jugador se puede haber ido, y entonces
				// esto ya no es su jugador sino una copia colgada del mundo. Paso de
				// verdad el 2026-09-22: un login que tardo ocho minutos, LuckPerms lo
				// rechazo por viejo ("doesn't currently have data pre-loaded"), y en el
				// log quedo un `left the game` ANTES del `joined the game`. Tocarle el
				// inventario a eso es escribirle la copia a alguien que no esta.
				//
				// Se pregunta por la CONEXION y no por la lista de jugadores. Preguntar
				// por la lista estuvo mal medio dia y rechazaba los logins buenos: para
				// cuando corre esta tarea el jugador todavia no esta anotado ahi —el
				// "joined the game" sale despues— asi que daba "se fue" siempre y nadie
				// llegaba al lobby.
				if (!handler.isAcceptingMessages()) {
					LOG.warn("{} se fue antes de que lo pudiera mandar al lobby: no lo toco",
							quien.getName().getString());
					return;
				}
				alEntrar(quien, servidor);
			});
		});

		// NO se le toca NADA al que se desconecta. Esto costo inventarios de verdad y
		// es la regla mas importante de este mod.
		//
		// Habia un DISCONNECT que le devolvia sus cosas al que se iba desde el lobby.
		// El evento corre en el hilo de RED (en el log se ve: "Netty Epoll IO #56"),
		// no en el del servidor, y para cuando corre, el servidor ya escribio el
		// archivo del jugador —vacio, porque en el lobby esta vacio—. Le devolviamos
		// el inventario a una copia en memoria que ya nadie iba a guardar, y encima
		// borrabamos la copia buena del JSON. Resultado: el jugador entraba sin nada
		// y no quedaba de donde sacarlo.
		//
		// Ahora la copia se consume en UN SOLO lugar, siempre con el jugador adentro
		// del juego y en el hilo del servidor: al entrar o al apretar SURVIVAL. Una
		// sola puerta de salida es lo que hace imposible perderlo y tambien
		// duplicarlo.

		// Antes habia tambien un LEAVE que sacaba del lobby al que se desconectaba.
		// Ya no hace falta y por eso no esta: existia porque nadie podia quedar
		// guardado adentro de una dimension nuestra, y ahora el lobby es un pedazo
		// del overworld. El que se va desde el lobby queda guardado ahi mismo, entra
		// ahi la proxima vez, y aprieta SURVIVAL cuando quiera.
		//
		// Que no vuelva. Cada vez que este mod le toco algo al que se estaba yendo,
		// alguien perdio el inventario.

		CommandRegistrationCallback.EVENT.register((dispatcher, registro, entorno) ->
				new ComandoLobby(vuelta, mirador).registrar(dispatcher));

		// Las tres protecciones del lobby. Los operadores quedan afuera: son los
		// que tienen que poder arreglar el mundo sin sacar el mod.
		PlayerBlockBreakEvents.BEFORE.register((nivel, quien, donde, estado, bloque) ->
				!(quien instanceof ServerPlayer jugador) || !protegido(jugador));

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((quien, fuente, cuanto) ->
				!(quien instanceof ServerPlayer jugador) || !enLobby(jugador));

		// El click derecho sobre un bloque: si es con la perla abre el menu, y si no
		// no hace nada. Los dos casos cancelan, que es lo que evita que alguien
		// ponga un bloque o abra algo en el lobby.
		UseBlockCallback.EVENT.register((quien, nivel, mano, golpe) -> {
			if (!(quien instanceof ServerPlayer jugador) || !enLasNuestras(jugador)) {
				return InteractionResult.PASS;
			}
			if (Perla.es(jugador.getItemInHand(mano))) {
				Perla.abrirMenu(jugador);
				return InteractionResult.SUCCESS;
			}
			return protegido(jugador) ? InteractionResult.FAIL : InteractionResult.PASS;
		});

		// El click derecho al aire. Cancelar es lo que hace que la perla no se lance
		// ni se gaste: es el menu, no una perla de verdad.
		UseItemCallback.EVENT.register((quien, nivel, mano) -> {
			if (quien instanceof ServerPlayer jugador && enLasNuestras(jugador)
					&& Perla.es(jugador.getItemInHand(mano))) {
				Perla.abrirMenu(jugador);
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});

		// Ni en el lobby ni en el coliseo hay bichos. Nunca, haya pelea o no.
		//
		// El mod de duelos ya barre hostiles adentro de la caja de la arena, pero un
		// creeper paseando por afuera del ring, o un phantom bajando sobre las
		// gradas, arruinan igual. Se borra al aparecer, que sale una vez por bicho,
		// en vez de barrer cada tick.
		//
		// El lobby entro en esta regla con la mudanza: era una dimension vacia y
		// ahora es un pedazo de overworld a y=250, donde de noche aparecen phantoms
		// y en cualquier sombra un zombi.
		//
		// Se respeta al invulnerable: es la marca que deja la varita de zonas sobre
		// un animal que alguien quiso cuidar, y `discard()` se lo llevaria puesto.
		ServerEntityEvents.ENTITY_LOAD.register((entidad, nivel) -> {
			if (!(entidad instanceof Mob bicho) || bicho.isInvulnerable()) return;
			if (LOBBY.tiene(entidad) || COLISEO.tiene(entidad)) bicho.discard();
		});

		LOG.info("Lobby de Pepe listo: el lobby esta en el overworld, en {} {} {}", X, Y, Z);
	}

	/**
	 * Lo manda al lobby, salvo que ya este ahi.
	 *
	 * El orden de los tres casos es lo unico que importa acá, y esta puesto asi
	 * para que sea **imposible** anotar como "las cosas de fulano" un inventario
	 * que no son sus cosas:
	 *
	 *  1. Si aparece en el **coliseo**, se lo lleva al lobby y no se le anota nada:
	 *     ahi tiene un kit prestado o la perla, y anotar eso le borraria el equipo.
	 *  2. Si aparece en el **lobby**, se le repone la perla si la perdio y listo.
	 *     Tampoco se anota: en el lobby el inventario esta vacio a proposito.
	 *  3. Si aparece en el **survival** teniendo una copia sin devolver, se le
	 *     devuelve TODO primero y recien despues se lo manda al lobby, que lo anota
	 *     con lo suyo ya puesto.
	 *
	 * El caso 3 es el que salva a los que quedaron guardados adentro de las
	 * dimensiones viejas: al borrarse `sdp:lobby`, el juego los deja en el spawn del
	 * overworld con el inventario vacio y la perla en la mano. Antes se miraba si
	 * tenia la perla para decidir si devolverle, y con eso justamente no se le
	 * devolvia nada y se le pisaba la copia buena. Ahora la perla no decide nada:
	 * decide donde esta parado.
	 */
	private void alEntrar(ServerPlayer quien, MinecraftServer servidor) {
		// Si se cayo el servidor mientras recorria el coliseo, vuelve de espectador
		// y con la marca puesta. Se le devuelve el modo antes que nada: de
		// espectador no puede ni abrir la perla.
		mirador.alEntrar(quien);

		if (enColiseo(quien)) {
			soloMover(quien, servidor);
			Carteles.bienvenida(quien);
			return;
		}
		if (enLobby(quien)) {
			if (!tienePerla(quien)) {
				quien.getInventory().setItem(0, Perla.nueva());
				quien.inventoryMenu.broadcastChanges();
			}
			Carteles.bienvenida(quien);
			return;
		}
		if (vuelta.de(quien) != null) {
			vuelta.de(quien).devolverA(quien, servidor);
			vuelta.borrar(quien);
			LOG.info("{} entro en el survival con una copia sin devolver: se la devolvi",
					quien.getName().getString());
		}
		if (mandarAlLobby(quien, servidor, vuelta)) {
			Carteles.bienvenida(quien);
		}
	}

	/**
	 * Anota lo que tiene, lo mueve y lo deja con la perla sola.
	 *
	 * El orden importa y es el unico posible: primero se escribe la copia en el
	 * archivo, y recien despues se le toca el inventario. Si la escritura falla no
	 * se le vacia nada y se le avisa; que el lobby quede feo con alguien vestido de
	 * netherita es infinitamente mejor que perderle el equipo.
	 */
	static boolean mandarAlLobby(ServerPlayer quien, MinecraftServer servidor, Vuelta vuelta) {
		if (!vuelta.anotar(quien)) {
			LOG.error("No pude guardar las cosas de {}: no lo muevo al lobby.",
					quien.getName().getString());
			Carteles.noPudeGuardar(quien);
			return false;
		}

		// Cuantas cosas se le guardaron, en el log. Es una linea por entrada al
		// lobby y vale lo que cuesta: la unica forma de enterarse de que a alguien
		// se le guardo un inventario que no era el suyo es ver el numero raro.
		LOG.info("{} va al lobby: le guarde {} cosas", quien.getName().getString(),
				vuelta.de(quien).cuantasCosas());

		quien.teleportTo(servidor.overworld(), X, Y, Z, Set.<Relative>of(), GIRO, MIRA, false);
		pieEnTierra(quien);
		quien.getInventory().clearContent();
		quien.getInventory().setItem(0, Perla.nueva());
		quien.inventoryMenu.broadcastChanges();
		return true;
	}

	/**
	 * Lo mueve al lobby y nada mas: no le anota nada ni le toca el inventario.
	 *
	 * Es para el que vuelve del coliseo, que ya tiene la perla y cuyas cosas de
	 * verdad estan anotadas desde antes.
	 */
	static boolean soloMover(ServerPlayer quien, MinecraftServer servidor) {
		quien.teleportTo(servidor.overworld(), X, Y, Z, Set.<Relative>of(), GIRO, MIRA, false);
		pieEnTierra(quien);
		return true;
	}

	/**
	 * Al patio no se llega volando.
	 *
	 * Es la red abajo del trapecio: el que entra al lobby de espectador es alguien
	 * que salio del modo de recorrer el coliseo por un camino que no conociamos, y
	 * si no se lo saca acá se queda volando —y peor, aprieta SURVIVAL y entra al
	 * mundo volando, que es un modo creativo de contrabando—. Al creativo no se lo
	 * toca: ese se lo puso un operador a proposito.
	 */
	private static void pieEnTierra(ServerPlayer quien) {
		if (quien.gameMode() != GameType.SPECTATOR) return;
		quien.setGameMode(GameType.SURVIVAL);
		LOG.info("{} llego al lobby de espectador: lo puse en survival",
				quien.getName().getString());
	}

	static boolean enLobby(Entity quien) {
		return LOBBY.tiene(quien);
	}

	static boolean enColiseo(Entity quien) {
		return COLISEO.tiene(quien);
	}

	/**
	 * Los dos lugares nuestros: el lobby y el coliseo.
	 *
	 * Importa para una sola cosa: la perla abre el menu en los dos, porque si no el
	 * click derecho la lanza de verdad y el jugador termina teletransportado a un
	 * rincon del coliseo.
	 *
	 * Lo que NO se comparte son las protecciones: en el coliseo se rompe y se
	 * pelea a proposito, y de eso se ocupa el mod de duelos.
	 */
	static boolean enLasNuestras(Entity quien) {
		return enLobby(quien) || enColiseo(quien);
	}

	private static boolean tienePerla(ServerPlayer quien) {
		for (int i = 0; i < quien.getInventory().getContainerSize(); i++) {
			if (Perla.es(quien.getInventory().getItem(i))) {
				return true;
			}
		}
		return false;
	}

	/** En el lobby no se toca nada, salvo que seas operador. */
	private static boolean protegido(ServerPlayer quien) {
		return enLobby(quien)
				&& !quien.level().getServer().getPlayerList().isOp(quien.nameAndId());
	}

	/** El que esta peleando no se escapa con un comando. */
	static boolean peleando(LivingEntity quien) {
		return quien.entityTags().contains(COMBATE);
	}
}
