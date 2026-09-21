package pe.sobrinosdepepe.equipos;

import net.minecraft.ChatFormatting;

import java.util.List;
import java.util.Locale;

/**
 * Los colores que puede tener un equipo, con el nombre que usa el jugador.
 *
 * Son doce de los dieciseis de Minecraft. Quedan afuera cuatro y por el mismo
 * motivo: no se distinguen. El negro y el gris oscuro no se leen sobre el fondo
 * del chat, el gris se confunde con el texto apagado de todos nuestros
 * mensajes, y el blanco es el color del que NO tiene equipo.
 *
 * El orden importa dos veces: es el orden en el que se dibuja la paleta de
 * `/equipo color`, y es el orden en el que se reparte el color al que arma un
 * equipo sin elegir ninguno. Por eso adelante van los ocho vivos, que son los
 * que de verdad se distinguen entre si en una pelea, y atras los cuatro
 * oscuros.
 *
 * El nombre en castellano es solo para hablar con el jugador. Adentro del
 * archivo y del scoreboard se guarda el nombre de Minecraft (`light_purple`),
 * que es el que entiende `team modify <equipo> color <color>` si alguna vez hay
 * que tocarlo a mano.
 */
public final class Colores {
	private Colores() {}

	public record Color(String nombre, ChatFormatting formato) {}

	public static final List<Color> TODOS = List.of(
			new Color("rojo", ChatFormatting.RED),
			new Color("naranja", ChatFormatting.GOLD),
			new Color("amarillo", ChatFormatting.YELLOW),
			new Color("verde", ChatFormatting.GREEN),
			new Color("celeste", ChatFormatting.AQUA),
			new Color("azul", ChatFormatting.BLUE),
			new Color("rosa", ChatFormatting.LIGHT_PURPLE),
			new Color("violeta", ChatFormatting.DARK_PURPLE),
			new Color("bordo", ChatFormatting.DARK_RED),
			new Color("verde_oscuro", ChatFormatting.DARK_GREEN),
			new Color("turquesa", ChatFormatting.DARK_AQUA),
			new Color("azul_oscuro", ChatFormatting.DARK_BLUE));

	/** El color que se llama asi, o null si ese nombre no existe. */
	public static ChatFormatting porNombre(String nombre) {
		String buscado = nombre.toLowerCase(Locale.ROOT);
		return TODOS.stream()
				.filter(c -> c.nombre().equals(buscado))
				.map(Color::formato)
				.findFirst()
				.orElse(null);
	}

	/**
	 * Como se llama un color para el jugador. Cae al nombre de Minecraft si
	 * alguien escribio a mano en el archivo un color que no esta en la lista:
	 * ahi el equipo se muestra igual, y no revienta nada.
	 */
	public static String comoSeLlama(ChatFormatting formato) {
		return TODOS.stream()
				.filter(c -> c.formato() == formato)
				.map(Color::nombre)
				.findFirst()
				.orElse(formato.getName());
	}

	public static List<String> nombres() {
		return TODOS.stream().map(Color::nombre).toList();
	}
}
