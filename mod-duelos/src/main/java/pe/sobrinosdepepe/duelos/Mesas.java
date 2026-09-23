package pe.sobrinosdepepe.duelos;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Las mesas que se estan armando ahora mismo, y lo que las hace arrancar.
 *
 * Viven en memoria y no en un archivo, igual que los retos de `Invitaciones`: una
 * mesa dura tres minutos y la gente que la arma esta conectada. Si el servidor se
 * reinicia en el medio, se vuelve a armar, que cuesta cuatro clicks.
 *
 * **Cada uno esta en una sola mesa.** Buscar por jugador tiene que dar una, y eso
 * es lo que hace que el menu sepa que mostrarte sin preguntarte nada.
 *
 * El `tick` es el que arranca las peleas completas. Esta aca y no en la pantalla a
 * proposito: la pantalla es una foto y se puede cerrar, y una pelea que solo
 * arranca si alguien tiene una ventana abierta no arranca nunca.
 */
public final class Mesas {

	/**
	 * Cuanto hay que esperar entre una mesa y la siguiente.
	 *
	 * Sin esto, armar y deshacer es gratis y en un minuto se le pueden mandar
	 * treinta invitaciones a la misma persona, cada una con su cartel en el chat.
	 */
	public static final int ESPERA_ENTRE_MESAS = 60;

	/** Por dueño, en minuscula. */
	private final Map<String, Mesa> mesas = new LinkedHashMap<>();

	/** Cuando armo cada uno su ultima mesa. */
	private final Map<String, Long> ultimaVez = new HashMap<>();

	/**
	 * El motivo por el que no puede armar una mesa ahora, o null si puede.
	 *
	 * Se pregunta antes de armarla y no despues: el que aprieta el boton tiene que
	 * enterarse en el momento, no cuando ya invito a alguien.
	 */
	public String porQueNoPuedeArmar(String dueno) {
		Mesa donde = dondeEsta(dueno);
		if (donde != null) {
			return donde.esElDueno(dueno)
					? "Ya estás armando una pelea. Deshacela primero."
					: "Estás en la pelea que arma " + donde.dueno + ". Bajate primero.";
		}
		int faltan = esperaParaArmar(dueno);
		if (faltan > 0) {
			return "Esperá " + faltan + " segundos para armar otra.";
		}
		return null;
	}

	/** Segundos que le faltan para poder armar otra mesa, o 0. */
	public int esperaParaArmar(String dueno) {
		Long cuando = ultimaVez.get(clave(dueno));
		if (cuando == null) return 0;
		long faltan = (cuando + ESPERA_ENTRE_MESAS * 1000L) - System.currentTimeMillis();
		return faltan <= 0 ? 0 : (int) Math.ceil(faltan / 1000.0);
	}

	/** Arma una mesa nueva. Quien llama ya pregunto si se puede. */
	public Mesa armar(String dueno, int porLado) {
		Mesa mesa = new Mesa(dueno, porLado);
		mesas.put(clave(dueno), mesa);
		ultimaVez.put(clave(dueno), System.currentTimeMillis());
		return mesa;
	}

	/** La mesa en la que esta sentado, sea el dueño o un invitado. */
	public Mesa dondeEsta(String quien) {
		limpiar();
		for (Mesa mesa : mesas.values()) {
			if (mesa.tiene(quien)) return mesa;
		}
		return null;
	}

	/**
	 * Lo saca de la mesa en la que este.
	 *
	 * Si el que se va es el dueño, la mesa entera se deshace: es la unica forma de
	 * que no quede una mesa sin nadie que la maneje.
	 */
	public void sacar(String quien) {
		Mesa mesa = dondeEsta(quien);
		if (mesa == null) return;
		if (mesa.esElDueno(quien)) {
			mesas.remove(clave(quien));
			return;
		}
		mesa.levantar(quien);
	}

	public void deshacer(Mesa mesa) {
		mesas.remove(clave(mesa.dueno));
	}

