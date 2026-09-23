package pe.sobrinosdepepe.duelos;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Una pelea que se esta armando a mano: quien va de cada lado y quien ya dijo
 * que si.
 *
 * ## Por que una sola cosa y no dos
 *
 * Al principio esto iban a ser dos sistemas: un "grupo" para juntarte con tu
 * companero, y una "mesa" para armar los cuatro lugares de un 2v2. Son lo mismo
 * mirado de dos formas, asi que es uno solo:
 *
 *   - llenas **tu lado** y lo dejas ahi  -> es un grupo, y se anota junto a la cola;
 *   - llenas **los dos lados**           -> es una pelea armada, y arranca sola.
 *
 * ## Arranca SOLA
 *
 * Cuando el ultimo dice que si, la pelea empieza. No hay boton de empezar y eso
 * no es una simplificacion: la primera version lo tenia y estaba roto de raiz.
 * El boton lo veia solo el dueño, y la pantalla del dueño **no se enteraba** de
 * que el otro habia aceptado —son dos menus distintos, cada uno con su copia
 * dibujada— asi que le quedaba "FALTA GENTE" para siempre mientras el invitado
 * veia "ya aceptaste". Los dos mirando una pantalla que no se iba a mover nunca.
 *
 * Arrancando sola, el estado de la pelea vive en un solo lugar —esta clase— y las
 * pantallas pasan a ser lo que tienen que ser: una foto de algo que ya decidio
 * otro.
 */
public final class Mesa {

	/** Cuanto vive una mesa sin completarse. */
	public static final int SEGUNDOS = 180;

	/**
	 * Lo mas alto que se puede poner de apuesta desde la pantalla.
	 *
	 * El boton sube de a diez y no hay donde escribir un numero, asi que sin tope
	 * el unico limite es la paciencia del que clickea. Con mil alcanza para una
	 * apuesta grande —un shard son diez minutos de juego— y evita el 999.999 que
	 * alguien va a intentar el primer dia.
	 */
	public static final int TOPE_APUESTA = 1000;

	/** De a cuanto sube el boton de la apuesta. */
	public static final int PASO_APUESTA = 10;

	/** Cuantos entran de cada lado: 1 es un 1v1, 2 un 2v2. */
	public final int porLado;

	/** Quien la armo. Es el unico que invita, cambia la apuesta y la deshace. */
	public final String dueno;

	/** Los dos lados. `lugares[0]` es el del dueño. Un null es un lugar libre. */
	private final String[][] lugares;

	/** Quienes dijeron que si. El dueño esta desde el momento cero. */
	private final Set<String> aceptaron = new LinkedHashSet<>();

	/**
	 * A quien se invito y cuando, para no dejar que el dueño insista.
	 *
	 * Sin esto, al que dice que no se lo puede reinvitar apretando la misma cabeza
	 * una vez por segundo, y eso es acoso con un cartel en el chat cada vez.
	 */
	private final Map<String, Long> invitadoHace = new HashMap<>();

	/** Cuanto hay que esperar para volver a invitar al mismo. */
	public static final int ESPERA_REINVITAR = 30;

	/** Shards que se juegan los dos lados. Cero es una pelea por el honor. */
	public int apuesta;

	/** El kit pedido, o null para que salga uno al azar (lo normal). */
	public String clase;

	public long vence;

	/** Lo ultimo que se aviso de por que no arranca, para no repetirlo cada tick. */
	private String ultimoProblema;

	public Mesa(String dueno, int porLado) {
		this.dueno = dueno;
		this.porLado = porLado;
		this.lugares = new String[2][porLado];
		this.lugares[0][0] = dueno;
		this.aceptaron.add(clave(dueno));
		refrescar();
	}

	public void refrescar() {
		vence = System.currentTimeMillis() + SEGUNDOS * 1000L;
	}

	public boolean vencio() {
		return System.currentTimeMillis() > vence;
	}

	public boolean esElDueno(String quien) {
		return dueno.equalsIgnoreCase(quien);
	}

	// ------------------------------------------------------------------- lugares

	/** Quien esta en ese lugar, o null si esta libre. */
	public String quienEsta(int lado, int puesto) {
		if (lado < 0 || lado > 1 || puesto < 0 || puesto >= porLado) return null;
		return lugares[lado][puesto];
	}

	/** Cuantos segundos faltan para poder reinvitar a ese, o 0 si ya se puede. */
	public int esperaParaInvitar(String quien) {
		Long cuando = invitadoHace.get(clave(quien));
		if (cuando == null) return 0;
		long faltan = (cuando + ESPERA_REINVITAR * 1000L) - System.currentTimeMillis();
		return faltan <= 0 ? 0 : (int) Math.ceil(faltan / 1000.0);
	}

	/**
	 * Sienta a alguien. Devuelve false si el lugar estaba ocupado, si esa persona
	 * ya esta sentada, o si se la invito hace muy poco.
	 */
	public boolean sentar(int lado, int puesto, String quien) {
		if (lado < 0 || lado > 1 || puesto < 0 || puesto >= porLado) return false;
		if (lugares[lado][puesto] != null) return false;
		if (tiene(quien)) return false;
		if (esperaParaInvitar(quien) > 0) return false;
		lugares[lado][puesto] = quien;
		invitadoHace.put(clave(quien), System.currentTimeMillis());
		refrescar();
		return true;
	}

	/** Lo saca de donde este, y le olvida el si. El dueño no se puede sacar. */
	public boolean levantar(String quien) {
		if (esElDueno(quien)) return false;
		boolean estaba = false;
		for (int lado = 0; lado < 2; lado++) {
			for (int puesto = 0; puesto < porLado; puesto++) {
				if (quien.equalsIgnoreCase(lugares[lado][puesto])) {
					lugares[lado][puesto] = null;
					estaba = true;
				}
			}
		}
		aceptaron.remove(clave(quien));
		return estaba;
	}

