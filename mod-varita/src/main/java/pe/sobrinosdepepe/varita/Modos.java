package pe.sobrinosdepepe.varita;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * El modo que puede tener una zona protegida, y cuales lo tienen puesto.
 *
 * Safe Zone no tiene nada de esto: una zona suya es siempre lo mismo para todas,
 * y el archivo de zonas no tiene donde guardar una opcion. Asi que el modo vive
 * aca, en `config/zonas-de-pepe.json`, y la zona se nombra por su `claimId`, que
 * es lo unico de una zona que no cambia cuando se la agranda o se le cambia el
 * dueño.
 *
 * Por ahora es uno solo, **sin bichos**: adentro no hay hostiles. Ni zombis, ni
 * esqueletos, ni creepers, ni phantoms. Es para el spawn y para el coliseo.
 *
 * Hubo un segundo modo, `construir`, que abria la zona para que pudiera poner
 * bloques cualquiera. Duro una tarde: adentro del coliseo eso dejaba que el de
 * las gradas picara la estructura entre pelea y pelea. Lo que hacia falta no era
 * abrir la zona sino abrirsela **a los dos que estan peleando y adentro de la
 * arena**, y eso lo sabe el mod de duelos y no este. Vive en
 * `duelos/mixin/ClaimManagerMixin`.
 */
public final class Modos {
	private static final Logger LOG = LoggerFactory.getLogger("varitadepepe");

	public static final String ARCHIVO = "zonas-de-pepe.json";

	private static final Set<String> sinBichos = new LinkedHashSet<>();
	private static MinecraftServer servidor;

	private Modos() {}

	// ------------------------------------------------------------------ consultas

	public static boolean sinBichos(String id) {
		return sinBichos.contains(clave(id));
	}

	/** Las zonas que tienen el modo puesto. Para el listado del comando. */
	public static Set<String> conAlgoPuesto() {
		return Set.copyOf(sinBichos);
	}

	/**
	 * Si en ese bloque no tendria que haber bichos. Lo pregunta el enganche del
	 * que aparece, o sea una vez por bicho que nace en el mundo entero: por eso
	 * sale de una cuando no hay ninguna zona con el modo puesto.
	 */
	public static boolean sinBichos(MinecraftServer servidor, int x, int z) {
		if (servidor == null || sinBichos.isEmpty()) return false;
		for (Zonas.Zona zona : Zonas.todas(servidor)) {
			if (zona.contiene(x, z)) return sinBichos(zona.id());
		}
		return false;
	}

	// -------------------------------------------------------------------- cambios

	/** Alterna el modo y devuelve como quedo. */
	public static boolean alternarBichos(String id) {
		return alternar(sinBichos, id);
	}

	private static boolean alternar(Set<String> cual, String id) {
		String clave = clave(id);
		boolean queda = cual.contains(clave) ? !cual.remove(clave) : cual.add(clave);
		guardar();
		return queda;
	}

	// ---------------------------------------------------------------- el archivo

	public static void alArrancar(MinecraftServer servidor) {
		Modos.servidor = servidor;
		leer();
		LOG.info("Modos de zona: {} sin bichos", sinBichos.size());
	}

	private static Path ruta() {
		return FabricLoader.getInstance().getConfigDir().resolve(ARCHIVO);
	}

	private static String clave(String id) {
		return id.toLowerCase(Locale.ROOT);
	}

	private static void leer() {
		Path ruta = ruta();
		if (!Files.isReadable(ruta)) {
			LOG.info("Todavia no hay {}: el primer comando lo escribe.", ruta.toAbsolutePath());
			return;
		}
		try {
			JsonObject json = JsonParser.parseString(Files.readString(ruta, StandardCharsets.UTF_8))
					.getAsJsonObject();
			cargar(json, "sinBichos", sinBichos);
		} catch (Exception e) {
			// Arrancar sin modos deja las zonas como las deja Safe Zone, que es el
			// lado seguro: protegidas. Se avisa fuerte y no se pisa el archivo.
			LOG.error("No pude leer " + ruta.toAbsolutePath() + ". Arranco sin modos.", e);
			sinBichos.clear();
		}
	}

	private static void cargar(JsonObject json, String nombre, Set<String> adonde) {
		adonde.clear();
		if (!json.has(nombre)) return;
		for (var elemento : json.getAsJsonArray(nombre)) adonde.add(clave(elemento.getAsString()));
	}

	private static void guardar() {
		JsonObject json = new JsonObject();
		json.add("sinBichos", comoLista(sinBichos));

		Path ruta = ruta();
		try {
			Files.createDirectories(ruta.getParent());
			// Se escribe al lado y despues se mueve: si el servidor se cae en la
			// mitad, el archivo viejo sigue entero.
			Path temporal = ruta.resolveSibling(ARCHIVO + ".nuevo");
			Files.writeString(temporal, new GsonBuilder().setPrettyPrinting().create().toJson(json),
					StandardCharsets.UTF_8);
			Files.move(temporal, ruta, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			LOG.error("No pude escribir " + ruta.toAbsolutePath(), e);
		}
	}

	private static JsonArray comoLista(Set<String> cual) {
		JsonArray lista = new JsonArray();
		cual.forEach(lista::add);
		return lista;
	}
}