	private void limpiar() {
		mesas.values().removeIf(Mesa::vencio);
	}

	// --------------------------------------------------------------------- el tick

	/**
	 * Mantiene las mesas y **arranca las que estan completas**.
	 *
	 * Corre una vez por tick. Con cero mesas —que es casi siempre— sale enseguida.
	 */
	public void tick(MinecraftServer servidor, Duelos duelos) {
		limpiar();
		if (mesas.isEmpty()) return;

		for (Mesa mesa : new ArrayList<>(mesas.values())) {
			avisarDeLosQueSeFueron(servidor, mesa);
			if (!mesa.lista()) continue;

			String problema = porQueNoArranca(servidor, duelos, mesa);
			if (problema != null) {
				if (mesa.hayQueAvisar(problema)) avisarATodos(servidor, mesa, Carteles.aviso(problema));
				continue;
			}
			arrancar(servidor, duelos, mesa);
		}

		// Al dueño que se desconecto se le cae la mesa entera.
		mesas.entrySet().removeIf(entrada ->
				servidor.getPlayerList().getPlayerByName(entrada.getValue().dueno) == null);
	}

	private void arrancar(MinecraftServer servidor, Duelos duelos, Mesa mesa) {
		Cola.Par par = mesa.lados();
		int apuesta = mesa.apuesta;
		String clase = mesa.clase;

		mesa.cerrarPantallas(servidor);
		deshacer(mesa);

		if (!duelos.arrancarDeMesa(par, apuesta, clase)) {
			avisarATodos(servidor, mesa,
					Carteles.mal("No se pudo arrancar la pelea. Armala de nuevo."));
		}
	}

	/**
	 * Lo que impide que una mesa completa arranque, o null si puede arrancar.
	 *
	 * Es la misma lista que mira `/pvp` antes de aceptar un reto, y por el mismo
	 * motivo: entre que a uno lo invitaron y que el ultimo acepto pueden pasar
	 * minutos, y en el medio alguien se metio en una pelea de verdad o se gasto
	 * los shards en la tienda.
	 */
	private String porQueNoArranca(MinecraftServer servidor, Duelos duelos, Mesa mesa) {
		if (!duelos.hayArena()) return "No hay arena marcada. Avisale a Pepe.";
		if (duelos.actual() != null) return "La arena está ocupada: arranca al terminar la de ahora.";

		for (String nombre : mesa.todos()) {
			ServerPlayer quien = servidor.getPlayerList().getPlayerByName(nombre);
			if (quien == null) return nombre + " se fue del servidor.";
			if (duelos.estaOcupado(nombre)) return nombre + " ya está en otra pelea.";
			if (quien.entityTags().contains(Puntos.COMBATE)) {
				return nombre + " está en combate en el survival.";
			}
			if (mesa.apuesta > 0 && Puntos.shards(servidor, nombre) < mesa.apuesta) {
				return nombre + " no tiene los " + mesa.apuesta + " shards.";
			}
		}
		return null;
	}

	private void avisarDeLosQueSeFueron(MinecraftServer servidor, Mesa mesa) {
		List<String> sefueron = mesa.sacarLosQueSeFueron(servidor);
		if (sefueron.isEmpty()) return;
		ServerPlayer dueno = servidor.getPlayerList().getPlayerByName(mesa.dueno);
		if (dueno != null) {
			dueno.sendSystemMessage(Carteles.aviso(String.join(", ", sefueron)
					+ " se fue del servidor y dejó su lugar libre."));
		}
		mesa.refrescarPantallas(servidor);
	}

	private void avisarATodos(MinecraftServer servidor, Mesa mesa, Component que) {
		for (String nombre : mesa.todos()) {
			ServerPlayer quien = servidor.getPlayerList().getPlayerByName(nombre);
			if (quien != null) quien.sendSystemMessage(que);
		}
	}

	private static String clave(String nombre) {
		return nombre.toLowerCase(Locale.ROOT);
	}
}
