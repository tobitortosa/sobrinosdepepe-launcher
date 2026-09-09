package pe.sobrinosdepepe.equipos;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * `/equipo`, el unico comando de todo esto.
 *
 *     /equipo                      quienes son y que podes hacer
 *     /equipo crear <nombre>       armar uno, y sos el jefe
 *     /equipo invitar <jugador>    solo el jefe, y el otro tiene que estar conectado
 *     /equipo aceptar <equipo>     entrar, mientras la invitacion no venza
 *     /equipo echar <jugador>      solo el jefe
 *     /equipo salir                irte
 *
 * No lleva `requires`, o sea que lo puede usar cualquiera sin ser operador: es
 * la unica forma de que sirva. Los que si tienen que ser del jefe se controlan
 * adentro, uno por uno, comparando contra el jefe del equipo.
 *
 * Todo lo que contesta va por `sendSystemMessage` al jugador y no por
 * `sendSuccess`: con sendSuccess el mensaje se le copia a los operadores
 * conectados (`broadcastToAdmins`), y las invitaciones de los demas no son
 * asunto de nadie.
 */
public final class ComandoEquipo {
	private final Registro registro;
	private final Invitaciones invitaciones;

	public ComandoEquipo(Registro registro, Invitaciones invitaciones) {
		this.registro = registro;
		this.invitaciones = invitaciones;
	}

