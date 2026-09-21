package pe.sobrinosdepepe.duelos;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * El coliseo de SOBRINOS DE PEPE.
 *
 * `/pvp <jugador>` reta a alguien. Al otro le llega un boton, lo clickea, y los
 * dos caen en la arena con el MISMO kit sacado al azar, quietos, mientras el
 * resto del servidor apuesta shards a quien va a ganar. Despues la cuenta de
 * 3, 2, 1 en pantalla y a pelear. El que gana se lleva la apuesta de los dos y
 * los que le acertaron cobran.
 *
 * Las tres cosas que lo hacen distinto de salir a pelear afuera:
 *
 *  - **no se pierde nada**: adentro se juega con un kit prestado y el inventario
 *    de verdad vuelve entero al terminar, asi que el que pierde no pierde el
 *    equipo. Lo unico que se juega es lo que se aposto;
 *  - **no se mete nadie**: al que no esta peleando lo saca de la arena, y los
 *    golpes no pasan ni para adentro ni para afuera;
 *  - **la arena vuelve a como estaba**: se puede romper todo, poner crystals y
 *    volar el piso con TNT; al terminar se rehace bloque por bloque.
 *
 * Este mod NO va en el pack del launcher: es `environment: server`. El jugador no
 * instala nada, le aparece `/pvp <jugador>` y listo. La arena la marca el que
 * administra con la misma varita de las zonas, con el click izquierdo.
 */
public final class DuelosServidor implements DedicatedServerModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("duelosdepepe");

	@Override
	public void onInitializeServer() {
		Registro registro = new Registro();
		Duelos duelos = new Duelos(registro);
		Torneo torneo = new Torneo(duelos);
		duelos.conTorneo(torneo);
		Varita varita = new Varita();
		ComandoPvp comando = new ComandoPvp(duelos, registro, varita, torneo);

		varita.registrar();
		duelos.registrarEventos();
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registros, entorno) -> comando.registrar(dispatcher));

		// El archivo se lee recien cuando el servidor termino de arrancar y no en
		// este metodo: adentro hay inventarios, y para leer un item hace falta el
		// registro de items del servidor, que aca todavia no existe.
		ServerLifecycleEvents.SERVER_STARTED.register(servidor -> {
			registro.alArrancar(servidor);
			duelos.alArrancar(servidor);
			torneo.alArrancar(servidor);
		});

		LOG.info("Duelos listos. El archivo es config/{}", Registro.ARCHIVO);
	}
}
