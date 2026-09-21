package pe.sobrinosdepepe.equipos;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.function.Predicate;

/**
 * Todo lo que el jugador lee de los equipos.
 *
 * Los colores y los simbolos son los mismos de servidor/estilo.py, porque el
 * chat, el cartel de la derecha y los menus de cofre tienen que verse como una
 * sola cosa. Los simbolos van con \\u y del plano 0 de Unicode: los del plano 1
 * salen como un cuadrado vacio en la fuente de fabrica.
 */
public final class Carteles {
	private Carteles() {}

	private static final int MARCA = 0xffb02e;
	private static final int ACENTO = 0x00a6ff;
	private static final int BIEN = 0x55ff7f;
	private static final int ERROR = 0xff5555;

	private static final String VINETA = "▪";
	private static final String RAYA = "▬";
	private static final String FLECHA = "»";

	// --------------------------------------------------------------------- el tag

	/**
	 * El `[NOMBRE] ` que va adelante del nombre del jugador. Es el prefijo del
	 * equipo del scoreboard, y con eso solo aparece en el chat cuando escribe, en
	 * el tab y arriba de la cabeza, sin tocar ninguna de las tres cosas.
	 *
	 * Los corchetes van grises y el nombre del equipo con SU color: el juego le
	 * tiñe el color del equipo al conjunto pero respeta el estilo propio de cada
	 * hijo, asi que los corchetes se quedan grises y no le compiten al nombre. Lo
	 * que queda del color del equipo es el nombre del equipo y el del jugador, que
	 * es lo que se tiene que leer de un vistazo.
	 */
	public static Component tag(Registro.Equipo equipo) {
		return Component.empty()
				.append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(equipo.nombre).withStyle(equipo.color))
				.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
	}

	// ------------------------------------------------------------------ el estado

	/** `/equipo` a secas cuando el jugador no tiene ninguno. */
	public static Component sinEquipo() {
		return marco(Component.empty()
				.append(gris("  Todavía no estás en ningún equipo.\n\n"))
				.append(comando("/equipo crear ", "el tuyo, y vos sos el jefe"))
				.append(gris("\n  O esperá a que alguien te invite.\n\n"))
				.append(gris("  En un equipo no se pegan entre ustedes y se ven\n"))
				.append(gris("  en la barra de arriba. Al resto no lo ve nadie.\n")));
	}

	/** `/equipo` a secas cuando si tiene: quienes son y que puede hacer. */
	public static Component elEquipo(MinecraftServer servidor, Registro.Equipo equipo, String quienPregunta) {
		MutableComponent cartel = Component.empty()
				.append(Component.literal("  "))
				.append(tag(equipo))
				.append(Component.literal(equipo.nombre.toUpperCase())
						.withStyle(estilo -> estilo.withColor(equipo.color).withBold(true)))
				.append(gris("   " + equipo.miembros.size() + "/" + Registro.TOPE
				+ "   " + Colores.comoSeLlama(equipo.color) + "\n\n"));

		for (String miembro : equipo.miembros) {
			boolean conectado = servidor.getPlayerList().getPlayerByName(miembro) != null;
			cartel.append(Component.literal("  " + VINETA + " ")
							.withStyle(estilo -> estilo.withColor(equipo.color)))
					.append(Component.literal(miembro)
							.withStyle(estilo -> estilo.withColor(conectado ? BIEN : 0x808080)))
					.append(gris(equipo.manda(miembro) ? "  (jefe)" : ""))
					.append(gris(conectado ? "" : "  desconectado"))
					.append(Component.literal("\n"));
		}

		cartel.append(Component.literal("\n"));
		if (equipo.manda(quienPregunta)) {
			cartel.append(comando("/equipo invitar ", "sumar a alguien que esté conectado"))
					.append(comando("/equipo echar ", "sacar a alguien del equipo"))
					.append(comando("/equipo color", "elegir el color del equipo"));
		}
		cartel.append(comando("/equipo salir", equipo.miembros.size() == 1
				? "irte, y el equipo se borra porque quedás solo"
				: "irte del equipo"));
		return marco(cartel);
	}

	// ------------------------------------------------------------- las invitaciones

	/** Lo que ve el invitado. El boton es todo: nadie escribe el nombre a mano. */
	public static Component teInvitaron(String quienInvita, Registro.Equipo equipo, int minutos) {
		return Component.empty()
				.append(dorado("\n  " + quienInvita))
				.append(gris(" te invitó a "))
				.append(Component.literal(equipo.nombre)
						.withStyle(estilo -> estilo.withColor(equipo.color).withBold(true)))
				.append(gris("\n  "))
				.append(boton("[ ENTRAR ]", "/equipo aceptar " + equipo.nombre,
						"Entrar a " + equipo.nombre))
				.append(gris("  o escribí /equipo aceptar " + equipo.nombre))
				.append(gris("\n  Te queda " + minutos + " minuto" + (minutos == 1 ? "" : "s") + ".\n"));
	}

	public static Component invitaste(String invitado, int minutos) {
		return bien("Lo invitaste a " + invitado + ". Tiene " + minutos
				+ " minuto" + (minutos == 1 ? "" : "s") + " para aceptar.");
	}

	// ------------------------------------------------------------------ los colores

	/**
	 * Los doce colores, cada uno escrito del suyo y clickeable.
	 *
	 * Es la forma de elegir que no obliga a acordarse de ningun nombre: se ven
	 * todos, se ve cual es el propio y se ve cual no se puede porque ya lo usa
	 * otro equipo. Los que no se pueden van en gris y sin click.
	 *
	 * @param libre si ese color no lo esta usando otro equipo
	 * @param puede si el que mira lo puede cambiar, o sea si es el jefe
	 */
	public static Component paleta(Registro.Equipo equipo, Predicate<String> libre, boolean puede) {
		MutableComponent cartel = Component.empty()
				.append(apagado("\n  " + RAYA.repeat(22) + "\n"))
				.append(dorado("  EL COLOR DE " + equipo.nombre.toUpperCase() + "\n\n"))
				.append(Component.literal("  " + VINETA + " "))
				.append(Component.literal("ahora es " + Colores.comoSeLlama(equipo.color))
						.withStyle(estilo -> estilo.withColor(equipo.color).withBold(true)))
				.append(Component.literal("\n\n"));

		int enLaFila = 0;
		for (Colores.Color color : Colores.TODOS) {
			if (enLaFila == 0) cartel.append(Component.literal("  "));

			boolean esElSuyo = color.formato() == equipo.color;
			boolean loPuedeElegir = puede && !esElSuyo && libre.test(color.nombre());
			MutableComponent palabra = Component.literal(color.nombre() + "  ");

			if (esElSuyo) {
				palabra.withStyle(estilo -> estilo.withColor(color.formato()).withBold(true)
						.withHoverEvent(new HoverEvent.ShowText(gris("El que tenés puesto"))));
			} else if (loPuedeElegir) {
				palabra.withStyle(estilo -> estilo.withColor(color.formato())
						.withClickEvent(new ClickEvent.RunCommand("/equipo color " + color.nombre()))
						.withHoverEvent(new HoverEvent.ShowText(gris("Clickea para dejarlo " + color.nombre()))));
			} else {
				palabra.withStyle(estilo -> estilo.withColor(0x555555)
						.withHoverEvent(new HoverEvent.ShowText(gris(puede
								? "Ese color ya lo usa otro equipo"
								: "El color lo elige el jefe"))));
			}

			cartel.append(palabra);
			if (++enLaFila == 4) {
				cartel.append(Component.literal("\n"));
				enLaFila = 0;
			}
		}

		cartel.append(gris(puede
				? "\n  Clickeá el que quieras y el equipo entero cambia.\n"
				: "\n  El color lo elige el jefe del equipo.\n"));
		return cartel.append(apagado("  " + RAYA.repeat(22) + "\n"));
	}

	public static Component quedoDe(Registro.Equipo equipo) {
		return Component.empty()
				.append(Component.literal("  El equipo quedó de color ").withStyle(estilo -> estilo.withColor(BIEN)))
				.append(Component.literal(Colores.comoSeLlama(equipo.color))
						.withStyle(estilo -> estilo.withColor(equipo.color).withBold(true)));
	}

	public static Component colorOcupado(String color, Registro.Equipo otro) {
		return mal("El color " + color + " ya lo usa " + otro.nombre + ". Elegí otro.");
	}

	public static Component colorRaro(String color) {
		return mal("No hay ningún color que se llame " + color
				+ ". Escribí /equipo color a secas para verlos todos.");
	}

	// ------------------------------------------------------------ los avisos cortos

	public static Component creaste(Registro.Equipo equipo) {
		return Component.empty()
				.append(dorado("\n  Armaste el equipo "))
				.append(Component.literal(equipo.nombre)
						.withStyle(estilo -> estilo.withColor(equipo.color).withBold(true)))
				.append(gris("\n  Sumá gente con "))
				.append(boton("/equipo invitar", "/equipo invitar ", "Completá con el nombre"))
				.append(gris("\n"));
	}

	public static Component entro(String jugador) {
		return bien(jugador + " entró al equipo.");
	}

	public static Component salio(String jugador) {
		return aviso(jugador + " se fue del equipo.");
	}

	public static Component echado(String jugador) {
		return aviso("Echaron a " + jugador + " del equipo.");
	}

	public static Component teEcharon(Registro.Equipo equipo) {
		return mal("Te echaron de " + equipo.nombre + ".");
	}

	public static Component teFuiste(String equipo, boolean seBorro) {
		return aviso(seBorro
				? "Te fuiste, y como quedabas solo el equipo " + equipo + " se borró."
				: "Te fuiste de " + equipo + ".");
	}

	public static Component mandaOtro(String jefe) {
		return aviso("Ahora manda " + jefe + ".");
	}

	// -------------------------------------------------------------- lo que no se puede

	public static Component yaTenesEquipo(Registro.Equipo equipo) {
		return mal("Ya estás en " + equipo.nombre + ". Salí con /equipo salir antes de armar otro.");
	}

	public static Component noTenesEquipo() {
		return mal("No estás en ningún equipo. Armá el tuyo con /equipo crear <nombre>.");
	}

	public static Component nombreUsado(String nombre) {
		return mal("Ya hay un equipo que se llama " + nombre + ". Elegí otro nombre.");
	}

	public static Component nombreFeo() {
		return mal("El nombre va de " + Registro.LARGO_MINIMO + " a " + Registro.LARGO_MAXIMO
				+ " letras o números, sin espacios ni símbolos.");
	}

	public static Component noSosElJefe(String jefe) {
		return mal("Eso lo hace el jefe del equipo, y el jefe es " + jefe + ".");
	}

	public static Component equipoLleno() {
		return mal("El equipo está lleno: son " + Registro.TOPE + " y no entra nadie más.");
	}

	public static Component elOtroYaTieneEquipo(String jugador) {
		return mal(jugador + " ya está en un equipo.");
	}

	public static Component esVosMismo() {
		return mal("A vos mismo no.");
	}

	public static Component noEstaEnTuEquipo(String jugador) {
		return mal(jugador + " no está en tu equipo.");
	}

	public static Component sinInvitacion(String nombre) {
		return mal("No tenés ninguna invitación de " + nombre
				+ ". Las invitaciones duran unos minutos y después vencen.");
	}

	public static Component sinInvitaciones() {
		return mal("No tenés ninguna invitación esperando.");
	}

	public static Component yaLoInvitaste(String jugador) {
		return mal("A " + jugador + " ya lo invitaste y todavía no contestó.");
	}

	// ----------------------------------------------------------------- las herramientas

	/**
	 * El marco de rayas del estado. Es el mismo de /comandos y de los menus, y es
	 * lo que hace que un mensaje largo en el chat se lea como una ficha y no como
	 * cuatro lineas sueltas entre lo que estan hablando los demas.
	 */
	private static MutableComponent marco(Component adentro) {
		return Component.empty()
				.append(apagado("\n  " + RAYA.repeat(22) + "\n"))
				.append(dorado("  TU EQUIPO\n\n"))
				.append(adentro)
				.append(apagado("  " + RAYA.repeat(22) + "\n"));
	}

	/** Una linea de "/comando  » para que sirve", clickeable. */
	private static MutableComponent comando(String comando, String paraQue) {
		return Component.empty()
				.append(Component.literal("  "))
				.append(boton(comando.trim(), comando, comando.endsWith(" ")
						? "Completá con el nombre" : "Clickea para usarlo"))
				.append(gris("  " + FLECHA + " " + paraQue + "\n"));
	}

	private static MutableComponent boton(String texto, String comando, String globito) {
		// Los que piden algo mas van con suggest_command, que lo deja escrito en el
		// chat para completar; los que se ejecutan solos, con run_command.
		ClickEvent click = comando.endsWith(" ")
				? new ClickEvent.SuggestCommand(comando)
				: new ClickEvent.RunCommand(comando);
		return Component.literal(texto).withStyle(estilo -> estilo
				.withColor(ACENTO)
				.withClickEvent(click)
				.withHoverEvent(new HoverEvent.ShowText(gris(globito))));
	}

	private static MutableComponent bien(String texto) {
		return Component.literal("  " + texto).withStyle(estilo -> estilo.withColor(BIEN));
	}

	private static MutableComponent mal(String texto) {
		return Component.literal("  " + texto).withStyle(estilo -> estilo.withColor(ERROR));
	}

	private static MutableComponent aviso(String texto) {
		return Component.literal("  " + texto).withStyle(estilo -> estilo.withColor(MARCA));
	}

	private static MutableComponent dorado(String texto) {
		return Component.literal(texto).withStyle(estilo -> estilo.withColor(MARCA).withBold(true));
	}

	private static MutableComponent gris(String texto) {
		return Component.literal(texto).withStyle(ChatFormatting.GRAY);
	}

	private static MutableComponent apagado(String texto) {
		return Component.literal(texto).withStyle(ChatFormatting.DARK_GRAY);
	}

	/** Los nombres de los equipos a los que a este jugador lo invitaron. */
	public static Component invitacionesPendientes(List<String> equipos) {
		return aviso("Te invitaron a: " + String.join(", ", equipos)
				+ ". Entrá con /equipo aceptar <equipo>.");
	}
}