	public void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("equipo")
				.executes(this::estado)
				.then(Commands.literal("crear")
						.then(Commands.argument("nombre", StringArgumentType.word())
								.executes(this::crear)))
				.then(Commands.literal("invitar")
						.then(Commands.argument("jugador", EntityArgument.player())
								.executes(this::invitar)))
				.then(Commands.literal("aceptar")
						.then(Commands.argument("equipo", StringArgumentType.word())
								.suggests((contexto, sugerencias) -> SharedSuggestionProvider.suggest(
										invitaciones.de(contexto.getSource().getTextName()), sugerencias))
								.executes(this::aceptar)))
				.then(Commands.literal("echar")
						.then(Commands.argument("jugador", StringArgumentType.word())
								.suggests((contexto, sugerencias) -> SharedSuggestionProvider.suggest(
										companeros(contexto.getSource().getTextName()), sugerencias))
								.executes(this::echar)))
				.then(Commands.literal("salir")
						.executes(this::salir)));
	}

	// ------------------------------------------------------------------- /equipo

	private int estado(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer jugador = contexto.getSource().getPlayerOrException();
		String nombre = jugador.getScoreboardName();
		Registro.Equipo equipo = registro.de(nombre);

		if (equipo == null) {
			jugador.sendSystemMessage(Carteles.sinEquipo());
			List<String> invitado = invitaciones.de(nombre);
			if (!invitado.isEmpty()) {
				jugador.sendSystemMessage(Carteles.invitacionesPendientes(invitado));
			}
			return 0;
		}

		jugador.sendSystemMessage(Carteles.elEquipo(contexto.getSource().getServer(), equipo, nombre));
		return 1;
	}

	// ------------------------------------------------------------- /equipo crear

	private int crear(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer jugador = contexto.getSource().getPlayerOrException();
		String nombre = jugador.getScoreboardName();
		String pedido = StringArgumentType.getString(contexto, "nombre");

		Registro.Equipo tiene = registro.de(nombre);
		if (tiene != null) {
			jugador.sendSystemMessage(Carteles.yaTenesEquipo(tiene));
			return 0;
		}
		if (!nombreValido(pedido)) {
			jugador.sendSystemMessage(Carteles.nombreFeo());
			return 0;
		}
		if (registro.porNombre(pedido) != null) {
			jugador.sendSystemMessage(Carteles.nombreUsado(pedido));
			return 0;
		}

		Registro.Equipo equipo = registro.crear(pedido, nombre);
		jugador.sendSystemMessage(Carteles.creaste(equipo));
		return 1;
	}

	/**
	 * Letras, numeros y guion bajo, del largo de un nombre de jugador. El nombre
	 * termina siendo un equipo del scoreboard y un color sobre la cabeza de
	 * alguien, asi que ni vacio, ni con espacios, ni con codigos de color.
	 */
	private static boolean nombreValido(String nombre) {
		return nombre.length() >= Registro.LARGO_MINIMO
				&& nombre.length() <= Registro.LARGO_MAXIMO
				&& nombre.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_');
	}

	// ----------------------------------------------------------- /equipo invitar

	private int invitar(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer jugador = contexto.getSource().getPlayerOrException();
		String nombre = jugador.getScoreboardName();
		ServerPlayer invitado = EntityArgument.getPlayer(contexto, "jugador");
		String suNombre = invitado.getScoreboardName();

		Registro.Equipo equipo = registro.de(nombre);
		if (equipo == null) {
			jugador.sendSystemMessage(Carteles.noTenesEquipo());
			return 0;
		}
		if (!equipo.manda(nombre)) {
			jugador.sendSystemMessage(Carteles.noSosElJefe(equipo.jefe));
			return 0;
		}
		if (suNombre.equalsIgnoreCase(nombre)) {
			jugador.sendSystemMessage(Carteles.esVosMismo());
			return 0;
		}
		if (equipo.lleno()) {
			jugador.sendSystemMessage(Carteles.equipoLleno());
			return 0;
		}
		if (registro.de(suNombre) != null) {
			jugador.sendSystemMessage(Carteles.elOtroYaTieneEquipo(suNombre));
			return 0;
		}
		if (invitaciones.hay(suNombre, equipo.nombre)) {
			jugador.sendSystemMessage(Carteles.yaLoInvitaste(suNombre));
			return 0;
		}

		invitaciones.invitar(suNombre, equipo.nombre);
		invitado.sendSystemMessage(Carteles.teInvitaron(nombre, equipo, Invitaciones.MINUTOS));
		jugador.sendSystemMessage(Carteles.invitaste(suNombre, Invitaciones.MINUTOS));
		return 1;
	}

	// ----------------------------------------------------------- /equipo aceptar

	private int aceptar(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer jugador = contexto.getSource().getPlayerOrException();
		String nombre = jugador.getScoreboardName();
		String pedido = StringArgumentType.getString(contexto, "equipo");

		Registro.Equipo tiene = registro.de(nombre);
		if (tiene != null) {
			jugador.sendSystemMessage(Carteles.yaTenesEquipo(tiene));
			return 0;
		}
		if (!invitaciones.hay(nombre, pedido)) {
			List<String> otras = invitaciones.de(nombre);
			jugador.sendSystemMessage(otras.isEmpty()
					? Carteles.sinInvitaciones()
					: Carteles.sinInvitacion(pedido));
			return 0;
		}

		// El equipo pudo disolverse o llenarse entre la invitacion y el enter.
		Registro.Equipo equipo = registro.porNombre(pedido);
		if (equipo == null) {
			invitaciones.olvidar(nombre);
			jugador.sendSystemMessage(Carteles.sinInvitacion(pedido));
			return 0;
		}
		if (equipo.lleno()) {
			jugador.sendSystemMessage(Carteles.equipoLleno());
			return 0;
		}

		registro.sumar(equipo, nombre);
		invitaciones.olvidar(nombre);
		avisar(contexto.getSource().getServer(), equipo, Carteles.entro(nombre), nombre);
		jugador.sendSystemMessage(Carteles.elEquipo(contexto.getSource().getServer(), equipo, nombre));
		return 1;
	}

	// ------------------------------------------------------------- /equipo echar

	private int echar(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer jugador = contexto.getSource().getPlayerOrException();
		String nombre = jugador.getScoreboardName();
		String pedido = StringArgumentType.getString(contexto, "jugador");

		Registro.Equipo equipo = registro.de(nombre);
		if (equipo == null) {
			jugador.sendSystemMessage(Carteles.noTenesEquipo());
			return 0;
		}
		if (!equipo.manda(nombre)) {
			jugador.sendSystemMessage(Carteles.noSosElJefe(equipo.jefe));
			return 0;
		}
		if (pedido.equalsIgnoreCase(nombre)) {
			// Echarse a si mismo es irse, y para eso esta /equipo salir. Dicho de
			// otra manera: el jefe no puede dejar al equipo sin jefe por accidente.
			jugador.sendSystemMessage(Carteles.esVosMismo());
			return 0;
		}
		if (!equipo.tiene(pedido)) {
			jugador.sendSystemMessage(Carteles.noEstaEnTuEquipo(pedido));
			return 0;
		}

		// El nombre exacto y no el que tipeo el jefe: el que se guarda y el que se
		// muestra es siempre el del jugador.
		String exacto = comoSeLlama(equipo, pedido);
		MinecraftServer servidor = contexto.getSource().getServer();
		registro.sacar(equipo, exacto);

		ServerPlayer echado = servidor.getPlayerList().getPlayerByName(exacto);
		if (echado != null) echado.sendSystemMessage(Carteles.teEcharon(equipo));
		avisar(servidor, equipo, Carteles.echado(exacto), null);
		return 1;
	}

	// ------------------------------------------------------------- /equipo salir

	private int salir(CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer jugador = contexto.getSource().getPlayerOrException();
		String nombre = jugador.getScoreboardName();
		Registro.Equipo equipo = registro.de(nombre);

		if (equipo == null) {
			jugador.sendSystemMessage(Carteles.noTenesEquipo());
			return 0;
		}

		boolean mandaba = equipo.manda(nombre);
		MinecraftServer servidor = contexto.getSource().getServer();
		boolean seBorro = registro.sacar(equipo, nombre);

		jugador.sendSystemMessage(Carteles.teFuiste(equipo.nombre, seBorro));
		if (!seBorro) {
			avisar(servidor, equipo, Carteles.salio(nombre), null);
			if (mandaba) avisar(servidor, equipo, Carteles.mandaOtro(equipo.jefe), null);
		}
		return 1;
	}

	// ------------------------------------------------------------------ auxiliares

	/** Le cuenta algo a los del equipo que estan conectados. */
	private static void avisar(MinecraftServer servidor, Registro.Equipo equipo,
			Component mensaje, String salvo) {
		for (String miembro : equipo.miembros) {
			if (salvo != null && miembro.equalsIgnoreCase(salvo)) continue;
			ServerPlayer companero = servidor.getPlayerList().getPlayerByName(miembro);
			if (companero != null) companero.sendSystemMessage(mensaje);
		}
	}

	private static String comoSeLlama(Registro.Equipo equipo, String parecido) {
		return equipo.miembros.stream()
				.filter(m -> m.equalsIgnoreCase(parecido))
				.findFirst()
				.orElse(parecido);
	}

	/** Para el autocompletado de /equipo echar: los del equipo, menos uno mismo. */
	private List<String> companeros(String jugador) {
		Registro.Equipo equipo = registro.de(jugador);
		if (equipo == null || !equipo.manda(jugador)) return List.of();
		return equipo.miembros.stream()
				.filter(m -> !m.equalsIgnoreCase(jugador))
				.toList();
	}
}
