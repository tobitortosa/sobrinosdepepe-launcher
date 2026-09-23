package pe.sobrinosdepepe.duelos;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * La pantalla de elegir a alguien: las caras de los que estan conectados.
 *
 * Es la razon por la que este mod dibuja sus propios menus. Un menu del datapack
 * es un JSON fijo y no sabe quien esta adentro del servidor; esta lista cambia
 * cada vez que alguien entra o sale.
 *
 * Debajo de cada cara dice **si se puede elegir o no y por que**: que esta
 * peleando, que ya esta en la mesa, que esta en otra. Mostrar a todos y despues
 * decirle que no al que clickeo es peor que no mostrarlos, porque el que mira ya
 * se hizo la idea.
 *
 * Las caras salen de verdad aunque el servidor sea offline-mode porque esta
 * **skinrestorer** instalado. Sin el serian todas Steve.
 */
public final class ElegirJugador extends Pantalla {

	/** Cuantas caras entran por pagina: las cuatro filas de adentro del marco. */
	private static final int POR_PAGINA = 28;

	private final ServerPlayer mira;
	private final String titulo;
	private final List<ServerPlayer> gente;
	private final Function<ServerPlayer, String> porQueNo;
	private final Consumer<ServerPlayer> alElegir;
	private final Runnable alVolver;
	private int pagina;

	private ElegirJugador(int id, Inventory inventario, ServerPlayer mira, String titulo,
			List<ServerPlayer> gente, Function<ServerPlayer, String> porQueNo,
			Consumer<ServerPlayer> alElegir, Runnable alVolver) {
		super(id, inventario, 6);
		this.mira = mira;
		this.titulo = titulo;
		this.gente = gente;
		this.porQueNo = porQueNo;
		this.alElegir = alElegir;
		this.alVolver = alVolver;
	}

	/**
	 * Abre la lista.
	 *
	 * `porQueNo` devuelve el motivo por el que ese jugador no se puede elegir, o
	 * null si se puede. `alVolver` es el boton de atras, que siempre tiene que
	 * llevar a algun lado: una pantalla sin salida se cierra con Escape y el
	 * jugador pierde lo que estaba armando.
	 */
	public static void abrir(ServerPlayer quien, MinecraftServer servidor, String titulo,
			Function<ServerPlayer, String> porQueNo, Consumer<ServerPlayer> alElegir,
			Runnable alVolver) {
		List<ServerPlayer> gente = new ArrayList<>(servidor.getPlayerList().getPlayers());
		gente.removeIf(otro -> otro == quien);
		gente.sort((uno, otro) -> uno.getScoreboardName().compareToIgnoreCase(otro.getScoreboardName()));
		Pantalla.abrir(quien, titulo, (id, inventario) ->
				new ElegirJugador(id, inventario, quien, titulo, gente, porQueNo, alElegir, alVolver));
	}

	@Override
	protected void dibujar() {
		for (int columna = 1; columna <= 9; columna++) {
			adorno(1, columna, Caras.relleno());
			adorno(6, columna, Caras.relleno());
		}

		if (gente.isEmpty()) {
			boton(3, 5, Caras.boton(Items.BARRIER, "NO HAY NADIE MÁS", ChatFormatting.RED,
					"Sos el único conectado"), null);
		}

		int desde = pagina * POR_PAGINA;
		for (int i = 0; i < POR_PAGINA && desde + i < gente.size(); i++) {
			ServerPlayer otro = gente.get(desde + i);
			int fila = 2 + i / 7;
			int columna = 2 + i % 7;
			String motivo = porQueNo.apply(otro);
			if (motivo == null) {
				boton(fila, columna, Caras.cabeza(otro, ChatFormatting.GREEN, "Clickealo para elegirlo"),
						(quien, cual) -> alElegir.accept(otro));
			} else {
				adorno(fila, columna, Caras.cabeza(otro, ChatFormatting.DARK_GRAY, motivo));
			}
		}

		if (pagina > 0) {
			boton(6, 3, Caras.boton(Items.ARROW, "« ANTERIOR", ChatFormatting.GOLD),
					(quien, cual) -> { pagina--; redibujar(); });
		}
		if (desde + POR_PAGINA < gente.size()) {
			boton(6, 7, Caras.boton(Items.ARROW, "SIGUIENTE »", ChatFormatting.GOLD),
					(quien, cual) -> { pagina++; redibujar(); });
		}
		boton(6, 5, Caras.boton(Items.BARRIER, "VOLVER", ChatFormatting.GOLD,
				"Sin elegir a nadie"), (quien, cual) -> alVolver.run());
	}
}
