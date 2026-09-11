package pe.sobrinosdepepe.acceso;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.network.FriendlyByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Le contesta al servidor dos cosas: el ticket que dejo el launcher y la lista de
 * los mods que tiene cargados el juego.
 *
 * El ticket lo pasa el launcher en una variable de entorno del proceso del juego,
 * no en un archivo ni en un argumento: un archivo lo copia cualquiera junto con la
 * carpeta de mods, y los argumentos se ven enteros en el administrador de tareas y
 * salen pegados en los reportes de error. Una variable de entorno vive y muere con
 * el proceso, y solo la pone quien lo arranca.
 *
 * Cuando no hay ticket contesta igual, con el campo vacio, para que el servidor
 * pueda distinguir "tiene los mods pero no abrio el launcher" de "no tiene los
 * mods" y mostrar el cartel que corresponde.
 *
 * La lista de mods va para que el servidor no deje entrar a los clientes de
 * trampas (Meteor, Wurst, los xray) y para que en el log quede escrito con que
 * juega cada uno. Se manda siempre, tenga ticket o no.
 */
public final class AccesoCliente implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("accesodepepe");

	/**
	 * Los tres que no son mods de nadie: los pone Fabric solo. Nombrarlos en cada
	 * login no le dice nada a nadie y hace el log ilegible.
	 */
	private static final Set<String> DEL_JUEGO = Set.of("minecraft", "java", "fabricloader");

	/**
	 * El tope de la lista, en caracteres. Con dieciseis mods no se llega ni a la
	 * mitad; esta para que un cliente con doscientos mods no mande un paquete
	 * enorme durante el login.
	 */
	private static final int LARGO_MAXIMO = 1500;

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
			respuesta.writeUtf(misMods());
			return CompletableFuture.completedFuture(respuesta);
		});
	}

	/**
	 * Los mods cargados, separados por coma y ordenados alfabeticamente.
	 *
	 * Solo los de arriba de todo: Fabric API trae cuarenta modulos adentro y cada
	 * uno cuenta como un mod, asi que los que vienen adentro de otro se saltean con
	 * getContainingMod(). Ordenados para que dos logins del mismo jugador den la
	 * misma linea en el log y se note cuando cambio algo.
	 */
	private static String misMods() {
		List<String> nombres = new ArrayList<>();

		try {
			for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
				if (mod.getContainingMod().isPresent()) continue;

				String id = mod.getMetadata().getId();
				if (!DEL_JUEGO.contains(id)) nombres.add(id);
			}
		} catch (Exception e) {
			// Contar los mods no puede ser el motivo por el que alguien no entra a
			// jugar: si esto falla se manda la lista vacia y el servidor sigue como
			// cuando el cliente tenia el mod viejo.
			LOG.warn("No pude armar la lista de mods", e);
			return "";
		}

		Collections.sort(nombres);

		StringBuilder lista = new StringBuilder();
		for (String nombre : nombres) {
			if (lista.length() + nombre.length() + 1 > LARGO_MAXIMO) break;
			if (lista.length() > 0) lista.append(',');
			lista.append(nombre);
		}

		return lista.toString();
	}
}