	public boolean tiene(String quien) {
		for (int lado = 0; lado < 2; lado++) {
			for (String sentado : lugares[lado]) {
				if (quien.equalsIgnoreCase(sentado)) return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ el si

	public void aceptar(String quien) {
		if (tiene(quien)) aceptaron.add(clave(quien));
	}

	public boolean acepto(String quien) {
		return aceptaron.contains(clave(quien));
	}

	/** Los que estan sentados pero todavia no contestaron. */
	public List<String> losQueFaltan() {
		List<String> faltan = new ArrayList<>();
		for (int lado = 0; lado < 2; lado++) {
			for (String sentado : lugares[lado]) {
				if (sentado != null && !acepto(sentado)) faltan.add(sentado);
			}
		}
		return faltan;
	}

	// ----------------------------------------------------------------- la apuesta

	/**
	 * Sube la apuesta un paso, sin pasar del tope ni de lo que el dueño tiene.
	 *
	 * Que no pueda apostar lo que no tiene se chequea igual al arrancar, pero
	 * frenarlo en el boton es lo que hace que se entere ahora y no despues de
	 * juntar a cuatro personas.
	 */
	public void subirApuesta(int tieneElDueno) {
		int techo = Math.min(TOPE_APUESTA, Math.max(0, tieneElDueno));
		apuesta = apuesta + PASO_APUESTA > techo ? 0 : apuesta + PASO_APUESTA;
	}

	// ----------------------------------------------------------------- el estado

	/** Los lugares del lado del dueño estan todos ocupados y aceptados. */
	public boolean miLadoListo() {
		return ladoListo(0);
	}

	public boolean ladoListo(int lado) {
		for (String sentado : lugares[lado]) {
			if (sentado == null || !acepto(sentado)) return false;
		}
		return true;
	}

	/** Los dos lados llenos y todos aceptaron: la pelea tiene que arrancar. */
	public boolean lista() {
		return ladoListo(0) && ladoListo(1);
	}

	/** Si del otro lado no hay nadie sentado: entonces esto es un grupo. */
	public boolean elOtroLadoVacio() {
		for (String sentado : lugares[1]) {
			if (sentado != null) return false;
		}
		return true;
	}

	public List<String> gente(int lado) {
		List<String> quienes = new ArrayList<>();
		for (String sentado : lugares[lado]) {
			if (sentado != null) quienes.add(sentado);
		}
		return quienes;
	}

	/** Todos los sentados, hayan aceptado o no. */
	public List<String> todos() {
		List<String> quienes = new ArrayList<>(gente(0));
		quienes.addAll(gente(1));
		return quienes;
	}

	/** Los dos lados, para armar el duelo. */
	public Cola.Par lados() {
		return new Cola.Par(Lado.de(gente(0)), Lado.de(gente(1)));
	}

	/**
	 * Si todos los sentados siguen conectados. El que se fue deja su lugar libre.
	 *
	 * Devuelve los nombres de los que se cayeron, para avisarle al dueño.
	 */
	public List<String> sacarLosQueSeFueron(MinecraftServer servidor) {
		List<String> sefueron = new ArrayList<>();
		for (int lado = 0; lado < 2; lado++) {
			for (int puesto = 0; puesto < porLado; puesto++) {
				String sentado = lugares[lado][puesto];
				if (sentado == null || esElDueno(sentado)) continue;
				if (servidor.getPlayerList().getPlayerByName(sentado) == null) {
					lugares[lado][puesto] = null;
					aceptaron.remove(clave(sentado));
					sefueron.add(sentado);
				}
			}
		}
		return sefueron;
	}

	// -------------------------------------------------------------- las pantallas

	/**
	 * Vuelve a dibujar la mesa en la pantalla de todos los que la tengan abierta.
	 *
	 * Cada jugador tiene su propio menu con su propia copia dibujada, asi que
	 * cambiar la mesa no mueve nada en la pantalla de los demas. Esto es lo que
	 * hace que el dueño vea que el otro acepto sin tener que cerrar y abrir.
	 */
	public void refrescarPantallas(MinecraftServer servidor) {
		paraCadaPantalla(servidor, PantallaMesa::redibujar);
	}

	/** Cierra la mesa en la pantalla de todos: se usa cuando la pelea arranca. */
	public void cerrarPantallas(MinecraftServer servidor) {
		paraCadaPantalla(servidor, pantalla -> pantalla.cerrar());
	}

	private void paraCadaPantalla(MinecraftServer servidor,
			java.util.function.Consumer<PantallaMesa> que) {
		for (String nombre : todos()) {
			ServerPlayer quien = servidor.getPlayerList().getPlayerByName(nombre);
			if (quien == null) continue;
			if (quien.containerMenu instanceof PantallaMesa pantalla && pantalla.esDe(this)) {
				que.accept(pantalla);
			}
		}
	}

	// ----------------------------------------------------------------- los avisos

	/**
	 * Avisa por que no arranca, pero **una sola vez** hasta que el motivo cambie.
	 *
	 * Esto se pregunta veinte veces por segundo: sin el filtro, "la arena esta
	 * ocupada" seria mil doscientas lineas de chat por minuto.
	 */
	public boolean hayQueAvisar(String problema) {
		if (problema == null) {
			ultimoProblema = null;
			return false;
		}
		if (problema.equals(ultimoProblema)) return false;
		ultimoProblema = problema;
		return true;
	}

	public String comoSeLlama() {
		return porLado + "v" + porLado;
	}

	private static String clave(String nombre) {
		return nombre.toLowerCase(Locale.ROOT);
	}
}
