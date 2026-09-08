package pe.sobrinosdepepe.acceso;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Le contesta al servidor con el ticket que dejo el launcher.
 *
 * El launcher lo pasa en una variable de entorno del proceso del juego, no en un
 * archivo ni en un argumento: un archivo lo copia cualquiera junto con la carpeta
 * de mods, y los argumentos se ven enteros en el administrador de tareas y salen
 * pegados en los reportes de error. Una variable de entorno vive y muere con el
 * proceso, y solo la pone quien lo arranca.
 *
 * Cuando no hay ticket contesta igual, con el campo vacio, para que el servidor
 * pueda distinguir "tiene los mods pero no abrio el launcher" de "no tiene los
 * mods" y mostrar el cartel que corresponde.
 */
public final class AccesoCliente implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("accesodepepe");

	@Override
	public void onInitializeClient() {
		ClientLoginNetworking.registerGlobalReceiver(Canal.ID, (cliente, handler, pedido, escuchar) -> {
			String ticket = System.getenv(Canal.VARIABLE);

			if (ticket == null || ticket.isBlank()) {
				LOG.warn("El juego se abrio sin el launcher: no tengo ticket para entrar al servidor");
				ticket = "";
			}

			FriendlyByteBuf respuesta = FriendlyByteBufs.create();
			respuesta.writeUtf(ticket);
			return CompletableFuture.completedFuture(respuesta);
		});
	}
}
