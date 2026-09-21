package pe.sobrinosdepepe.duelos;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo unico de los duelos que sobrevive a un reinicio: la arena y los inventarios
 * que todavia no volvieron a su dueño.
 *
 * Los retos, las apuestas, la cola y el torneo **no** se guardan, y es a
 * proposito: son cosas de los proximos minutos y de gente que esta conectada. Si
 * el servidor se reinicia, se vuelve a retar y listo.
 *
 * Los inventarios son otra historia. Mientras dos estan peleando, lo que tenian
 * encima existe solamente adentro de este mod, asi que se escribe al archivo en
 * cuanto empieza el duelo y se borra cuando ya se les devolvio. Si el servidor se
 * cae en el medio de una pelea —que en este servidor pasa por RAM— al arrancar de
 * nuevo esta la copia, y cada uno la recupera al conectarse.
 *
 * El archivo es `config/duelos-de-pepe.json` y se puede abrir y mirar: la arena
 * son seis numeros y dos puntos, y ahi se ve si alguien quedo con el inventario
 * en el aire.
 */
public final class Registro {
	private static final Logger LOG = LoggerFactory.getLogger("duelosdepepe");

	public static final String ARCHIVO = "duelos-de-pepe.json";

	private final Path ruta;
	private MinecraftServer servidor;
	private Arena arena;
	private final Map<String, Guardado> pendientes = new LinkedHashMap<>();

	public Registro() {
		this.ruta = FabricLoader.getInstance().getConfigDir().resolve(ARCHIVO);
	}

	public void alArrancar(MinecraftServer servidor) {
		this.servidor = servidor;
		leer();
		if (arena != null) {
			LOG.info("La arena esta en {} {} {} a {} {} {} ({} bloques)",
					arena.min.getX(), arena.min.getY(), arena.min.getZ(),
					arena.max.getX(), arena.max.getY(), arena.max.getZ(), arena.volumen());
		} else {
			LOG.info("Todavia no hay arena. Se marca con la varita: /pvp arena");
		}
		if (!pendientes.isEmpty()) {
			LOG.warn("Quedaron {} inventarios sin devolver de la ultima vez: {}",
					pendientes.size(), String.join(", ", pendientes.keySet()));
		}
	}

	// ----------------------------------------------------------------------- arena

	public Arena arena() {
		return arena;
	}

	public void ponerArena(Arena arena) {
		this.arena = arena;
		guardar();
	}

	public void borrarArena() {
		this.arena = null;
		guardar();
	}

	// ------------------------------------------------------------- los inventarios

	/**
	 * Guarda la copia y la escribe al archivo antes de tocarle nada al jugador.
	 * Devuelve false si ese jugador YA tenia una copia sin devolver, y en ese caso
	 * no pisa nada.
	 *
	 * Pisar seria la unica forma de que alguien pierda todo de verdad: si por lo
	 * que sea un jugador entrara dos veces a la arena sin que le devolvieran lo
	 * suyo, la segunda copia guardaria el KIT PRESTADO como si fuera su inventario
	 * y lo de verdad se perderia para siempre. Hoy no puede pasar —se pelea de a
	 * una y `Duelos.estaOcupado` lo tapa— pero el precio de equivocarse es tan alto
	 * que el candado va igual, abajo de todo, donde se escribe.
	 */
	public boolean anotar(Guardado copia) {
		String clave = copia.jugador.toLowerCase(java.util.Locale.ROOT);
		if (pendientes.containsKey(clave)) {
			LOG.error("{} ya tenia un inventario guardado sin devolver. NO lo piso: "
					+ "antes de que entre a otro duelo hay que devolverselo.", copia.jugador);
			return false;
		}
		pendientes.put(clave, copia);
		guardar();
		return true;
	}

	/** La copia que le quedo a alguien, o null si no le debemos nada. */
	public Guardado pendienteDe(String jugador) {
		return pendientes.get(jugador.toLowerCase(java.util.Locale.ROOT));
	}

	public void olvidar(String jugador) {
		if (pendientes.remove(jugador.toLowerCase(java.util.Locale.ROOT)) != null) guardar();
	}

	public boolean hayPendientes() {
		return !pendientes.isEmpty();
	}

	// --------------------------------------------------------------------- archivo

	private void leer() {
		if (!Files.isReadable(ruta)) return;
		try {
			JsonObject json = JsonParser.parseString(Files.readString(ruta, StandardCharsets.UTF_8))
					.getAsJsonObject();

			if (json.has("arena") && json.get("arena").isJsonObject()) {
				arena = Arena.deJson(json.getAsJsonObject("arena"));
			}

			if (json.has("pendientes")) {
				for (JsonElement elemento : json.getAsJsonArray("pendientes")) {
					Guardado copia = Guardado.deJson(servidor.registryAccess(),
							elemento.getAsJsonObject());
					pendientes.put(copia.jugador.toLowerCase(java.util.Locale.ROOT), copia);
				}
			}
		} catch (Exception e) {
			// Con el archivo a medio leer no se puede seguir: adentro puede haber el
			// inventario entero de alguien. Se avisa fuerte y NO se pisa, asi se puede
			// mirar a mano antes de que el proximo cambio lo sobreescriba.
			LOG.error("No pude leer " + ruta.toAbsolutePath()
					+ ". Arranco sin arena y sin inventarios pendientes; mira el archivo.", e);
			arena = null;
			pendientes.clear();
		}
	}

	private void guardar() {
		JsonObject json = new JsonObject();
		if (arena != null) json.add("arena", arena.aJson());

		JsonArray lista = new JsonArray();
		List<Guardado> copias = new ArrayList<>(pendientes.values());
		for (Guardado copia : copias) {
			lista.add(copia.aJson(servidor.registryAccess()));
		}
		json.add("pendientes", lista);

		try {
			Files.createDirectories(ruta.getParent());
			// Se escribe al lado y despues se mueve: si el servidor se cae en la
			// mitad, el archivo viejo sigue entero. Y el archivo viejo es el que
			// tiene los inventarios.
			Path temporal = ruta.resolveSibling(ARCHIVO + ".nuevo");
			Files.writeString(temporal,
					new GsonBuilder().setPrettyPrinting().create().toJson(json),
					StandardCharsets.UTF_8);
			Files.move(temporal, ruta, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			LOG.error("No pude escribir " + ruta.toAbsolutePath(), e);
		}
	}
}
