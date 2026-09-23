package pe.sobrinosdepepe.duelos;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * `/pvp`, con todo colgado del mismo comando.
 *
 *     /pvp                          el menu de siempre, que lo sigue abriendo Melius
 *     /pvp <jugador> [shards]       retarlo a un duelo
 *     /pvp aceptar <jugador>        entrar a la arena contra el
 *     /pvp rechazar <jugador>       decirle que no
 *     /pvp rendirse                 abandonar el duelo que estas peleando
 *     /pvp apostar <jugador> <n>    ponerle shards a uno de los dos
 *     /pvp ver                      mirar la pelea de espectador, volando
 *     /pvp top                      los que mas duelos ganaron
 *     /pvp duelos                   como funciona todo esto
 *     /pvp arena ...                marcar y mirar la arena (operadores)
 *     /pvp torneo ...               el torneo
 *
 * **El `/pvp` pelado no se toca aca a proposito.** Ese ya existe: es un comando
 * de Melius (`servidor/comandos/pvp.json`) que abre el menu de cofre `sdp:pvp`, y
 * es el que tiene el `op_level: 4` que hace que el menu se le abra tambien al que
 * no es operador. Este comando registra solamente los hijos, sin `executes` en la
 * raiz, y Brigadier los fusiona: `CommandNode.addChild` pisa el `command` del nodo
 * solo si el nuevo trae uno, asi que con el nuestro en null el de Melius sobrevive
 * sin importar cual de los dos mods registre primero.
 *
 * No lleva `requires` en la raiz: lo usa cualquiera sin ser operador, que es la
 * unica forma de que sirva. Los que si son del que administra —los de `arena` y
 * los de abrir y arrancar el torneo— lo piden nodo por nodo.
 *
 * Todo contesta por `sendSystemMessage` al jugador y no por `sendSuccess`: con
 * sendSuccess el mensaje se le copia a los operadores conectados, y los retos de
 * los demas no son asunto de nadie.
 */
public final class ComandoPvp {
	/**
	 * Cuanto mide de alto una arena sacada de una zona protegida, si no se dice
	 * otra cosa. Las zonas de Safe Zone no tienen altura —la proteccion es la
	 * columna entera— asi que el alto lo pone este comando. 24 le entran bien a un
	 * coliseo con gradas; si el techo esta mas arriba, `/pvp arena guardar 40`.
	 */
	private static final int ALTO_POR_DEFECTO = 24;

	private final Duelos duelos;
	private final Registro registro;
	private final Varita varita;
	private final Torneo torneo;

	public ComandoPvp(Duelos duelos, Registro registro, Varita varita, Torneo torneo) {
		this.duelos = duelos;
		this.registro = registro;
		this.varita = varita;
		this.torneo = torneo;
	}

