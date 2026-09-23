package pe.sobrinosdepepe.lobby;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Con que vuelve cada uno al Survival: donde estaba parado y todo lo que tenia.
 *
 * Existe porque el lobby le pisa dos cosas al jugador. Minecraft guarda una sola
 * posicion por jugador —la de cuando se desconecto— asi que si lo mandamos al
 * lobby sin anotar antes donde estaba, el Survival arrancaria siempre en el
 * spawn. Y el inventario es uno solo: para que el lobby este vacio hay que
 * guardarle el suyo y devolverselo despues.
 *
 * Se escribe en disco en cada anotacion y **antes** de tocarle el inventario, no
 * al apagar: el servidor se cae solo por RAM cada tanto, y lo que este en memoria
 * y no en el archivo se pierde. Perder esto es perderle el inventario a alguien.
 *
 * La clave es el nombre y no el UUID a proposito: el servidor es offline-mode, el
 * UUID sale del nombre, y asi el archivo se puede abrir y entender.
 */
final class Vuelta {

	private static final String ARCHIVO = "lobby-de-pepe.json";
	private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path ruta;
	private final Logger log;
	private final Map<String, Guardado> guardados = new HashMap<>();
	private MinecraftServer servidor;

	Vuelta(Logger log) {
		this.log = log;
		this.ruta = FabricLoader.getInstance().getConfigDir().resolve(ARCHIVO);
	}

	/**
	 * Anota como esta el jugador ahora mismo y lo deja escrito.
	 *
	 * Devuelve false si no se pudo escribir. Quien llame a esto NO le tiene que
	 * tocar el inventario si dio false: mejor que el lobby quede feo con las cosas
	 * puestas a que alguien se quede sin ellas.
	 *
	 * **Nunca pisa una copia que ya existe**, y ese es el seguro contra la unica
	 * forma que tiene este mod de hacer daño de verdad. Una copia sin devolver
	 * significa que el jugador todavia no recupero lo suyo, asi que lo que tenga
	 * encima en este momento no son sus cosas —es la perla del lobby, o un kit
	 * prestado del coliseo— y anotarlo seria cambiarle su equipo por eso. Si esto
	 * salta, el orden de `alEntrar` se rompio en alguna parte y hay que arreglar
	 * eso, no sacar el seguro.
	 */
	boolean anotar(ServerPlayer quien) {
		String nombre = quien.getName().getString();
		if (guardados.containsKey(nombre)) {
			log.error("{} ya tiene cosas guardadas sin devolver: NO las piso. "
					+ "Se le devuelven con /survival.", nombre);
			return false;
		}
		guardados.put(nombre, Guardado.de(quien));
		return escribir();
	}

	/** Con que volver, o null si no hay nada anotado. */
	Guardado de(ServerPlayer quien) {
		return guardados.get(quien.getName().getString());
	}

	/** Se llama cuando el jugador ya recupero lo suyo. */
	void borrar(ServerPlayer quien) {
		if (guardados.remove(quien.getName().getString()) != null) {
			escribir();
		}
	}

	void alArrancar(MinecraftServer servidor) {
		this.servidor = servidor;
		if (!Files.isReadable(ruta)) {
			log.info("Todavia no hay {}: se escribe cuando entre el primero.",
					ruta.toAbsolutePath());
			return;
		}
		try {
			JsonObject json = JsonParser.parseString(
					Files.readString(ruta, StandardCharsets.UTF_8)).getAsJsonObject();
			for (String nombre : json.keySet()) {
				guardados.put(nombre, Guardado.deJson(servidor.registryAccess(),
						json.getAsJsonObject(nombre)));
			}
			log.info("{} jugador(es) con cosas guardadas, de {}", guardados.size(),
					ruta.toAbsolutePath());
		} catch (Exception error) {
			// No se corta el arranque, pero esto hay que verlo: significa que alguien
			// puede estar en el lobby con su inventario adentro de este archivo.
			log.error("NO PUDE LEER {}. Si hay alguien en el lobby, sus cosas estan ahi "
					+ "adentro y no las toques.", ruta, error);
		}
	}

	private boolean escribir() {
		if (servidor == null) {
			log.error("Me pidieron guardar antes de que arrancara el servidor.");
			return false;
		}
		JsonObject json = new JsonObject();
		guardados.forEach((nombre, guardado) ->
				json.add(nombre, guardado.aJson(servidor.registryAccess())));
		try {
			Files.createDirectories(ruta.getParent());
			Files.writeString(ruta, GSON.toJson(json), StandardCharsets.UTF_8);
			return true;
		} catch (IOException error) {
			log.error("No pude escribir {}", ruta, error);
			return false;
		}
	}
}
