package pe.sobrinosdepepe.equipos;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Quien esta en que equipo, y quien manda en cada uno.
 *
 * La fuente de verdad es config/equipos-de-pepe.json, y NO el scoreboard. Los
 * equipos del scoreboard son un espejo que se rearma en cada cambio y al
 * arrancar el servidor. Es al reves de lo que uno esperaria, y esta hecho a
 * proposito por dos motivos:
 *
 *  - un equipo del scoreboard no tiene donde guardar quien es el jefe, y sin
 *    jefe no hay a quien pedirle permiso para invitar ni a quien echar a nadie;
 *  - el archivo se abre desde el panel y se entiende de una. Si algo queda raro,
 *    se corrige ahi y se reinicia.
 *
 * Lo que si aporta el scoreboard, y por eso el espejo existe:
 *
 *  - `friendlyFire` en false: entre companeros no se pega. Lo hace el juego, no
 *    nosotros.
 *  - el color del nombre, que es como en una pelea se ve de un vistazo quien es
 *    de quien.
 *  - la barra de arriba. team-only-locator-bar mira `ServerPlayer.getTeam()`, o
 *    sea el equipo del scoreboard: sin el espejo, la barra no muestra a nadie.
 */
public final class Registro {
	private static final Logger LOG = LoggerFactory.getLogger("equiposdepepe");

	public static final String ARCHIVO = "equipos-de-pepe.json";

	/**
	 * Cuantos entran en un equipo. El tope no es una decoracion: la barra de
	 * arriba muestra a los companeros, asi que un equipo con medio servidor
	 * adentro es el radar de todos contra todos que justamente sacamos.
	 */
	public static final int TOPE = 6;

	/** Lo que se acepta como nombre: ni vacio, ni con espacios, ni una pared de texto. */
	public static final int LARGO_MINIMO = 3;
	public static final int LARGO_MAXIMO = 16;

	/**
	 * Los ocho colores que se reparten, en orden. Son los que se distinguen sobre
	 * el pasto y sobre la piedra; quedan afuera el negro, los dos grises y el
	 * blanco, que es el color del que no tiene equipo.
	 */
	private static final ChatFormatting[] COLORES = {
			ChatFormatting.AQUA, ChatFormatting.GREEN, ChatFormatting.YELLOW,
			ChatFormatting.LIGHT_PURPLE, ChatFormatting.RED, ChatFormatting.BLUE,
			ChatFormatting.GOLD, ChatFormatting.DARK_GREEN,
	};

	/**
	 * El prefijo de los equipos del scoreboard. Existe para no pisar los equipos
	 * de otros mods: Server-Side Horror tiene el suyo (`noTags`, con su jugador
	 * fantasma adentro) y sin el prefijo un jugador podria crear un equipo llamado
	 * igual y meterse ahi.
	 */
	private static final String PREFIJO = "sdp_";

	/** Un equipo. El jefe esta siempre adentro de la lista de miembros. */
	public static final class Equipo {
		public final String nombre;
		public ChatFormatting color;
		public String jefe;
		public final List<String> miembros;

		Equipo(String nombre, ChatFormatting color, String jefe, List<String> miembros) {
			this.nombre = nombre;
			this.color = color;
			this.jefe = jefe;
			this.miembros = miembros;
		}

		public boolean manda(String jugador) {
			return jefe.equalsIgnoreCase(jugador);
		}

		public boolean tiene(String jugador) {
			return miembros.stream().anyMatch(m -> m.equalsIgnoreCase(jugador));
		}

		public boolean lleno() {
			return miembros.size() >= TOPE;
		}

		/** El nombre del equipo del scoreboard que lo espeja. */
		public String enElScoreboard() {
			return PREFIJO + nombre.toLowerCase(Locale.ROOT);
		}
	}

	private final List<Equipo> equipos = new ArrayList<>();
	private final Path ruta;
	private MinecraftServer servidor;

	public Registro() {
		this.ruta = FabricLoader.getInstance().getConfigDir().resolve(ARCHIVO);
	}

	// ------------------------------------------------------------------ consultas

	public Equipo de(String jugador) {
		for (Equipo equipo : equipos) {
			if (equipo.tiene(jugador)) return equipo;
		}
		return null;
	}

	public Equipo porNombre(String nombre) {
		for (Equipo equipo : equipos) {
			if (equipo.nombre.equalsIgnoreCase(nombre)) return equipo;
		}
		return null;
	}

	public List<Equipo> todos() {
		return List.copyOf(equipos);
	}

	// ------------------------------------------------------------------- cambios

	public Equipo crear(String nombre, String jefe) {
		Equipo equipo = new Equipo(nombre, colorLibre(), jefe, new ArrayList<>(List.of(jefe)));
		equipos.add(equipo);
		aplicar(equipo);
		guardar();
		return equipo;
	}

	public void sumar(Equipo equipo, String jugador) {
		equipo.miembros.add(jugador);
		aplicar(equipo);
		guardar();
	}

	/**
	 * Saca a un jugador de su equipo y devuelve si el equipo se disolvio.
	 *
	 * Si el que se va es el jefe, manda el que sigue. No hay eleccion ni nada
	 * parecido: el equipo tiene que quedar siempre con alguien que pueda invitar y
	 * echar, y el que sigue es el que hace mas tiempo que esta.
	 */
	public boolean sacar(Equipo equipo, String jugador) {
		equipo.miembros.removeIf(m -> m.equalsIgnoreCase(jugador));
		desafiliar(jugador);

		if (equipo.miembros.isEmpty()) {
			equipos.remove(equipo);
			borrarDelScoreboard(equipo);
			guardar();
			return true;
		}

		if (equipo.manda(jugador)) equipo.jefe = equipo.miembros.get(0);
		aplicar(equipo);
		guardar();
		return false;
	}

