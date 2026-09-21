package pe.sobrinosdepepe.duelos;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * El torneo: una llave de uno contra uno hasta que queda uno solo.
 *
 * Cada llave es un duelo igual a cualquier otro —kit al azar, la misma arena, la
 * gente apostando— y por eso este archivo es corto: lo unico que hace es decidir
 * quien pelea con quien y llevar la cuenta de los que van quedando.
 *
 * **Se encola una pelea por vez y no la ronda entera.** Encolar la ronda completa
 * suena mas prolijo y es una fuente de peleas fantasma: alguien se desconecta,
 * su llave desaparece de la cola y el torneo se queda esperando un resultado que
 * no va a llegar nunca. Encolando de a una, si la que esta en la cola no puede
 * arrancar se resuelve en el momento y se encola la siguiente.
 *
 * Al que se desconecta antes de que le toque **no se lo saca de la lista**: se
 * resuelve cuando le toca, y ahi el que estaba pasa de ronda sin pelear. Sacarlo
 * antes obligaria a rearmar la llave en el medio, que es de donde salen los
 * torneos que se cuelgan.
 *
 * La entrada y el pozo son shards, como todo lo que se apuesta aca. Se cobran al
 * anotarse y se los lleva enteros el campeon.
 */
public final class Torneo {
	private static final Logger LOG = LoggerFactory.getLogger("duelosdepepe");

	private final Duelos duelos;
	private MinecraftServer servidor;

	private boolean abierto;
	private boolean jugando;
	private int entrada;
	private int pozo;

	private final List<String> anotados = new ArrayList<>();
	private final List<String> vivos = new ArrayList<>();
	private final List<String> ganadores = new ArrayList<>();
	private final Deque<String[]> porPelear = new ArrayDeque<>();

	public Torneo(Duelos duelos) {
		this.duelos = duelos;
	}

	public void alArrancar(MinecraftServer servidor) {
		this.servidor = servidor;
	}

	// ------------------------------------------------------------------- consultas

	public boolean abierto() {
		return abierto;
	}

	public boolean jugando() {
		return jugando;
	}

	public int entrada() {
		return entrada;
	}

	public int pozo() {
		return pozo;
	}

	public List<String> lista() {
		return List.copyOf(jugando ? vivos : anotados);
	}

	public boolean estaAnotado(String quien) {
		return anotados.stream().anyMatch(q -> q.equalsIgnoreCase(quien))
				|| vivos.stream().anyMatch(q -> q.equalsIgnoreCase(quien));
	}

	// ------------------------------------------------------------------ inscripcion

	/** Devuelve el error, o null si quedo abierto. */
	public Component abrir(int entrada) {
		if (abierto || jugando) return Carteles.torneoYaHay();
		this.abierto = true;
		this.jugando = false;
		this.entrada = Math.max(0, entrada);
		this.pozo = 0;
		anotados.clear();
		vivos.clear();
		ganadores.clear();
		porPelear.clear();

		servidor.getPlayerList().broadcastSystemMessage(
				Carteles.torneoAbierto(this.entrada, 0), false);
		LOG.info("Torneo abierto, entrada {} shards", this.entrada);
		return null;
	}

	public Component entrar(ServerPlayer quien) {
		if (jugando) return Carteles.torneoYaEmpezo();
		if (!abierto) return Carteles.torneoNoHay();
		String nombre = quien.getScoreboardName();
		if (estaAnotado(nombre)) return Carteles.torneoYaEstas();

		int tiene = Puntos.shards(servidor, nombre);
		if (entrada > tiene) return Carteles.sinShards(entrada, tiene);

		if (entrada > 0) {
			Puntos.sumarShards(servidor, nombre, -entrada);
			pozo += entrada;
		}
		anotados.add(nombre);
		servidor.getPlayerList().broadcastSystemMessage(
				Carteles.torneoAnotado(nombre, anotados.size()), false);
		return null;
	}

	public Component salir(ServerPlayer quien) {
		if (jugando) return Carteles.torneoYaEmpezo();
		if (!abierto) return Carteles.torneoNoHay();
		String nombre = quien.getScoreboardName();
		if (!anotados.removeIf(q -> q.equalsIgnoreCase(nombre))) return Carteles.torneoNoEstas();

		if (entrada > 0) {
			Puntos.sumarShards(servidor, nombre, entrada);
			pozo -= entrada;
		}
		quien.sendSystemMessage(Carteles.bien("Te borraste del torneo."));
		return null;
	}

	/** Al que se va antes de que empiece se lo borra y se le devuelve la entrada. */
	public void seFue(String quien) {
		if (!abierto || jugando) return;
		if (!anotados.removeIf(q -> q.equalsIgnoreCase(quien))) return;
		if (entrada > 0) {
			Puntos.sumarShards(servidor, quien, entrada);
			pozo -= entrada;
		}
	}

	public Component cancelar() {
		if (!abierto && !jugando) return Carteles.torneoNoHay();
		// La entrada vuelve a TODOS los que la pagaron y no solo a los que siguen en
		// pie: si el torneo se cancela en la mitad, el que perdio en la primera
		// ronda tampoco llego a jugarse el pozo.
		if (entrada > 0) {
			for (String quien : anotados) Puntos.sumarShards(servidor, quien, entrada);
		}
		apagar();
		servidor.getPlayerList().broadcastSystemMessage(Carteles.torneoCancelado(), false);
		return null;
	}

	// --------------------------------------------------------------------- la llave

