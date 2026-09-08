package pe.sobrinosdepepe.acceso;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Solo entra al servidor quien abrio el juego con el SOBRINOS DE PEPE Launcher.
 *
 * Como funciona: mientras el jugador esta entrando, y antes de que aparezca en
 * el mundo, el servidor le manda un pedido por un canal nuestro. El launcher le
 * dejo al juego un ticket firmado por el backend, y este mod del lado del
 * cliente lo contesta. Si la firma cierra y no vencio, pasa.
 *
 * Los tres motivos por los que alguien no entra, y lo que ve cada uno:
 *
 *  - Entro con TLauncher o con cualquier Minecraft sin nuestros mods. El
 *    protocolo de Minecraft obliga a contestar el pedido, y un cliente que no
 *    conoce el canal contesta "no entendi": eso llega aca como entendido=false.
 *    Ve el cartel con el link para bajarse el launcher.
 *  - Tiene los mods (le pasamos la carpeta) pero abrio el juego por su cuenta.
 *    Contesta el pedido pero sin ticket, porque el ticket no esta en la carpeta
 *    del juego: se lo pasa el launcher al arrancar, y solo el launcher lo tiene.
 *  - Tiene un ticket viejo, o el de otro jugador. La firma lleva el nombre
 *    adentro y se compara con el del login.
 *
 * Por que en el login y no cuando ya esta jugando: aca todavia no se genero su
 * jugador ni se cargo su inventario, y el cartel se muestra en la pantalla de
 * desconexion completo, con varias lineas y el link.
 */
public final class AccesoServidor implements DedicatedServerModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("accesodepepe");

	/** Un ticket mide unos 90 caracteres; el tope es para no leer lo que manden. */
	private static final int LARGO_MAXIMO = 256;

	@Override
	public void onInitializeServer() {
		ConfigAcceso config = ConfigAcceso.leer(LOG);

		// El pedido va SIEMPRE, incluso si al servidor le falta la config: es lo que
		// hace que un cliente sin el mod se delate contestando "no entendi".
		ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, sync) ->
				sender.sendPacket(Canal.ID, FriendlyByteBufs.create()));

		ServerLoginNetworking.registerGlobalReceiver(Canal.ID,
				(server, handler, entendido, respuesta, sync, sender) -> {
					// Ojo: para echar a alguien se usa handler y NO el sender que llega
					// aca. El disconnect() del sender de Fabric cierra el socket y nada
					// mas, asi que el jugador ve "Desconectado" pelado y nunca se entera
					// de que tiene que bajarse el launcher. El del handler manda primero
					// el paquete con el cartel, que es todo el punto de esto.
					String nombre = nombreDelLogin(handler);

					if (!config.configurado()) {
						rechazar(handler, Carteles.sinConfigurar(), nombre, "al servidor le falta " + ConfigAcceso.ARCHIVO);
						return;
					}

					if (!entendido) {
						rechazar(handler, Carteles.sinLauncher(config.link()), nombre, "cliente sin el mod");
						return;
					}

					Ticket.Resultado resultado =
							Ticket.verificar(config.secreto(), leerTicket(respuesta), System.currentTimeMillis() / 1000);

					switch (resultado.estado()) {
						case VACIO -> rechazar(handler, Carteles.sinTicket(config.link()), nombre, "sin ticket");
						case FORMATO, FIRMA ->
								rechazar(handler, Carteles.invalido(config.link()), nombre, "ticket que no verifica");
						case VENCIDO ->
								rechazar(handler, Carteles.vencido(config.link()), nombre, "ticket vencido");
						case OK -> {
							if (!resultado.nombre().equals(nombre)) {
								rechazar(handler, Carteles.invalido(config.link()), nombre,
										"ticket emitido para " + resultado.nombre());
							} else {
								LOG.info("{} entra con el launcher", nombre);
							}
						}
					}
				});

		LOG.info("Acceso solo con el launcher: activo");
	}

	/**
	 * Un cliente al que no le importa el protocolo puede contestar cualquier cosa,
	 * asi que leer el ticket no puede tirar la conexion abajo: si el contenido no
	 * es el que esperamos, cuenta como que no lo mando.
	 */
	private static String leerTicket(FriendlyByteBuf respuesta) {
		try {
			if (respuesta == null || !respuesta.isReadable()) return "";
			return respuesta.readUtf(LARGO_MAXIMO);
		} catch (Exception e) {
			return "";
		}
	}

	/**
	 * getUserName() devuelve el nombre pedido y, atras, la direccion desde donde se
	 * conecta ("PEPE (1.2.3.4:56789)"), porque esta pensado para el log. El nombre
	 * es lo que va antes del primer espacio: un usuario valido es solo letras,
	 * numeros y guion bajo, asi que nunca tiene espacios.
	 */
	private static String nombreDelLogin(ServerLoginPacketListenerImpl handler) {
		String conDireccion = handler.getUserName();
		int espacio = conDireccion.indexOf(' ');
		return espacio < 0 ? conDireccion : conDireccion.substring(0, espacio);
	}

	/**
	 * Echa a alguien con el cartel puesto.
	 *
	 * Tiene que ser el disconnect del handler de vanilla, que manda un
	 * ClientboundLoginDisconnectPacket con el texto y despues cierra la conexion.
	 * El disconnect() del PacketSender de Fabric va derecho a
	 * Connection.disconnect(), que cierra el canal SIN mandar ningun paquete: el
	 * servidor loguea el motivo igual, pero el jugador solo ve "Desconectado" y no
	 * hay forma de que se entere de que necesita el launcher. Nos comimos
	 * exactamente ese error la primera vez que se probo.
	 */
	private static void rechazar(
			ServerLoginPacketListenerImpl handler, Component cartel, String nombre, String motivo) {
		LOG.info("No dejo entrar a {}: {}", nombre, motivo);
		handler.disconnect(cartel);
	}
}