	// -------------------------------------------------------- el espejo y el disco

	/**
	 * Se llama una sola vez, cuando el servidor termino de arrancar: deja el
	 * scoreboard igual al archivo. Antes de esto no hay scoreboard que tocar,
	 * porque el mundo todavia no esta cargado.
	 */
	public void alArrancar(MinecraftServer servidor) {
		this.servidor = servidor;
		leer();

		Set<String> mios = new HashSet<>();
		for (Equipo equipo : equipos) {
			mios.add(equipo.enElScoreboard());
			aplicar(equipo);
		}

		// Los equipos que quedaron en el scoreboard y ya no estan en el archivo:
		// pasa si se edito el archivo a mano con el servidor apagado. Se limpian
		// solos los que son nuestros, que son los que empiezan con el prefijo.
		Scoreboard scoreboard = servidor.getScoreboard();
		for (PlayerTeam viejo : new ArrayList<>(scoreboard.getPlayerTeams())) {
			if (viejo.getName().startsWith(PREFIJO) && !mios.contains(viejo.getName())) {
				scoreboard.removePlayerTeam(viejo);
				LOG.info("Borro el equipo {} del scoreboard: no esta en {}", viejo.getName(), ARCHIVO);
			}
		}

		LOG.info("{} equipo(s) cargados de {}", equipos.size(), ruta.toAbsolutePath());
	}

	/** Deja el equipo del scoreboard igual al del archivo. */
	private void aplicar(Equipo equipo) {
		if (servidor == null) return;

		Scoreboard scoreboard = servidor.getScoreboard();
		String id = equipo.enElScoreboard();
		PlayerTeam team = scoreboard.getPlayerTeam(id);
		if (team == null) team = scoreboard.addPlayerTeam(id);

		team.setDisplayName(Component.literal(equipo.nombre));
		team.setColor(equipo.color);
		// Lo unico que de verdad cambia como se juega: entre companeros no se pega.
		team.setAllowFriendlyFire(false);

		// Los que estan en el team del scoreboard y ya no en el equipo. Sale del
		// team y no del juego: addPlayerToTeam ya saca al que estaba en otro lado.
		for (String estaba : new ArrayList<>(team.getPlayers())) {
			if (!equipo.tiene(estaba)) scoreboard.removePlayerFromTeam(estaba, team);
		}
		for (String miembro : equipo.miembros) {
			scoreboard.addPlayerToTeam(miembro, team);
		}
	}

	private void desafiliar(String jugador) {
		if (servidor == null) return;
		Scoreboard scoreboard = servidor.getScoreboard();
		PlayerTeam team = scoreboard.getPlayersTeam(jugador);
		if (team != null && team.getName().startsWith(PREFIJO)) {
			scoreboard.removePlayerFromTeam(jugador, team);
		}
	}

	private void borrarDelScoreboard(Equipo equipo) {
		if (servidor == null) return;
		Scoreboard scoreboard = servidor.getScoreboard();
		PlayerTeam team = scoreboard.getPlayerTeam(equipo.enElScoreboard());
		if (team != null) scoreboard.removePlayerTeam(team);
	}

	/** El primero de la lista que no este en uso, y si estan todos, el que toque. */
	private ChatFormatting colorLibre() {
		Collection<ChatFormatting> usados = equipos.stream().map(e -> e.color).toList();
		for (ChatFormatting color : COLORES) {
			if (!usados.contains(color)) return color;
		}
		return COLORES[equipos.size() % COLORES.length];
	}

	private void leer() {
		if (!Files.isReadable(ruta)) {
			LOG.info("Todavia no hay {}: el primer /equipo crear lo escribe.", ruta.toAbsolutePath());
			return;
		}

		try {
			JsonObject json = JsonParser.parseString(Files.readString(ruta, StandardCharsets.UTF_8))
					.getAsJsonObject();
			for (var elemento : json.getAsJsonArray("equipos")) {
				JsonObject o = elemento.getAsJsonObject();
				List<String> miembros = new ArrayList<>();
				for (var m : o.getAsJsonArray("miembros")) miembros.add(m.getAsString());
				if (miembros.isEmpty()) continue;

				// Si el jefe que dice el archivo no esta en la lista de miembros, manda
				// el primero: un equipo sin jefe no puede invitar ni echar a nadie.
				String escrito = o.get("jefe").getAsString();
				String jefe = miembros.stream().anyMatch(m -> m.equalsIgnoreCase(escrito))
						? escrito : miembros.get(0);

				equipos.add(new Equipo(
						o.get("nombre").getAsString(),
						ChatFormatting.getByName(o.get("color").getAsString()),
						jefe,
						miembros));
			}
		} catch (Exception e) {
			// Sin equipos se puede jugar; con un archivo a medio leer no. Se avisa
			// fuerte y se arranca vacio, sin pisar el archivo: el proximo cambio si
			// lo pisa, asi que hay que mirar el log antes de que alguien toque algo.
			LOG.error("No pude leer " + ruta.toAbsolutePath() + ". Arranco sin equipos.", e);
			equipos.clear();
		}
	}

	private void guardar() {
		JsonArray lista = new JsonArray();
		for (Equipo equipo : equipos) {
			JsonObject o = new JsonObject();
			o.addProperty("nombre", equipo.nombre);
			o.addProperty("color", equipo.color.getName());
			o.addProperty("jefe", equipo.jefe);
			JsonArray miembros = new JsonArray();
			equipo.miembros.forEach(miembros::add);
			o.add("miembros", miembros);
			lista.add(o);
		}
		JsonObject json = new JsonObject();
		json.add("equipos", lista);

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
}
