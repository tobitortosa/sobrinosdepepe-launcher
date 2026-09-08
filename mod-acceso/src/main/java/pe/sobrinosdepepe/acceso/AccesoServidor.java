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
		ConfigAcceso alArrancar = ConfigAcceso.leer(LOG);
		LOG.info("Solo con el launcher: {}", alArrancar.exigir() ? "SI, el candado esta puesto" : "no, el candado esta abierto");

		// El pedido va SIEMPRE: con el candado abierto tambien, porque es lo que nos
		// deja saber quien esta jugando sin el launcher sin echar a nadie. Un cliente
		// que no conoce el canal se delata contestando "no entendi".
		ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, sync) ->
				sender.sendPacket(Canal.ID, FriendlyByteBufs.create()));

		ServerLoginNetworking.registerGlobalReceiver(Canal.ID,
				(server, handler, entendido, respuesta, sync, sender) -> {
					// La config se lee de nuevo en cada intento y no una sola vez al
					// arrancar: asi prender o apagar el candado es subir el archivo, sin
					// reiniciar el servidor y sin echar a los que estan jugando. Es un
					// archivo de cien bytes y los logins son unos pocos por dia.
					ConfigAcceso config = ConfigAcceso.leer(null);
					String nombre = nombreDelLogin(handler);
					Veredicto veredicto = revisar(config, entendido, respuesta, nombre);

					if (veredicto.pasa()) {
						LOG.info("{} entra con el launcher", nombre);
						return;
					}

					if (!config.exigir()) {
						LOG.warn("{} entra SIN el launcher ({}). El candado esta abierto: exigir=false en {}",
								nombre, veredicto.motivo(), ConfigAcceso.ARCHIVO);
						return;
					}

					// Ojo: se echa con el handler y NO con el sender que llega aca. El
					// disconnect() del sender de Fabric cierra el socket y nada mas, asi
					// que el jugador ve "Desconectado" pelado y nunca se entera de que
					// tiene que bajarse el launcher. El del handler manda primero el
					// paquete con el cartel, que es todo el punto de esto.
					LOG.info("No dejo entrar a {}: {}", nombre, veredicto.motivo());
					handler.disconnect(veredicto.cartel());
				});
	}

	/**
	 * Si entra o no, con que cartel y por que. Revisar y actuar estan separados
	 * porque con el candado abierto se hace la misma revision y en lugar de echar a
	 * nadie se escribe en el log quien entro sin el launcher.
	 */
	private record Veredicto(boolean pasa, Component cartel, String motivo) {}

	private static Veredicto revisar(
			ConfigAcceso config, boolean entendido, FriendlyByteBuf respuesta, String nombre) {
		if (!config.configurado()) {
			return new Veredicto(false, Carteles.sinConfigurar(), "al servidor le falta " + ConfigAcceso.ARCHIVO);
		}

		if (!entendido) {
			return new Veredicto(false, Carteles.sinLauncher(config.link()), "cliente sin el mod");
		}

		Ticket.Resultado resultado =
				Ticket.verificar(config.secreto(), leerTicket(respuesta), System.currentTimeMillis() / 1000);

		return switch (resultado.estado()) {
			case VACIO -> new Veredicto(false, Carteles.sinTicket(config.link()), "sin permiso de entrada");
			case FORMATO, FIRMA ->
					new Veredicto(false, Carteles.invalido(config.link()), "permiso que no verifica");
			case VENCIDO -> new Veredicto(false, Carteles.vencido(config.link()), "permiso vencido");
			case OK -> resultado.nombre().equals(nombre)
					? new Veredicto(true, null, "con el launcher")
					: new Veredicto(false, Carteles.invalido(config.link()),
							"permiso emitido para " + resultado.nombre());
		};
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

}
