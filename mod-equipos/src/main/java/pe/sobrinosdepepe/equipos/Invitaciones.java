package pe.sobrinosdepepe.equipos;

import java.util.ArrayList;
import java.util.List;

/**
 * Las invitaciones que estan esperando respuesta.
 *
 * Viven en memoria y no en el archivo, a proposito: una invitacion dura dos
 * minutos y el que la mando esta conectado. Si el servidor se reinicia en el
 * medio, se vuelve a invitar y listo; guardarlas seria arrastrar por semanas
 * invitaciones que nadie se acuerda de haber mandado.
 */
public final class Invitaciones {
	/** Cuanto dura una invitacion sin contestar. */
	public static final int MINUTOS = 2;

	private record Invitacion(String invitado, String equipo, long vence) {}

	private final List<Invitacion> pendientes = new ArrayList<>();

	public void invitar(String invitado, String equipo) {
		limpiar();
		pendientes.add(new Invitacion(invitado, equipo,
				System.currentTimeMillis() + MINUTOS * 60_000L));
	}

	public boolean hay(String invitado, String equipo) {
		limpiar();
		return pendientes.stream().anyMatch(i ->
				i.invitado().equalsIgnoreCase(invitado) && i.equipo().equalsIgnoreCase(equipo));
	}

	/** A que equipos lo invitaron, para el autocompletado y para el aviso. */
	public List<String> de(String invitado) {
		limpiar();
		return pendientes.stream()
				.filter(i -> i.invitado().equalsIgnoreCase(invitado))
				.map(Invitacion::equipo)
				.toList();
	}

	/** Se llama cuando el jugador entra a un equipo: el resto ya no tiene sentido. */
	public void olvidar(String invitado) {
		pendientes.removeIf(i -> i.invitado().equalsIgnoreCase(invitado));
	}

	private void limpiar() {
		long ahora = System.currentTimeMillis();
		pendientes.removeIf(i -> i.vence() < ahora);
	}
}