	public Component arrancar() {
		if (jugando) return Carteles.torneoYaEmpezo();
		if (!abierto) return Carteles.torneoNoHay();
		if (anotados.size() < 2) return Carteles.torneoFaltanAnotados(anotados.size());

		abierto = false;
		jugando = true;
		vivos.clear();
		vivos.addAll(anotados);
		Collections.shuffle(vivos);

		servidor.getPlayerList().broadcastSystemMessage(
				Carteles.torneoArranca(vivos.size(), pozo), false);
		LOG.info("Torneo arrancando con {}: {}", vivos.size(), String.join(", ", vivos));

		armarLaRonda();
		siguiente();
		return null;
	}

	/**
	 * Arma los cruces de la ronda. El que queda desparejado pasa derecho, que es
	 * lo que hace que funcione con cualquier cantidad de anotados y no solo con 4,
	 * 8 o 16.
	 */
	private void armarLaRonda() {
		ganadores.clear();
		porPelear.clear();
		Collections.shuffle(vivos);

		int i = 0;
		while (i + 1 < vivos.size()) {
			porPelear.add(new String[] {vivos.get(i), vivos.get(i + 1)});
			i += 2;
		}
		if (i < vivos.size()) {
			String solo = vivos.get(i);
			ganadores.add(solo);
			servidor.getPlayerList().broadcastSystemMessage(Carteles.torneoLibre(solo), false);
		}
	}

	/** Encola la proxima llave, o termina la ronda si no quedan. */
	private void siguiente() {
		while (porPelear.isEmpty()) {
			// Se acabo la ronda.
			vivos.clear();
			vivos.addAll(ganadores);
			if (vivos.size() <= 1) {
				coronar(vivos.isEmpty() ? null : vivos.get(0));
				return;
			}
			armarLaRonda();
		}

		String[] cruce = porPelear.poll();
		ServerPlayer primero = servidor.getPlayerList().getPlayerByName(cruce[0]);
		ServerPlayer segundo = servidor.getPlayerList().getPlayerByName(cruce[1]);

		// Alguno no esta: el otro pasa sin pelear y se sigue con la llave que venia.
		if (primero == null || segundo == null) {
			if (primero != null) pasa(cruce[0]);
			else if (segundo != null) pasa(cruce[1]);
			siguiente();
			return;
		}

		// Las llaves del torneo van siempre con la clase al azar: elegirla seria
		// darle a uno de los dos algo que el otro no pidio.
		duelos.encolar(cruce[0], cruce[1], 0, null, true);
	}

	/** Lo llama Duelos cuando termina una pelea que era de torneo. */
	public void termino(Duelo duelo) {
		if (!jugando) return;

		if (duelo.ganador() != null) {
			pasa(duelo.ganador());
		} else {
			// Empate o pelea cortada. Si los dos siguen conectados se vuelve a jugar;
			// si no, pasa el que quedo. Un empate no puede eliminar a los dos.
			ServerPlayer primero = servidor.getPlayerList().getPlayerByName(duelo.uno);
			ServerPlayer segundo = servidor.getPlayerList().getPlayerByName(duelo.otro);
			if (primero != null && segundo != null) {
				servidor.getPlayerList().broadcastSystemMessage(
						Carteles.aviso("Empate: " + duelo.uno + " y " + duelo.otro
								+ " la vuelven a jugar."), false);
				porPelear.addFirst(new String[] {duelo.uno, duelo.otro});
			} else if (primero != null) {
				pasa(duelo.uno);
			} else if (segundo != null) {
				pasa(duelo.otro);
			}
		}
		siguiente();
	}

	/** La llave que estaba en la cola no pudo arrancar: se resuelve sin pelear. */
	public void noPudoArrancar(Duelos.EnEspera espera) {
		if (!jugando) return;
		ServerPlayer primero = servidor.getPlayerList().getPlayerByName(espera.uno());
		ServerPlayer segundo = servidor.getPlayerList().getPlayerByName(espera.otro());
		if (primero != null) pasa(espera.uno());
		else if (segundo != null) pasa(espera.otro());
		siguiente();
	}

	private void pasa(String quien) {
		if (ganadores.stream().anyMatch(q -> q.equalsIgnoreCase(quien))) return;
		ganadores.add(quien);
		int quedan = ganadores.size() + porPelear.size() * 2;
		if (quedan > 1) {
			servidor.getPlayerList().broadcastSystemMessage(
					Carteles.torneoPasa(quien, quedan), false);
		}
	}

	private void coronar(String campeon) {
		if (campeon != null) {
			if (pozo > 0) Puntos.sumarShards(servidor, campeon, pozo);
			servidor.getPlayerList().broadcastSystemMessage(
					Carteles.torneoCampeon(campeon, pozo), false);
			LOG.info("Campeon del torneo: {} (pozo {})", campeon, pozo);
		} else {
			// No quedo nadie en pie: se fueron todos del servidor antes de terminar.
			// El pozo no puede quedarse sin dueño, asi que vuelve a los que pagaron
			// la entrada. `anotados` sigue teniendo la lista original: `arrancar()`
			// la copia a `vivos` y no la vacia.
			if (entrada > 0) {
				for (String quien : anotados) Puntos.sumarShards(servidor, quien, entrada);
			}
			servidor.getPlayerList().broadcastSystemMessage(Carteles.torneoCancelado(), false);
		}
		apagar();
	}

	private void apagar() {
		abierto = false;
		jugando = false;
		entrada = 0;
		pozo = 0;
		anotados.clear();
		vivos.clear();
		ganadores.clear();
		porPelear.clear();
	}
}