	public void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("pvp")
				// Los shards van antes que la clase a proposito: Brigadier prueba los
				// argumentos en el orden en que se registraron, asi que con el numero
				// primero, "/pvp Fulano 50" entra por los shards y "/pvp Fulano CUERO"
				// falla el numero y cae en la clase. Al reves, "50" seria un nombre de
				// clase que no existe.
				.then(Commands.argument("jugador", EntityArgument.player())
						.executes(c -> retar(c, 0, null))
						.then(Commands.argument("shards", IntegerArgumentType.integer(0))
								.executes(c -> retar(c, IntegerArgumentType.getInteger(c, "shards"), null))
								.then(Commands.argument("clase", StringArgumentType.word())
										.suggests((c, sug) -> SharedSuggestionProvider.suggest(Kits.NOMBRES, sug))
										.executes(c -> retar(c, IntegerArgumentType.getInteger(c, "shards"),
												StringArgumentType.getString(c, "clase")))))
						.then(Commands.argument("clase", StringArgumentType.word())
								.suggests((c, sug) -> SharedSuggestionProvider.suggest(Kits.NOMBRES, sug))
								.executes(c -> retar(c, 0, StringArgumentType.getString(c, "clase")))))
				.then(Commands.literal("aceptar")
						.then(Commands.argument("jugador", StringArgumentType.word())
								.suggests((c, s) -> SharedSuggestionProvider.suggest(
										duelos.invitaciones().quienesRetaron(c.getSource().getTextName()), s))
								.executes(this::aceptar)))
				.then(Commands.literal("rechazar")
						.then(Commands.argument("jugador", StringArgumentType.word())
								.suggests((c, s) -> SharedSuggestionProvider.suggest(
										duelos.invitaciones().quienesRetaron(c.getSource().getTextName()), s))
								.executes(this::rechazar)))
				.then(Commands.literal("rendirse").executes(this::rendirse))
				.then(Commands.literal("apostar")
						.then(Commands.argument("jugador", StringArgumentType.word())
								.suggests((c, s) -> SharedSuggestionProvider.suggest(losQuePelean(), s))
								.then(Commands.argument("shards", IntegerArgumentType.integer(1))
										.executes(this::apostar))))
				.then(Commands.literal("ver").executes(this::ver))
				.then(Commands.literal("volver").executes(this::dejarDeMirar))
				.then(Commands.literal("cola")
						.executes(c -> anotarse(c, "1v1"))
						.then(Commands.argument("modo", StringArgumentType.word())
								.suggests((c, s) -> SharedSuggestionProvider.suggest(
										new String[] {"1v1", "2v2", "equipos"}, s))
								.executes(c -> anotarse(c, StringArgumentType.getString(c, "modo")))))
				.then(Commands.literal("salir").executes(this::salirDeLaCola))
				.then(Commands.literal("mesa")
						.executes(this::abrirMesa)
						.then(Commands.argument("modo", StringArgumentType.word())
								.suggests((c, s) -> SharedSuggestionProvider.suggest(
										new String[] {"1v1", "2v2"}, s))
								.executes(c -> armarMesa(c, StringArgumentType.getString(c, "modo")))))
			.then(Commands.literal("top").executes(this::top))
				.then(Commands.literal("duelos").executes(this::comoEs))
				.then(Commands.literal("probar")
						.requires(ComandoPvp::esOperador)
						.executes(this::probar))
				.then(Commands.literal("arena")
						.requires(ComandoPvp::esOperador)
						.executes(this::verArena)
						.then(Commands.literal("guardar")
								.executes(c -> guardarArena(c, ALTO_POR_DEFECTO))
								.then(Commands.argument("alto", IntegerArgumentType.integer(4, 320))
										.executes(c -> guardarArena(c,
												IntegerArgumentType.getInteger(c, "alto")))))
						.then(Commands.literal("zona")
								.then(Commands.argument("zona", StringArgumentType.word())
										.suggests((c, s) -> SharedSuggestionProvider.suggest(
												Zonas.ids(c.getSource().getServer()), s))
										.executes(c -> guardarDeZona(c, ALTO_POR_DEFECTO))
										.then(Commands.argument("alto", IntegerArgumentType.integer(4, 320))
												.executes(c -> guardarDeZona(c,
														IntegerArgumentType.getInteger(c, "alto"))))))
						.then(Commands.literal("punto1").executes(c -> ponerPunto(c, 1)))
						.then(Commands.literal("punto2").executes(c -> ponerPunto(c, 2)))
				.then(Commands.literal("borrar").executes(this::borrarArena)))
				.then(Commands.literal("torneo")
						.executes(this::verTorneo)
						.then(Commands.literal("entrar").executes(this::torneoEntrar))
						.then(Commands.literal("salir").executes(this::torneoSalir))
						.then(Commands.literal("abrir")
								.requires(ComandoPvp::esOperador)
								.executes(c -> torneoAbrir(c, 0))
								.then(Commands.argument("entrada", IntegerArgumentType.integer(0))
										.executes(c -> torneoAbrir(c,
												IntegerArgumentType.getInteger(c, "entrada")))))
						.then(Commands.literal("arrancar")
								.requires(ComandoPvp::esOperador)
								.executes(this::torneoArrancar))
						.then(Commands.literal("cancelar")
								.requires(ComandoPvp::esOperador)
								.executes(this::torneoCancelar))));
	}

	// ------------------------------------------------------------------- el reto

	/**
	 * `/pvp <jugador> [shards] [clase]`.
	 *
	 * La clase es opcional y sin ella sale una al azar, que es lo normal. Que se
	 * pueda elegir no rompe que la pelea sea pareja: los dos van con la misma
	 * igual, y al retado le llega escrita en el reto antes de apretar ACEPTAR. Es
	 * proponer una pelea, no imponerla.
	 */
	private int retar(CommandContext<CommandSourceStack> contexto, int apuesta, String clase)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		ServerPlayer aQuien = EntityArgument.getPlayer(contexto, "jugador");
		MinecraftServer servidor = contexto.getSource().getServer();

		String yo = quien.getScoreboardName();
		String el = aQuien.getScoreboardName();

		if (el.equalsIgnoreCase(yo)) return no(quien, Carteles.aVosMismo());
		if (clase != null && !Kits.existe(clase)) return no(quien, Carteles.claseQueNoExiste(clase));
		// Guardada siempre en mayusculas: asi el cartel del reto y el del duelo la
		// muestran igual sin importar como la haya tipeado el que reto.
		String laClase = clase == null ? null : clase.toUpperCase(java.util.Locale.ROOT);

		Component problema = sePuedePelear(servidor, quien, aQuien, apuesta, yo);
		if (problema != null) return no(quien, problema);
		if (duelos.invitaciones().buscar(el, yo) != null) {
			return no(quien, Carteles.yaLoRetaste(el));
		}

		duelos.invitaciones().retar(yo, el, apuesta, laClase);
		aQuien.sendSystemMessage(Carteles.teRetaron(yo, apuesta, laClase, Invitaciones.SEGUNDOS));
		aQuien.level().playSound(null, aQuien.getX(), aQuien.getY(), aQuien.getZ(),
				net.minecraft.sounds.SoundEvents.ANVIL_LAND,
				net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.6f);
		quien.sendSystemMessage(Carteles.retaste(el, apuesta, laClase, Invitaciones.SEGUNDOS));
		return 1;
	}

	private int aceptar(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		MinecraftServer servidor = contexto.getSource().getServer();
		String pedido = StringArgumentType.getString(contexto, "jugador");
		String yo = quien.getScoreboardName();

		Invitaciones.Reto reto = duelos.invitaciones().buscar(yo, pedido);
		if (reto == null) {
			List<String> otros = duelos.invitaciones().quienesRetaron(yo);
			return no(quien, otros.isEmpty() ? Carteles.sinRetos() : Carteles.noHayReto(pedido));
		}

		ServerPlayer retador = servidor.getPlayerList().getPlayerByName(reto.retador());
		if (retador == null) return no(quien, Carteles.noEstaConectado(reto.retador()));

		// Se vuelve a chequear todo: entre el reto y el enter pasaron hasta sesenta
		// segundos, y en el medio cualquiera de los dos pudo entrar a otra pelea o
		// gastarse los shards en la tienda.
		Component problema = sePuedePelear(servidor, retador, quien, reto.apuesta(), yo);
		if (problema != null) return no(quien, problema);

		int lugar = duelos.encolar(reto.retador(), yo, reto.apuesta(), reto.clase(), false);
		if (lugar > 1) {
			quien.sendSystemMessage(Carteles.enLaCola(lugar));
			retador.sendSystemMessage(Carteles.enLaCola(lugar));
		}
		return 1;
	}

	private int rechazar(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		String pedido = StringArgumentType.getString(contexto, "jugador");
		String yo = quien.getScoreboardName();

		Invitaciones.Reto reto = duelos.invitaciones().buscar(yo, pedido);
		if (reto == null) return no(quien, Carteles.noHayReto(pedido));

		duelos.invitaciones().olvidarUno(yo, reto.retador());
		quien.sendSystemMessage(Carteles.rechazaste(reto.retador()));
		ServerPlayer retador = contexto.getSource().getServer().getPlayerList()
				.getPlayerByName(reto.retador());
		if (retador != null) retador.sendSystemMessage(Carteles.teRechazaron(yo));
		return 1;
	}

	private int rendirse(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		Duelo duelo = duelos.actual();
		if (duelo == null || !duelo.esDuelista(quien.getScoreboardName())) {
			return no(quien, Carteles.noEstasPeleando());
		}
		if (duelo.etapa() != Duelo.Etapa.PELEA) {
			return no(quien, Carteles.mal("Todavia no empezo la pelea. Esperá la cuenta."));
		}
		duelo.rendirse(quien.getScoreboardName());
		return 1;
	}

	// ---------------------------------------------------------------- las apuestas

	private int apostar(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		String aQuien = StringArgumentType.getString(contexto, "jugador");
		int cuanto = IntegerArgumentType.getInteger(contexto, "shards");

		Duelo duelo = duelos.actual();
		if (duelo == null) return no(quien, Carteles.noHayDuelo());

		Component problema = duelo.apostar(quien, aQuien, cuanto);
		return problema == null ? 1 : no(quien, problema);
	}

	/**
	 * `/pvp ver`: te lleva a las gradas a mirar la pelea que se esta jugando.
	 *
	 * Es el boton que sale en el cartel de cada duelo, y por eso lo puede usar
	 * cualquiera sin ser operador: la gracia del coliseo es que lo mire todo el
	 * mundo, y pedirle al que mira que sepa las coordenadas es pedirle de mas.
	 *
	 * Las tres cosas que lo frenan, y las tres son la misma: un teletransporte
	 * gratis no puede ser una forma de zafar de algo.
	 *
	 *  - **estar en combate** no te deja ir. Sin esto, `/pvp ver` seria el mejor
	 *    `/home` del servidor: cada vez que te acorralan, un click y aparecias
	 *    entero del otro lado del mapa. Es la misma marca del datapack que ya
	 *    tapa `/home`, `/spawn`, `/rtp` y `/tpa`.
	 *  - **el que esta peleando** tampoco: ya esta adentro de la arena, y salir
	 *    de ahi seria abandonar la pelea sin perderla.
	 *  - **tiene que haber un duelo ahora**. Si no, no hay nada que mirar y esto
	 *    seria un viaje gratis a un punto fijo del mapa a cualquier hora.
	 */
	/**
	 * Abre la mesa en la que estas sentado.
	 *
	 * Es el comando al que llega el invitado desde el boton del chat, asi que no
	 * puede pedir nada mas: el que lo aprieta no sabe ni que existe una mesa.
	 */
	private int abrirMesa(CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		Mesa mesa = duelos.mesas().dondeEsta(quien.getScoreboardName());
		if (mesa == null) {
			return no(quien, Carteles.mal("No estás armando ninguna pelea. "
					+ "Armá una desde el menú del coliseo."));
		}
		PantallaMesa.abrir(quien, duelos, duelos.mesas(), contexto.getSource().getServer(), mesa);
		return 1;
	}

	/**
	 * Arma una mesa nueva y la abre.
	 *
	 * No se puede si ya estas peleando o anotado: dos compromisos a la vez
	 * terminan siempre en alguien plantado esperando en la arena.
	 */
	private int armarMesa(CommandContext<CommandSourceStack> contexto, String modo)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		String nombre = quien.getScoreboardName();

		if (duelos.estaOcupado(nombre)) {
			return no(quien, Carteles.mal("Ya estás en una pelea o esperando turno."));
		}
		if (duelos.espera().estaAnotado(nombre)) {
			return no(quien, Carteles.mal("Estás anotado en la lista de espera. "
					+ "Bajate primero con /pvp salir."));
		}
		if (quien.entityTags().contains(Puntos.COMBATE)) {
			return no(quien, Carteles.enPelea());
		}
		// Una mesa por persona y una por minuto: sin esto, armar y deshacer es
		// gratis y se le pueden mandar treinta invitaciones seguidas al mismo.
		String problema = duelos.mesas().porQueNoPuedeArmar(nombre);
		if (problema != null) {
			return no(quien, Carteles.mal(problema));
		}

		int porLado = "2v2".equalsIgnoreCase(modo) ? 2 : 1;
		Mesa mesa = duelos.mesas().armar(nombre, porLado);
		PantallaMesa.abrir(quien, duelos, duelos.mesas(), contexto.getSource().getServer(), mesa);
		return 1;
	}

	private int ver(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		MinecraftServer servidor = contexto.getSource().getServer();

		Duelo duelo = duelos.actual();
		if (duelo == null) return no(quien, Carteles.noHayDuelo());
		if (duelo.esDuelista(quien.getScoreboardName())) {
			return no(quien, Carteles.mal("Estás peleando. Mirala desde adentro."));
		}
		if (quien.entityTags().contains("sdp_combate")) return no(quien, Carteles.enPelea());

		Arena arena = duelo.arena();
		ServerLevel nivel = arena.nivel(servidor);
		if (nivel == null) return no(quien, Carteles.sinArena());

		if (duelos.mirones().estaMirando(quien)) {
			return no(quien, Carteles.mal("Ya la estás mirando. Para volver: /pvp volver"));
		}
		// De espectador y no a pie: asi no puede pegar, ni que le peguen, ni tocar un
		// bloque, y mira desde donde quiera. Si no se pudo anotar a donde vuelve, no
		// se lo mueve: quedar de espectador sin vuelta es peor que no ver la pelea.
		if (!duelos.mirones().aMirar(quien, arena, nivel)) {
			return no(quien, Carteles.mal("No pude guardar desde dónde venís. Avisale a Pepe."));
		}
		quien.sendSystemMessage(Carteles.teLlevamosALasGradas(duelo.uno, duelo.otro));
		quien.sendSystemMessage(Carteles.aviso("Estás mirando de espectador. Para volver: /pvp volver"));
		return 1;
	}

	/** Deja de mirar y vuelve a donde estaba, con lo suyo. */
	private int dejarDeMirar(CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		if (!duelos.mirones().estaMirando(quien)) {
			return no(quien, Carteles.mal("No estás mirando ninguna pelea."));
		}
		duelos.mirones().devolver(quien);
		quien.sendSystemMessage(Carteles.aviso("Volviste a donde estabas."));
		return 1;
	}

	/**
	 * `/pvp cola [1v1|2v2|equipos]`: se anota en la lista de espera.
	 *
	 * No hace falta rival: cuando haya con quien cruzarlo, la pelea arranca sola.
	 */
	private int anotarse(CommandContext<CommandSourceStack> contexto, String modoTexto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		MinecraftServer servidor = contexto.getSource().getServer();
		String nombre = quien.getScoreboardName();

		Cola.Modo modo = Cola.Modo.porNombre(modoTexto);
		if (modo == null) {
			return no(quien, Carteles.mal("Ese modo no existe. Son 1v1, 2v2 y equipos."));
		}
		if (!duelos.hayArena()) return no(quien, Carteles.sinArena());
		if (duelos.estaOcupado(nombre)) {
			return no(quien, Carteles.mal("Ya tenés una pelea armada."));
		}
		if (quien.entityTags().contains("sdp_combate")) return no(quien, Carteles.enPelea());
		if (duelos.mirones().estaMirando(quien)) {
			return no(quien, Carteles.mal("Estás mirando una pelea. Volvé primero: /pvp volver"));
		}
		if (modo == Cola.Modo.EQUIPOS && Cola.equipoDe(servidor, nombre) == null) {
			return no(quien, Carteles.mal("Para el modo equipos hay que tener equipo. "
					+ "Armá uno con /equipo crear."));
		}

		int cuantos = duelos.espera().anotar(nombre, modo);
		int faltan = Math.max(0, modo.cupos - cuantos);
		quien.sendSystemMessage(Carteles.aviso("Anotado en " + modo.comoSeLlama + ". "
				+ (faltan > 0 ? "Falta" + (faltan == 1 ? " " : "n ") + faltan
						+ (modo == Cola.Modo.EQUIPOS ? " equipo(s)." : " más.")
				: "Arranca en cuanto se libere la arena.")));
		for (ServerPlayer otro : duelos.espera().conectadosDe(servidor, modo)) {
			if (otro != quien) {
				otro.sendSystemMessage(Carteles.aviso(nombre + " se anotó a " + modo.comoSeLlama
						+ ": son " + cuantos + " esperando."));
			}
		}
		return 1;
	}

	/** `/pvp salir`: se baja de la lista de espera. */
	private int salirDeLaCola(CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		if (!duelos.espera().sacar(quien.getScoreboardName())) {
			return no(quien, Carteles.mal("No estabas anotado en ninguna lista."));
		}
		quien.sendSystemMessage(Carteles.aviso("Te bajaste de la lista de espera."));
		return 1;
	}


	private List<String> losQuePelean() {
		Duelo duelo = duelos.actual();
		if (duelo == null) return List.of();
		return List.of(duelo.uno, duelo.otro);
	}

	// ------------------------------------------------------------------ lo que se ve

	private int top(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		MinecraftServer servidor = contexto.getSource().getServer();
		String yo = quien.getScoreboardName();
		quien.sendSystemMessage(Carteles.top(Puntos.mejores(servidor, 10), yo,
				Puntos.ganados(servidor, yo), Puntos.perdidos(servidor, yo)));
		return 1;
	}

	private int comoEs(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		quien.sendSystemMessage(Carteles.comoEs(duelos.hayArena(), Kits.CUANTAS));
		return 1;
	}

	// -------------------------------------------------------------------- la prueba

	/**
	 * `/pvp probar`: guarda tu inventario como lo guardaria un duelo, lo escribe,
	 * lo vuelve a leer y compara. No te toca nada.
	 *
	 * Existe porque lo unico de todo esto que no se arregla con un perdon es que
	 * alguien pierda lo que llevaba encima. Correlo parado con lo mejor que tengas
	 * —elytra, shulkers llenas, netherita encantada— y con eso queda probado contra
	 * items de verdad y no contra un ejemplo.
	 */
	private int probar(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		List<String> problemas = Guardado.probarLaIdaYVuelta(quien, contexto.getSource().getServer());
		quien.sendSystemMessage(Carteles.resultadoDeLaPrueba(
				Guardado.cuantosLlenos(quien), Guardado.cuantosCasilleros(quien), problemas));
		return problemas.isEmpty() ? 1 : 0;
	}

	// --------------------------------------------------------------------- la arena

	private int verArena(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		Arena arena = registro.arena();
		quien.sendSystemMessage(arena == null
				? Carteles.sinArenaTodavia()
				: Carteles.laArena(arena, duelos.actual() != null,
						Zonas.lasQuePisan(contexto.getSource().getServer(), arena)));
		return 1;
	}

	/**
	 * `/pvp arena guardar [alto]`, por los dos caminos.
	 *
	 * Primero mira si marcaste dos esquinas con la varita (click izquierdo). Si no,
	 * agarra **la zona protegida en la que estas parado**, que es el camino corto:
	 * marcar una zona con el click derecho ya se sabe hacer y lo hace todo el
	 * mundo, y pedirle a alguien que se acuerde de cual de los dos botones era para
	 * hacer lo mismo es pedir de mas.
	 *
	 * De una zona sale el rectangulo tal cual, pero el alto lo pone este comando:
	 * la proteccion de Safe Zone no mira la altura, asi que casi todas las zonas
	 * estan marcadas con las dos esquinas a la misma altura y de ahi no se puede
	 * sacar el alto de un coliseo.
	 */
	private int guardarArena(CommandContext<CommandSourceStack> contexto, int alto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		MinecraftServer servidor = contexto.getSource().getServer();

		BlockPos[] esquinas = varita.seleccionDe(quien);
		if (esquinas != null) return dejarArena(quien, esquinas, "las dos esquinas que marcaste");

		Zonas.Zona zona = Zonas.dondeEsta(servidor, quien);
		if (zona == null) return no(quien, Carteles.nadaQueGuardar());
		return dejarArena(quien, zona.esquinas(alto), "la zona " + zona.id());
	}

	private int guardarDeZona(CommandContext<CommandSourceStack> contexto, int alto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		String pedida = StringArgumentType.getString(contexto, "zona");

		Zonas.Zona zona = Zonas.porId(contexto.getSource().getServer(), pedida);
		if (zona == null) return no(quien, Carteles.zonaQueNoExiste(pedida));
		return dejarArena(quien, zona.esquinas(alto), "la zona " + zona.id());
	}

	private int dejarArena(ServerPlayer quien, BlockPos[] esquinas, String deDonde) {
		Arena arena = Arena.deLasEsquinas((ServerLevel) quien.level(), esquinas[0], esquinas[1]);
		if (arena.volumen() > Arena.TOPE_BLOQUES) {
			return no(quien, Carteles.cajaMuyGrande(arena.volumen()));
		}

		registro.ponerArena(arena);
		varita.olvidar(quien);
		quien.sendSystemMessage(Carteles.arenaGuardada(arena, deDonde,
				Zonas.lasQuePisan(quien.level().getServer(), arena)));
		quien.sendSystemMessage(Carteles.laArena(arena, duelos.actual() != null,
				Zonas.lasQuePisan(quien.level().getServer(), arena)));
		return 1;
	}

	private int ponerPunto(CommandContext<CommandSourceStack> contexto, int cual)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		Arena arena = registro.arena();
		if (arena == null) return no(quien, Carteles.sinArenaTodavia());
		if (!arena.contiene(quien.position()) || !arena.esDe(quien.level())) {
			return no(quien, Carteles.mal("Parate adentro de la arena y volve a escribirlo."));
		}

		registro.ponerArena(arena.conPunto(cual, quien.position()));
		quien.sendSystemMessage(Carteles.puntoGuardado(cual));
		return 1;
	}

	private int borrarArena(CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		if (duelos.actual() != null) {
			return no(quien, Carteles.mal("Ahora mismo se esta peleando ahi. Esperá a que termine."));
		}
		registro.borrarArena();
		quien.sendSystemMessage(Carteles.arenaBorrada());
		return 1;
	}

	// --------------------------------------------------------------------- el torneo

	private int verTorneo(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		quien.sendSystemMessage(Carteles.torneoEstado(torneo.abierto(), torneo.jugando(),
				torneo.entrada(), torneo.lista(), torneo.pozo(), quien.getScoreboardName()));
		return 1;
	}

	private int torneoAbrir(CommandContext<CommandSourceStack> contexto, int entrada)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		if (!duelos.hayArena()) return no(quien, Carteles.sinArena());
		return contestar(quien, torneo.abrir(entrada));
	}

	private int torneoEntrar(CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		return contestar(quien, torneo.entrar(quien));
	}

	private int torneoSalir(CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		return contestar(quien, torneo.salir(quien));
	}

	private int torneoArrancar(CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		return contestar(quien, torneo.arrancar());
	}

	private int torneoCancelar(CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		return contestar(quien, torneo.cancelar());
	}

	// ----------------------------------------------------------------- lo compartido

	/**
	 * Todo lo que tiene que dar bien para que dos puedan pelear. Devuelve el cartel
	 * del problema, o null si se puede.
	 *
	 * Se llama dos veces: al retar y al aceptar. La segunda no es de mas — entre
	 * las dos pasa hasta un minuto, y en ese minuto cualquiera de los dos pudo
	 * meterse en otra pelea o quedarse sin los shards de la apuesta.
	 */
	private Component sePuedePelear(MinecraftServer servidor, ServerPlayer retador,
			ServerPlayer retado, int apuesta, String quienPregunta) {
		if (!duelos.hayArena()) return Carteles.sinArena();

		for (ServerPlayer cual : List.of(retador, retado)) {
			String nombre = cual.getScoreboardName();
			boolean soyYo = nombre.equalsIgnoreCase(quienPregunta);

			if (duelos.estaOcupado(nombre)) {
				return soyYo ? Carteles.yaEstasPeleando() : Carteles.yaEstaPeleando(nombre);
			}
			// La marca de pelea: entrar a la arena en el medio de una pelea de verdad
			// seria la forma perfecta de escaparse, porque adentro nadie de afuera te
			// toca y al salir estas curado y del otro lado del mundo.
			if (cual.entityTags().contains("sdp_combate")) {
				return soyYo ? Carteles.enPelea() : Carteles.elOtroEnPelea(nombre);
			}
			if (apuesta > 0) {
				int tiene = Puntos.shards(servidor, nombre);
				if (tiene < apuesta) {
					return soyYo ? Carteles.sinShards(apuesta, tiene)
							: Carteles.elOtroSinShards(nombre, apuesta);
				}
			}
		}
		return null;
	}

	private static int contestar(ServerPlayer quien, Component problema) {
		if (problema == null) return 1;
		quien.sendSystemMessage(problema);
		return 0;
	}

	private static int no(ServerPlayer quien, Component porQue) {
		quien.sendSystemMessage(porQue);
		return 0;
	}

	/** El que administra. En 26.1 se le pregunta a la lista, no a un numero. */
	private static boolean esOperador(CommandSourceStack fuente) {
		ServerPlayer quien = fuente.getPlayer();
		if (quien == null) return true;
		return fuente.getServer().getPlayerList().isOp(quien.nameAndId());
	}

}
