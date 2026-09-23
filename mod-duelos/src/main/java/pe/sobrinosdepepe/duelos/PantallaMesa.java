package pe.sobrinosdepepe.duelos;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;

/**
 * La mesa, dibujada: dos lados con sus lugares, y quien falta que acepte.
 *
 * Se lee como una cancha vista de arriba, con el marco al medio separando los dos
 * lados:
 *
 *      TU LADO                      EL OTRO
 *      [cara] [lugar libre]   |     [lugar libre] [lugar libre]
 *
 * Un lugar libre es un vidrio verde que se clickea; uno ocupado es la cara, y
 * abajo dice si ya acepto o si todavia se lo esta esperando. El dueño es el unico
 * que puede tocar los lugares, y por eso al invitado le sale todo apagado: ve la
 * mesa, ve quien falta, y su unico boton es aceptar o irse.
 *
 * **No hay boton de empezar.** La pelea arranca sola cuando el ultimo acepta, y de
 * eso se ocupa `Mesas.tick`. Esta pantalla es una foto de algo que decide otro: se
 * puede cerrar en cualquier momento sin que pase nada, que es justamente lo que
 * uno espera de una pantalla.
 */
public final class PantallaMesa extends Pantalla {

	private final Duelos duelos;
	private final Mesas mesas;
	private final MinecraftServer servidor;
	private final ServerPlayer mira;
	private final Mesa mesa;

	private PantallaMesa(int id, Inventory inventario, Duelos duelos, Mesas mesas,
			MinecraftServer servidor, ServerPlayer mira, Mesa mesa) {
		super(id, inventario, 6);
		this.duelos = duelos;
		this.mesas = mesas;
		this.servidor = servidor;
		this.mira = mira;
		this.mesa = mesa;
	}

	public static void abrir(ServerPlayer quien, Duelos duelos, Mesas mesas,
			MinecraftServer servidor, Mesa mesa) {
		Pantalla.abrir(quien, "PELEA " + mesa.comoSeLlama(), (id, inventario) ->
				new PantallaMesa(id, inventario, duelos, mesas, servidor, quien, mesa));
	}

	/** Si esta pantalla esta mostrando esa mesa. Lo pregunta `Mesa` al refrescar. */
	public boolean esDe(Mesa cual) {
		return mesa == cual;
	}

	public void cerrar() {
		mira.closeContainer();
	}

	private boolean mando() {
		return mesa.esElDueno(mira.getScoreboardName());
	}

	@Override
	protected void dibujar() {
		for (int columna = 1; columna <= 9; columna++) {
			adorno(1, columna, Caras.relleno());
			adorno(6, columna, Caras.relleno());
		}
		// La linea del medio: separa los dos lados y se lee sola.
		for (int fila = 2; fila <= 4; fila++) {
			adorno(fila, 5, Caras.relleno());
		}

		adorno(2, 2, Caras.boton(Items.LIME_BANNER, "TU LADO", ChatFormatting.GREEN,
				mesa.gente(0).size() + " de " + mesa.porLado));
		adorno(2, 8, Caras.boton(Items.RED_BANNER, "EL OTRO LADO", ChatFormatting.RED,
				mesa.gente(1).size() + " de " + mesa.porLado));

		for (int puesto = 0; puesto < mesa.porLado; puesto++) {
			dibujarLugar(3 + puesto, 2, 0, puesto);
			dibujarLugar(3 + puesto, 8, 1, puesto);
		}

		dibujarApuesta();
		dibujarAcciones();
	}

