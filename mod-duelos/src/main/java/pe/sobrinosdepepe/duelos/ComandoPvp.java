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
				.then(Commands.argument("jugador", EntityArgument.player())
						.executes(c -> retar(c, 0))
						.then(Commands.argument("shards", IntegerArgumentType.integer(0))
								.executes(c -> retar(c, IntegerArgumentType.getInteger(c, "shards")))))
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
				.then(Commands.literal("top").executes(this::top))
				.then(Commands.literal("duelos").executes(this::comoEs))
				.then(Commands.literal("probar")
						.requires(ComandoPvp::esOperador)
						.executes(this::probar))
				.then(Commands.literal("arena")
						.requires(ComandoPvp::esOperador)
						.executes(this::verArena)
						.then(Commands.literal("guardar").executes(this::guardarArena))
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

	private int retar(CommandContext<CommandSourceStack> contexto, int apuesta)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		ServerPlayer aQuien = EntityArgument.getPlayer(contexto, "jugador");
		MinecraftServer servidor = contexto.getSource().getServer();

		String yo = quien.getScoreboardName();
		String el = aQuien.getScoreboardName();

		if (el.equalsIgnoreCase(yo)) return no(quien, Carteles.aVosMismo());
		Component problema = sePuedePelear(servidor, quien, aQuien, apuesta, yo);
		if (problema != null) return no(quien, problema);
		if (duelos.invitaciones().buscar(el, yo) != null) {
			return no(quien, Carteles.yaLoRetaste(el));
		}

		duelos.invitaciones().retar(yo, el, apuesta);
		aQuien.sendSystemMessage(Carteles.teRetaron(yo, apuesta, Invitaciones.SEGUNDOS));
		aQuien.level().playSound(null, aQuien.getX(), aQuien.getY(), aQuien.getZ(),
				net.minecraft.sounds.SoundEvents.ANVIL_LAND,
				net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.6f);
		quien.sendSystemMessage(Carteles.retaste(el, apuesta, Invitaciones.SEGUNDOS));
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

		int lugar = duelos.encolar(reto.retador(), yo, reto.apuesta(), false);
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
				: Carteles.laArena(arena, duelos.actual() != null));
		return 1;
	}

	private int guardarArena(CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		BlockPos[] esquinas = varita.seleccionDe(quien);
		if (esquinas == null) return no(quien, Carteles.faltaLaOtraEsquina());

		Arena arena = Arena.deLasEsquinas((ServerLevel) quien.level(), esquinas[0], esquinas[1]);
		if (arena.volumen() > Arena.TOPE_BLOQUES) {
			return no(quien, Carteles.cajaMuyGrande(arena.volumen()));
		}

		registro.ponerArena(arena);
		varita.olvidar(quien);
		quien.sendSystemMessage(Carteles.arenaGuardada(arena));
		quien.sendSystemMessage(Carteles.laArena(arena, false));
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
