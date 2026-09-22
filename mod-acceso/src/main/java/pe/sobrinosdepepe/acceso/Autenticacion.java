package pe.sobrinosdepepe.acceso;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

/**
 * El que entra con el launcher no escribe ni {@code /register} ni {@code /login}.
 *
 * Desde que la puerta esta abierta, el servidor pide contrasena adentro del juego
 * (EasyAuth), porque es offline-mode y si no cualquiera entra con el nombre de
 * cualquiera. Para el que viene por el launcher eso es pedirle dos veces lo mismo:
 * ya tiene una cuenta con contrasena en el backend, ya la uso para entrar, y el
 * ticket que trae esta firmado con el nombre adentro. Su identidad ya esta probada
 * —mejor que con una contrasena escrita en el chat, que la ve cualquiera que este
 * grabando—, asi que el servidor lo da por autenticado y listo.
 *
 * Como se hace: EasyAuth le agrega a cada jugador, por mixin, los metodos de su
 * interfaz {@code PlayerAuth}. Se los llama por reflexion a proposito, para no
 * atarnos a su jar: si EasyAuth no esta puesto, los metodos no existen, esto no
 * hace nada y todo sigue funcionando igual.
 *
 * Son dos llamadas y no una:
 *
 *   setSkipAuth       lo deja afuera de la revision para siempre, asi tampoco le
 *                     pide nada al salir ni al volver.
 *   setAuthenticated  lo desbloquea ahora, porque para cuando llegamos aca EasyAuth
 *                     ya lo congelo.
 *
 * Y se hacen dos veces, una al entrar y otra en el tick siguiente, porque el orden
 * entre dos mods que escuchan el mismo evento no lo decide nadie: si EasyAuth
 * corriera despues que nosotros, la primera pasada se perderia.
 */
public final class Autenticacion {
	/**
	 * Cuanto vale el ticket ya verificado, desde el login hasta que el jugador
	 * aparece en el mundo. Son dos cosas seguidas y tardan menos de un segundo; el
	 * minuto es para una conexion mala. Vencido no pasa nada malo: al jugador le
	 * van a pedir la contrasena como a cualquiera.
	 */
	private static final long VALE_MS = 60_000;

	/** Los que pasaron la revision del ticket y todavia no aparecieron en el mundo. */
	private static final Map<String, Long> ESPERANDO = new ConcurrentHashMap<>();

	private static volatile Puente puente;
	private static volatile boolean buscado;

	/** {@code saltear} puede ser null: es el que ahorra la revision, no el que desbloquea. */
	private record Puente(Method saltear, Method autenticar) {}

	private Autenticacion() {}

	/** Lo llama el login cuando el ticket verifico. */
	static void anotar(String nombre) {
		long ahora = System.currentTimeMillis();
		ESPERANDO.entrySet().removeIf(e -> ahora - e.getValue() > VALE_MS);
		ESPERANDO.put(nombre, ahora);
	}

	/** Lo llama el evento de entrada, cuando el jugador ya esta en el mundo. */
	static void alEntrar(ServerPlayer jugador, MinecraftServer server, Logger log) {
		Long cuando = ESPERANDO.remove(jugador.getName().getString());
		if (cuando == null || System.currentTimeMillis() - cuando > VALE_MS) return;

		if (!marcar(jugador, log)) return;
		server.execute(() -> marcar(jugador, log));
		jugador.sendSystemMessage(Carteles.sinContrasena());
	}

	private static boolean marcar(ServerPlayer jugador, Logger log) {
		Puente p = puente(jugador.getClass(), log);
		if (p == null) return false;

		try {
			if (p.saltear() != null) p.saltear().invoke(jugador);
			p.autenticar().invoke(jugador, true);
			return true;
		} catch (ReflectiveOperationException e) {
			log.warn("No pude darle entrada libre a {}: {}", jugador.getName().getString(), e.toString());
			return false;
		}
	}

	/**
	 * Busca los dos metodos una sola vez. Si no estan es que EasyAuth no esta
	 * puesto, y entonces no hay nada que saltear.
	 *
	 * Se busca por el final del nombre y no por el nombre entero: Mixin le puede
	 * cambiar el nombre a los miembros que inyecta, poniendole adelante un prefijo
	 * propio para que dos mods no se pisen. En el log del servidor se ve pasar uno
	 * asi: modifyReturnValue$zdb000$easyauth$easyAuth$isInvisible.
	 *
	 * Y son dos firmas distintas, que es lo que costo los dos primeros intentos:
	 * {@code setAuthenticated} recibe un boolean, pero {@code setSkipAuth} **no
	 * recibe nada** —llamarlo ya es prenderlo—. Buscar los dos con un boolean no
	 * encontraba el segundo, y sin los dos no habia puente.
	 */
	private static Puente puente(Class<?> clase, Logger log) {
		if (buscado) return puente;
		buscado = true;

		Method saltear = buscar(clase, "setSkipAuth", 0);
		Method autenticar = buscar(clase, "setAuthenticated", 1);

		if (autenticar != null) {
			puente = new Puente(saltear, autenticar);
			log.info("EasyAuth esta puesto ({}): al que entre con el launcher no se le pide contrasena",
					autenticar.getName());
		} else {
			// Si no aparecieron, que quede escrito que hay de verdad del otro lado:
			// buscar a ciegas dos veces ya costo un reinicio cada una.
			StringBuilder candidatos = new StringBuilder();
			for (Method metodo : clase.getMethods()) {
				if (metodo.getName().toLowerCase(java.util.Locale.ROOT).contains("auth")) {
					candidatos.append(' ').append(metodo.getName())
							.append('/').append(metodo.getParameterCount());
				}
			}
			log.info("EasyAuth no esta puesto: nadie se saltea la contrasena."
					+ " clase={} metodos con auth:{}", clase.getName(),
					candidatos.isEmpty() ? " ninguno" : candidatos);
		}

		return puente;
	}

	/** El primer metodo publico que termine asi y reciba esa cantidad de argumentos. */
	private static Method buscar(Class<?> clase, String final_, int cuantos) {
		for (Method metodo : clase.getMethods()) {
			if (metodo.getParameterCount() == cuantos && metodo.getName().endsWith(final_)) {
				return metodo;
			}
		}
		return null;
	}
}