	private void dibujarLugar(int fila, int columna, int lado, int puesto) {
		if (fila > 5) return;
		String quien = mesa.quienEsta(lado, puesto);

		if (quien == null) {
			if (!mando()) {
				adorno(fila, columna, Caras.boton(Items.LIGHT_GRAY_STAINED_GLASS_PANE,
						"LUGAR LIBRE", ChatFormatting.GRAY, "Lo llena " + mesa.dueno));
				return;
			}
			boton(fila, columna, Caras.boton(Items.LIME_STAINED_GLASS_PANE, "LUGAR LIBRE",
					ChatFormatting.GREEN, "Clickealo para invitar a alguien"),
					(clickeo, cual) -> elegirPara(lado, puesto));
			return;
		}

		ServerPlayer sentado = servidor.getPlayerList().getPlayerByName(quien);
		boolean acepto = mesa.acepto(quien);
		String estado = acepto ? "Listo" : "Esperando que acepte...";
		ChatFormatting color = acepto ? ChatFormatting.GREEN : ChatFormatting.YELLOW;

		if (sentado == null) {
			adorno(fila, columna, Caras.boton(Items.BARRIER, quien, ChatFormatting.DARK_GRAY,
					"Se fue del servidor"));
			return;
		}
		if (mando() && !mesa.esElDueno(quien)) {
			boton(fila, columna, Caras.cabeza(sentado, color, estado, "Click para sacarlo"),
					(clickeo, cual) -> {
						mesa.levantar(quien);
						avisar(quien, Carteles.aviso(mesa.dueno + " te sacó de la pelea."));
						mesa.refrescarPantallas(servidor);
					});
			return;
		}
		adorno(fila, columna, Caras.cabeza(sentado, color, estado));
	}

	private void dibujarApuesta() {
		String cuanto = mesa.apuesta == 0 ? "Por el honor, sin apuesta"
				: mesa.apuesta + " shards cada lado";
		if (!mando()) {
			adorno(5, 5, Caras.boton(Items.AMETHYST_SHARD, "LA APUESTA",
					ChatFormatting.LIGHT_PURPLE, cuanto));
			return;
		}
		int tiene = Puntos.shards(servidor, mesa.dueno);
		boton(5, 5, Caras.boton(Items.AMETHYST_SHARD, "LA APUESTA", ChatFormatting.LIGHT_PURPLE,
				cuanto, "Tenés " + tiene + " shards",
				"Click para subir de a " + Mesa.PASO_APUESTA,
				"Al llegar al tope vuelve a cero"),
				(clickeo, cual) -> {
					mesa.subirApuesta(tiene);
					mesa.refrescarPantallas(servidor);
				});
	}

	private void dibujarAcciones() {
		if (!mando()) {
			if (mesa.acepto(mira.getScoreboardName())) {
				adorno(6, 5, Caras.boton(Items.LIME_DYE, "YA ACEPTASTE", ChatFormatting.GREEN,
						"Arranca sola cuando acepten todos"));
			} else {
				boton(6, 5, Caras.boton(Items.LIME_DYE, "ACEPTAR", ChatFormatting.GREEN,
						"Entrás a esta pelea",
						"Arranca sola cuando acepten todos"), (clickeo, cual) -> {
							mesa.aceptar(mira.getScoreboardName());
							avisarATodos(Carteles.aviso(mira.getScoreboardName() + " aceptó."));
							mesa.refrescarPantallas(servidor);
						});
			}
			boton(6, 7, Caras.boton(Items.BARRIER, "IRME", ChatFormatting.RED,
					"Dejás tu lugar libre"), (clickeo, cual) -> {
						mesas.sacar(mira.getScoreboardName());
						avisar(mesa.dueno, Carteles.aviso(mira.getScoreboardName() + " se bajó."));
						mesa.refrescarPantallas(servidor);
						cerrar();
					});
			return;
		}

		if (mesa.lista()) {
			adorno(6, 5, Caras.boton(Items.NETHERITE_SWORD, "ARRANCANDO...",
					ChatFormatting.GOLD, "Aceptaron todos"));
		} else if (mesa.miLadoListo() && mesa.elOtroLadoVacio()) {
			boton(6, 5, Caras.boton(Items.CLOCK, "ANOTARNOS A LA COLA", ChatFormatting.GOLD,
					"Tu lado entero espera rival",
					"Los cruzamos con quien aparezca"),
					(clickeo, cual) -> anotarse());
		} else {
			adorno(6, 5, Caras.boton(Items.CLOCK, "FALTA GENTE", ChatFormatting.GRAY,
					faltaQue()));
		}

		boton(6, 7, Caras.boton(Items.BARRIER, "DESHACER LA MESA", ChatFormatting.RED,
				"Se cancela para todos"), (clickeo, cual) -> {
					avisarATodos(Carteles.aviso(mesa.dueno + " deshizo la pelea."));
					mesa.cerrarPantallas(servidor);
					mesas.deshacer(mesa);
					cerrar();
				});
	}

