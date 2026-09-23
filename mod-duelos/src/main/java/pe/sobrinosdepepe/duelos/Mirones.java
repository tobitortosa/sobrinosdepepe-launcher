package pe.sobrinosdepepe.duelos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Los que miran la pelea: adonde vuelven y en que modo estaban.
 *
 * El que aprieta VER LA PELEA cae **en modo espectador** sobre el centro de la
 * arena, volando y atravesando paredes. Es mejor que las gradas a secas y por tres
 * razones: no puede pegar, no le pueden pegar y no puede tocar un bloque, asi que
 * no hay forma de que meta la mano en una pelea ajena; puede mirar desde donde
 * quiera; y no hay que reservarle lugar a nadie en la tribuna.
 *
 * Lo unico que hay que cuidar es devolverlo. **Nadie puede quedarse en espectador
 * para siempre**, asi que su lugar y su modo de juego se escriben en
 * `config/mirones-de-pepe.json` apenas empieza a mirar, y vuelven:
 *
 *   - cuando termina la pelea,
 *   - cuando aprieta VOLVER (o escribe `/pvp volver`),
 *   - cuando se desconecta —antes de que el servidor guarde su archivo, porque el
 *     coliseo es una dimension propia y **nadie puede quedar guardado adentro**:
 *     el que se conecta dentro de una dimension custom se queda con el mapa vacio,
 *   - y al entrar, si el servidor se cayo mientras miraba.
 *
 * El que mira **no se convierte en espectador si estaba peleando**: eso lo maneja
 * el duelo, que tiene su propia copia de todo.
 */
final class Mirones {

	private static final Logger LOG = LoggerFactory.getLogger("duelosdepepe");
	private static final String ARCHIVO = "mirones-de-pepe.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Donde estaba y como estaba antes de ponerse a mirar. */
	record Sitio(String mundo, double x, double y, double z, float giro, float mira, String modo) {

		ResourceKey<Level> donde() {
			return ResourceKey.create(Registries.DIMENSION, Identifier.parse(mundo));
		}

		GameType comoEstaba() {
			try {
				return GameType.valueOf(modo);
			} catch (IllegalArgumentException error) {
				return GameType.SURVIVAL;
			}
		}
	}

	private final Path ruta;
	private final Map<String, Sitio> sitios = new HashMap<>();
	private MinecraftServer servidor;

	Mirones() {
		this.ruta = FabricLoader.getInstance().getConfigDir().resolve(ARCHIVO);
	}

	void alArrancar(MinecraftServer servidor) {
		this.servidor = servidor;
		if (!Files.isReadable(ruta)) return;
		try {
			JsonObject json = JsonParser.parseString(
					Files.readString(ruta, StandardCharsets.UTF_8)).getAsJsonObject();
			for (String nombre : json.keySet()) {
				sitios.put(nombre, GSON.fromJson(json.get(nombre), Sitio.class));
			}
			if (!sitios.isEmpty()) {
				LOG.info("{} mirón(es) sin devolver de la vez pasada: vuelven al entrar",
						sitios.size());
			}
		} catch (Exception error) {
			LOG.error("No pude leer {}. Si alguien quedo de espectador, sacalo con /gamemode.",
					ruta, error);
		}
	}

	/**
	 * Lo pone a mirar. Devuelve false si no se pudo anotar donde estaba, y en ese
	 * caso **no se lo toca**: mejor que no vea la pelea a que quede de espectador
	 * sin forma de volver.
	 */
	boolean aMirar(ServerPlayer quien, Arena arena, ServerLevel nivel) {
		String clave = clave(quien);
		if (sitios.containsKey(clave)) return true;

		sitios.put(clave, new Sitio(
				quien.level().dimension().identifier().toString(),
				quien.getX(), quien.getY(), quien.getZ(),
				quien.getYRot(), quien.getXRot(),
				quien.gameMode().name()));
		if (!escribir()) {
			sitios.remove(clave);
			return false;
		}

		// Al centro de la arena y bien arriba, mirando para abajo. No hay un asiento
		// marcado y no hace falta: de espectador vuela, atraviesa las paredes y se
		// acomoda solo. Y asi cada cancha nueva trae su propio punto de mirada sin
		// que nadie tenga que marcarlo.
		Vec3 desde = arena.centro().add(0, arena.alto() / 3.0, 0);
		quien.teleportTo(nivel, desde.x, desde.y, desde.z,
				Set.<Relative>of(), quien.getYRot(), 35.0f, false);
		quien.setGameMode(GameType.SPECTATOR);
		quien.setDeltaMovement(0, 0, 0);
		quien.resetFallDistance();
		return true;
	}

