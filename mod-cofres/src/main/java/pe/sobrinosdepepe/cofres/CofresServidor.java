package pe.sobrinosdepepe.cofres;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * `/cofre <jugador>` abre el cofre de ender de cualquiera.
 *
 * El caso para el que existe: alguien queda baneado por IP y hay que ver que
 * guardo. Los comandos tipo /invsee de otros mods le abren el inventario a un
 * jugador CONECTADO, y con un baneado no hay a quien abrirle nada. Esto lee el
 * archivo del jugador —lo que quedo guardado en su ultima desconexion— y arma
 * con eso una ventana de solo lectura.
 *
 * Es solo para operadores de nivel 4 y no lo ve nadie mas: la ventana se le abre
 * al que escribio el comando y a nadie mas se le avisa nada.
 */
public final class CofresServidor implements DedicatedServerModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("cofresdepepe");

	@Override
	public void onInitializeServer() {
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registros, entorno) -> ComandoCofre.registrar(dispatcher));
	}
}