	private String faltaQue() {
		if (!mesa.losQueFaltan().isEmpty()) {
			return "Falta que acepten: " + String.join(", ", mesa.losQueFaltan());
		}
		return "Llená los lugares libres";
	}

	// ------------------------------------------------------------------ acciones

	private void elegirPara(int lado, int puesto) {
		ElegirJugador.abrir(mira, servidor,
				lado == 0 ? "INVITAR A TU LADO" : "INVITAR AL OTRO LADO",
				this::porQueNo,
				otro -> {
					String nombre = otro.getScoreboardName();
					if (!mesa.sentar(lado, puesto, nombre)) {
						mira.sendSystemMessage(Carteles.mal("No pude sentar a " + nombre + "."));
					} else {
						otro.sendSystemMessage(Carteles.invitacionAMesa(
								mesa.dueno, mesa.comoSeLlama(), mesa.apuesta, lado == 0));
						otro.level().playSound(null, otro.getX(), otro.getY(), otro.getZ(),
								net.minecraft.sounds.SoundEvents.ANVIL_LAND,
								net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.6f);
						mira.sendSystemMessage(Carteles.aviso("Invitaste a " + nombre + "."));
					}
					PantallaMesa.abrir(mira, duelos, mesas, servidor, mesa);
				},
				() -> PantallaMesa.abrir(mira, duelos, mesas, servidor, mesa));
	}

	/**
	 * El motivo por el que ese no se puede invitar, o null si se puede.
	 *
	 * Se muestra debajo de la cara, apagada. Mostrar a todos y despues decirle que
	 * no al que clickeo es peor que no mostrarlos: el que mira ya se hizo la idea.
	 */
	private String porQueNo(ServerPlayer otro) {
		String nombre = otro.getScoreboardName();
		if (mesa.tiene(nombre)) return "Ya está en esta pelea";
		if (duelos.estaOcupado(nombre)) return "Ya está peleando o en la cola";
		if (duelos.espera().estaAnotado(nombre)) return "Está en la lista de espera";
		if (mesas.dondeEsta(nombre) != null) return "Está armando otra pelea";
		if (otro.entityTags().contains(Puntos.COMBATE)) return "Está en combate";
		int falta = mesa.esperaParaInvitar(nombre);
		if (falta > 0) return "Lo invitaste recién: esperá " + falta + "s";
		if (mesa.apuesta > 0 && Puntos.shards(servidor, nombre) < mesa.apuesta) {
			return "No tiene los " + mesa.apuesta + " shards";
		}
		return null;
	}

	private void anotarse() {
		duelos.espera().anotarLado(mesa.gente(0), Cola.Modo.porNombre(mesa.comoSeLlama()));
		avisarATodos(Carteles.aviso("Anotados a la cola de " + mesa.comoSeLlama()
				+ ". Arranca cuando haya rival."));
		mesa.cerrarPantallas(servidor);
		mesas.deshacer(mesa);
		cerrar();
	}

	private void avisar(String quien, Component que) {
		ServerPlayer conectado = servidor.getPlayerList().getPlayerByName(quien);
		if (conectado != null) conectado.sendSystemMessage(que);
	}

	private void avisarATodos(Component que) {
		for (String quien : mesa.todos()) avisar(quien, que);
	}
}
