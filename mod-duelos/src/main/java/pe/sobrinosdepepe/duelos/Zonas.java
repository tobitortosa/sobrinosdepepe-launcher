package pe.sobrinosdepepe.duelos;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
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
 * Existe por una razon practica: marcar la arena con la varita **ya se sabe
 * hacer**. Es el click derecho de siempre, el mismo con el que se protege una
 * base, y lo hace todo el mundo sin pensar. Pedirle a alguien que aprenda un
 * gesto nuevo —el click izquierdo, que es el que usa este mod— para hacer lo
 * mismo es pedirle que se acuerde de cual de los dos botones era, y la primera
 * vez que se equivoca no pasa nada visible: marca una zona y el comando de la
 * arena le dice que le falta una esquina.
 *
 * Asi que `/pvp arena guardar` tambien agarra la zona en la que estas parado.
 *
 * Se lee el archivo de Safe Zone y no se le pregunta al mod, porque no expone
 * ninguna API. Es `world/safe-zone/claims.json`, una lista de objetos con el id,
 * el dueño y las dos esquinas. Safe Zone lo escribe **en el momento** en que se
 * crea la zona —medido: una zona creada a las 13:44 ya estaba en el archivo—, asi
 * que no hace falta reiniciar ni recargar nada.
 *
 * Si algun dia Safe Zone cambia el formato, esto deja de encontrar zonas y avisa;
 * la varita sigue andando igual, que es el otro camino.
 */
public final class Zonas {
	private static final Logger LOG = LoggerFactory.getLogger("duelosdepepe");

	private static final String ARCHIVO = "safe-zone/claims.json";

	/**
	 * Una zona. Las alturas son las de los bloques que se clickearon y nada mas:
	 * la proteccion de Safe Zone **no mira la altura**, protege la columna entera.
	 * Por eso una zona marcada de un solo click de altura es normal y por eso la
	 * arena se arma con el alto que pide el que la guarda.
	 */
	public record Zona(String id, String dueno,
			int x1, int y1, int z1, int x2, int y2, int z2) {

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

		public int piso() {
			return Math.min(y1, y2);
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

		/** Si se pisa con la caja de una arena, mirando solo X y Z. */
		public boolean pisa(Arena arena) {
			return minX() <= arena.max.getX() && maxX() >= arena.min.getX()
					&& minZ() <= arena.max.getZ() && maxZ() >= arena.min.getZ();
		}

		/**
		 * Las dos esquinas de la caja de la arena que sale de esta zona: el
		 * rectangulo tal cual, un bloque mas abajo del piso que se clickeo —para que
		 * el piso entre y se pueda devolver— y el alto que se pida.
		 */
		public BlockPos[] esquinas(int alto) {
			return new BlockPos[] {
					new BlockPos(minX(), piso() - 1, minZ()),
					new BlockPos(maxX(), piso() - 1 + alto, maxZ()),
			};
		}
	}

	private Zonas() {}

	public static List<Zona> todas(MinecraftServer servidor) {
		Path ruta = servidor.getWorldPath(LevelResource.ROOT).resolve(ARCHIVO);
		if (!Files.isReadable(ruta)) return List.of();

		List<Zona> zonas = new ArrayList<>();
		try {
			JsonArray lista = JsonParser.parseString(Files.readString(ruta, StandardCharsets.UTF_8))
					.getAsJsonArray();
			for (var elemento : lista) {
				JsonObject o = elemento.getAsJsonObject();
				zonas.add(new Zona(
						o.get("claimId").getAsString(),
						o.has("ownerName") ? o.get("ownerName").getAsString() : "?",
						o.get("x1").getAsInt(), o.get("y1").getAsInt(), o.get("z1").getAsInt(),
						o.get("x2").getAsInt(), o.get("y2").getAsInt(), o.get("z2").getAsInt()));
			}
		} catch (Exception e) {
			// Sin zonas se puede marcar la arena igual, con la varita. Se avisa en el
			// log y se sigue: que Safe Zone cambie su archivo no puede romper el /pvp.
			LOG.warn("No pude leer " + ruta.toAbsolutePath()
					+ ". La arena se puede marcar igual con la varita.", e);
			return List.of();
		}
		return zonas;
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

	/** Los ids de las zonas que se pisan con la arena. Para el aviso. */
	public static List<String> lasQuePisan(MinecraftServer servidor, Arena arena) {
		List<String> ids = new ArrayList<>();
		for (Zona zona : todas(servidor)) {
			if (zona.pisa(arena)) ids.add(zona.id());
		}
		return ids;
	}

	public static List<String> ids(MinecraftServer servidor) {
		return todas(servidor).stream().map(Zona::id).toList();
	}
}
