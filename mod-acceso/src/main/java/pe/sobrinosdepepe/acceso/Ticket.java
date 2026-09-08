package pe.sobrinosdepepe.acceso;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * El permiso de entrada que el launcher le pasa al juego y el juego le manda al
 * servidor. Lo firma el backend con un secreto que tambien tiene el servidor, y
 * dura unas horas: si alguien abre Minecraft sin pasar por el launcher no tiene
 * ninguno, y por eso no entra.
 *
 * El formato es una linea sola, sin JSON y sin base64, para poder leerlo de un
 * log cuando algo no anda:
 *
 *     1:PEPE:1757260000:a3f1...  ->  version : nombre : vence : firma
 *
 * La firma es HMAC-SHA256 de "1:PEPE:1757260000" con el secreto compartido, en
 * hexadecimal. El nombre va adentro de la firma a proposito: asi el ticket de
 * uno no le sirve a otro, que es justo la forma facil de saltear esto (te paso
 * mi ticket y entras con TLauncher).
 *
 * Lo que este esquema NO resuelve: el duenio del ticket puede usar el suyo para
 * entrar con su propio nombre desde otro launcher, si se toma el trabajo de
 * copiar el mod y la variable de entorno. Cerrar eso pide que el servidor le
 * pregunte al backend por cada login, y no vale la pena.
 */
public final class Ticket {
	/** La version del formato. Si algun dia cambia, el servidor viejo rechaza el nuevo. */
	public static final String VERSION = "1";

	public enum Estado {
		/** Firma correcta y todavia vigente. */
		OK,
		/** El cliente no mando nada: abrio el juego sin el launcher. */
		VACIO,
		/** No tiene la forma de un ticket. */
		FORMATO,
		/** La firma no cierra: no lo emitio nuestro backend, o el secreto no coincide. */
		FIRMA,
		/** Era valido pero ya paso su hora. */
		VENCIDO,
	}

	/** El nombre viene solo cuando la firma cerro; antes de eso no se puede creer. */
	public record Resultado(Estado estado, String nombre) {}

	private Ticket() {}

	public static Resultado verificar(String secreto, String ticket, long ahoraEnSegundos) {
		if (ticket == null || ticket.isBlank()) return new Resultado(Estado.VACIO, null);

		String[] partes = ticket.split(":");
		if (partes.length != 4 || !VERSION.equals(partes[0])) return new Resultado(Estado.FORMATO, null);

		String cuerpo = partes[0] + ":" + partes[1] + ":" + partes[2];
		if (!firmaCoincide(secreto, cuerpo, partes[3])) return new Resultado(Estado.FIRMA, null);

		long vence;
		try {
			vence = Long.parseLong(partes[2]);
		} catch (NumberFormatException e) {
			return new Resultado(Estado.FORMATO, null);
		}

		if (vence <= ahoraEnSegundos) return new Resultado(Estado.VENCIDO, partes[1]);

		return new Resultado(Estado.OK, partes[1]);
	}

	/**
	 * Se compara con MessageDigest.isEqual y no con equals para que el tiempo que
	 * tarda no dependa de cuantos caracteres acerto. Es la forma estandar de
	 * comparar firmas y no cuesta nada.
	 */
	private static boolean firmaCoincide(String secreto, String cuerpo, String firma) {
		byte[] esperada = hmac(secreto, cuerpo);
		byte[] recibida = deHex(firma);
		return recibida != null && MessageDigest.isEqual(esperada, recibida);
	}

	private static byte[] hmac(String secreto, String cuerpo) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			// HmacSHA256 esta en todas las JVM: si esto falla, algo mucho peor pasa.
			throw new IllegalStateException("No pude calcular el HMAC", e);
		}
	}

	private static byte[] deHex(String texto) {
		int largo = texto.length();
		if (largo == 0 || largo % 2 != 0) return null;

		byte[] salida = new byte[largo / 2];
		for (int i = 0; i < largo; i += 2) {
			int alto = Character.digit(texto.charAt(i), 16);
			int bajo = Character.digit(texto.charAt(i + 1), 16);
			if (alto < 0 || bajo < 0) return null;
			salida[i / 2] = (byte) ((alto << 4) | bajo);
		}
		return salida;
	}
}
