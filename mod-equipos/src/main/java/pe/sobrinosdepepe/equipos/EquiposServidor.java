package pe.sobrinosdepepe.equipos;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Los equipos de SOBRINOS DE PEPE.
 *
 * El servidor es PvP libre y de a ratos eso quiere decir tres contra uno sin
 * querer, porque no habia ninguna forma de decir "estos dos andamos juntos".
 * Un equipo es eso y nada mas: entre companeros no se pega, y se ven en la
 * barra de arriba.
 *
 * De donde sale la barra: 26.1 la trae de fabrica (la gamerule `locator_bar`) y
 * de fabrica muestra a TODOS los jugadores del servidor, que en un survival
 * donde matar paga es un radar que arruina el juego. El mod
 * **team-only-locator-bar** la deja mostrando unicamente a los del mismo equipo
 * del scoreboard, y este mod es el que reparte esos equipos. Los dos van del
 * lado del servidor y ninguno de los dos toca el launcher.
 *
 * Este mod NO va en el pack: es `environment: server`. El jugador no instala
 * nada; le aparece `/equipo` y listo.
 */
public final class EquiposServidor implements DedicatedServerModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("equiposdepepe");

	@Override
	public void onInitializeServer() {
		Registro registro = new Registro();
		Invitaciones invitaciones = new Invitaciones();
		ComandoEquipo comando = new ComandoEquipo(registro, invitaciones);

		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registros, entorno) -> comando.registrar(dispatcher));

		// El archivo se lee recien cuando el servidor termino de arrancar, y no en
		// este metodo: los equipos del scoreboard viven adentro del mundo, y aca
		// todavia no hay mundo cargado al que copiarselos.
		ServerLifecycleEvents.SERVER_STARTED.register(registro::alArrancar);

		LOG.info("Equipos listos. El archivo es config/{}", Registro.ARCHIVO);
	}
}
