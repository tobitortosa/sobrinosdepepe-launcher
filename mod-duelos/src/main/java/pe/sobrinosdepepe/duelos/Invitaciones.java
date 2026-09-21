package pe.sobrinosdepepe.duelos;

import java.util.ArrayList;
import java.util.List;

/**
 * Los retos que estan esperando respuesta.
 *
 * Viven en memoria y no en el archivo, igual que las invitaciones de equipo: un
 * reto dura un minuto y el que lo mando esta conectado. Si el servidor se
 * reinicia en el medio, se vuelve a retar.
 *
 * Un minuto y no dos: el que reta a alguien lo esta mirando, y un reto de hace
 * cinco minutos que de golpe te mete en la arena es peor que no tener reto.
 */
public final class Invitaciones {
	/** Cuanto dura un reto sin contestar. */
	public static final int SEGUNDOS = 60;

	/**
	 * Un reto: quien reta, a quien, cuantos shards se juegan los dos y con que
	 * clase. La clase en null es "la que salga", que es lo normal.
	 */
	public record Reto(String retador, String retado, int apuesta, String clase, long vence) {}

	private final List<Reto> pendientes = new ArrayList<>();

	public void retar(String retador, String retado, int apuesta, String clase) {
		limpiar();
		pendientes.removeIf(r -> r.retador().equalsIgnoreCase(retador)
				&& r.retado().equalsIgnoreCase(retado));
		pendientes.add(new Reto(retador, retado, apuesta, clase,
				System.currentTimeMillis() + SEGUNDOS * 1000L));
	}

	/** El reto que le mando ese a este, o null si no hay ninguno vivo. */
	public Reto buscar(String retado, String retador) {
		limpiar();
		return pendientes.stream()
				.filter(r -> r.retado().equalsIgnoreCase(retado)
						&& r.retador().equalsIgnoreCase(retador))
				.findFirst()
				.orElse(null);
	}

	/** Quienes lo retaron, para el autocompletado y para el cartel. */
	public List<String> quienesRetaron(String retado) {
		limpiar();
		return pendientes.stream()
				.filter(r -> r.retado().equalsIgnoreCase(retado))
				.map(Reto::retador)
				.toList();
	}

	/** Se llama cuando el duelo arranca: los demas retos de esos dos ya no valen. */
	public void olvidar(String jugador) {
		pendientes.removeIf(r -> r.retador().equalsIgnoreCase(jugador)
				|| r.retado().equalsIgnoreCase(jugador));
	}

	public void olvidarUno(String retado, String retador) {
		pendientes.removeIf(r -> r.retado().equalsIgnoreCase(retado)
				&& r.retador().equalsIgnoreCase(retador));
	}

	private void limpiar() {
		long ahora = System.currentTimeMillis();
		pendientes.removeIf(r -> r.vence() < ahora);
	}
}