	boolean estaMirando(ServerPlayer quien) {
		return sitios.containsKey(clave(quien));
	}

	/** Lo devuelve a donde estaba, con el modo de juego que tenia. */
	void devolver(ServerPlayer quien) {
		Sitio sitio = sitios.remove(clave(quien));
		if (sitio == null) return;
		escribir();

		quien.setGameMode(sitio.comoEstaba());
		ServerLevel destino = servidor == null ? null : servidor.getLevel(sitio.donde());
		if (destino != null) {
			quien.teleportTo(destino, sitio.x(), sitio.y(), sitio.z(),
					Set.<Relative>of(), sitio.giro(), sitio.mira(), false);
		} else {
			LOG.error("No existe {} para devolver a {}: lo dejo donde esta, pero en su modo.",
					sitio.mundo(), quien.getScoreboardName());
		}
		quien.setDeltaMovement(0, 0, 0);
		quien.resetFallDistance();
	}

	/**
	 * Cuantos bloques alrededor de la arena puede alejarse el que mira.
	 *
	 * De espectador se vuela sin frenos y el coliseo esta a 500.000 bloques del
	 * spawn: el que sale derecho cruza el mundo entero y no vuelve. Con 48 sobra
	 * para recorrer las gradas y todo el edificio —la cancha mide 37 de lado y el
	 * coliseo entero 61— y no alcanza para irse a ningun lado.
	 */
	private static final double ALCANCE = 48.0;

	/**
	 * Devuelve arriba de la cancha al que se fue volando lejos.
	 *
	 * Corre una vez por tick, y con la lista vacia —que es casi siempre— no hace
	 * nada. No le avisa nada al jugador: mientras esta adentro del edificio no se
	 * entera de que esto existe, y el que choca contra el limite lo nota solo.
	 */
	void noSeVayan(MinecraftServer servidor, Arena arena) {
		if (sitios.isEmpty() || arena == null) return;
		ServerLevel nivel = arena.nivel(servidor);
		if (nivel == null) return;

		AABB cerca = arena.caja().inflate(ALCANCE);
		Vec3 desde = arena.centro().add(0, arena.alto() / 3.0, 0);
		for (String clave : new ArrayList<>(sitios.keySet())) {
			ServerPlayer quien = porClave(clave);
			if (quien == null) continue;
			if (quien.level() == nivel && cerca.contains(quien.position())) continue;
			quien.teleportTo(nivel, desde.x, desde.y, desde.z,
					Set.<Relative>of(), quien.getYRot(), 35.0f, false);
			quien.setDeltaMovement(0, 0, 0);
			quien.resetFallDistance();
		}
	}

	/** Cuando termina la pelea vuelven todos. */
	void devolverATodos() {
		if (servidor == null) return;
		for (String clave : new ArrayList<>(sitios.keySet())) {
			ServerPlayer quien = porClave(clave);
			if (quien != null) devolver(quien);
		}
	}

	/** Los que estan mirando ahora mismo y siguen conectados. */
	List<ServerPlayer> mirando() {
		List<ServerPlayer> gente = new ArrayList<>();
		if (servidor == null) return gente;
		for (String clave : sitios.keySet()) {
			ServerPlayer quien = porClave(clave);
			if (quien != null) gente.add(quien);
		}
		return gente;
	}

	private ServerPlayer porClave(String clave) {
		for (ServerPlayer quien : servidor.getPlayerList().getPlayers()) {
			if (clave(quien).equals(clave)) return quien;
		}
		return null;
	}

	private static String clave(ServerPlayer quien) {
		return quien.getScoreboardName().toLowerCase(Locale.ROOT);
	}

	private boolean escribir() {
		JsonObject json = new JsonObject();
		sitios.forEach((nombre, sitio) -> json.add(nombre, GSON.toJsonTree(sitio)));
		try {
			Files.createDirectories(ruta.getParent());
			Files.writeString(ruta, GSON.toJson(json), StandardCharsets.UTF_8);
			return true;
		} catch (Exception error) {
			LOG.error("No pude escribir {}", ruta, error);
			return false;
		}
	}
}
