package pe.sobrinosdepepe.varita;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Las zonas protegidas de Safe Zone, leidas de su archivo.
 *
 * Es el mismo camino que usa el mod de duelos y por el mismo motivo: Safe Zone
 * no expone ninguna API, asi que la unica forma de saber donde estan las zonas
 * es leer `world/safe-zone/claims.json`, que es la lista de objetos con el id, el
 * dueño y las dos esquinas. Safe Zone lo escribe en el momento en que se crea o
 * se borra una zona, asi que no hace falta reiniciar ni recargar nada.
 *
 * **La proteccion de Safe Zone no mira la altura**: `ClaimData.contains()`
 * compara solo X y Z, o sea que una zona es la columna entera de la roca madre
 * al cielo. Los `y1`/`y2` del archivo son el registro de las esquinas que se
 * clickearon y nada mas. Por eso aca tampoco se mira la altura en ningun lado.
 *
 * El archivo se relee cuando cambia y no en cada consulta: el barrido de bichos
 * pregunta una vez por segundo y leer un archivo tantas veces seria una tonteria.
 * La fecha de modificacion alcanza para darse cuenta: la escribe el sistema de
 * archivos y no hay que confiar en que Safe Zone avise nada.
 */
public final class Zonas {
	private static final Logger LOG = LoggerFactory.getLogger("varitadepepe");

	private static final String ARCHIVO = "safe-zone/claims.json";

	/**
	 * Una zona. Las esquinas vienen como las clickeo el que la hizo, asi que x1
	 * puede ser mayor que x2: por eso todo pasa por minX/maxX y no por x1/x2.
	 */
	public record Zona(String id, String dueno, int x1, int z1, int x2, int z2) {

		public int minX() {
			return Math.min(x1, x2);
		}

		public int maxX() {
			return Math.max(x1, x2);
		}

		public int minZ() {
			return Math.min(z1, z2);
		}

		public int maxZ() {
			return Math.max(z1, z2);
		}

		public int ancho() {
			return maxX() - minX() + 1;
		}

		public int largo() {
			return maxZ() - minZ() + 1;
		}

		/** Si ese punto cae adentro del rectangulo, sin mirar la altura. */
		public boolean contiene(double x, double z) {
			return x >= minX() && x < maxX() + 1 && z >= minZ() && z < maxZ() + 1;
		}

		/** Lo mismo para un bloque, que es lo que pregunta el mixin. */
		public boolean contiene(int x, int z) {
			return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
		}
	}

	private Zonas() {}

	private static List<Zona> cache = List.of();
	private static long cuandoSeEscribio = -1;
	private static long cuandoMire;
	private static Path ruta;

	/**
	 * Cada cuanto se le pregunta al disco si el archivo cambio, en milisegundos.
	 *
	 * No es una optimizacion de mas. Por aca pasa el mixin de las zonas donde se
	 * construye, y ese corre adentro de `ClaimManager.getClaimAt`, que Safe Zone
	 * llama **por cada bloque** que toca una explosion. Sin el freno, una TNT
	 * serian cientos de consultas al sistema de archivos en un tick.
	 */
	private static final long CADA_CUANTO_MIRO = 1000;

	/**
	 * Todas las zonas, releyendo el archivo solo si cambio desde la ultima vez.
	 *
	 * Si el archivo no se puede leer se devuelve lo ultimo que si se pudo, y no
	 * una lista vacia: una lista vacia diria "no hay ninguna zona protegida", que
	 * es exactamente lo contrario de lo que conviene suponer cuando no se sabe.
	 */
	public static List<Zona> todas(MinecraftServer servidor) {
		// El freno va primero que nada: `isReadable` tambien le pregunta al disco.
		long ahora = System.currentTimeMillis();
		if (ahora - cuandoMire < CADA_CUANTO_MIRO) return cache;
		cuandoMire = ahora;

		if (ruta == null) ruta = servidor.getWorldPath(LevelResource.ROOT).resolve(ARCHIVO);
		if (!Files.isReadable(ruta)) {
			cache = List.of();
			cuandoSeEscribio = -1;
			return cache;
		}

		try {
			long escrito = Files.getLastModifiedTime(ruta).toMillis();
			if (escrito == cuandoSeEscribio) return cache;
			cuandoSeEscribio = escrito;
			cache = leer();
		} catch (Exception e) {
			LOG.warn("No pude leer " + ruta.toAbsolutePath() + ". Sigo con lo ultimo que lei.", e);
		}
		return cache;
	}

	private static List<Zona> leer() throws Exception {
		List<Zona> zonas = new ArrayList<>();
		JsonArray lista = JsonParser.parseString(Files.readString(ruta, StandardCharsets.UTF_8))
				.getAsJsonArray();
		for (var elemento : lista) {
			JsonObject o = elemento.getAsJsonObject();
			zonas.add(new Zona(
					o.get("claimId").getAsString(),
					o.has("ownerName") ? o.get("ownerName").getAsString() : "?",
					o.get("x1").getAsInt(), o.get("z1").getAsInt(),
					o.get("x2").getAsInt(), o.get("z2").getAsInt()));
		}
		return List.copyOf(zonas);
	}

	/** La zona en la que esta parado, o null. */
	public static Zona dondeEsta(MinecraftServer servidor, ServerPlayer quien) {
		for (Zona zona : todas(servidor)) {
			if (zona.contiene(quien.getX(), quien.getZ())) return zona;
		}
		return null;
	}

	public static Zona porId(MinecraftServer servidor, String id) {
		for (Zona zona : todas(servidor)) {
			if (zona.id().equalsIgnoreCase(id)) return zona;
		}
		return null;
	}
}
