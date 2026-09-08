package pe.sobrinosdepepe.acceso;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lo que el servidor necesita saber para revisar los tickets: el secreto con el
 * que el backend los firma y el link que se le muestra a quien no tiene uno.
 *
 * Vive en config/acceso-de-pepe.json adentro del servidor:
 *
 *     { "secreto": "...", "link": "sobrinosdepepe.vercel.app" }
 *
 * El secreto es el mismo que la variable ACCESS_SECRET del backend, y el link
 * sale de SITE_URL. Los dos los sube servidor/subir-acceso.py, que los lee de
 * web/.env.local: una sola fuente de verdad, asi que el dia que compremos un
 * dominio se cambia ahi y se vuelve a subir.
 *
 * Si el archivo no esta o le falta el secreto, NO entra nadie. Es a proposito:
 * un control de acceso que se abre solo cuando su configuracion falla no es un
 * control de acceso. El log dice exactamente que archivo escribir.
 */
public record ConfigAcceso(String secreto, String link) {
	public static final String ARCHIVO = "acceso-de-pepe.json";

	public boolean configurado() {
		return secreto != null && !secreto.isBlank();
	}

	public static ConfigAcceso leer(Logger log) {
		Path ruta = FabricLoader.getInstance().getConfigDir().resolve(ARCHIVO);

		if (!Files.isReadable(ruta)) {
			log.error("No encontre {}. Sin ese archivo no entra nadie al servidor.", ruta.toAbsolutePath());
			log.error("Se sube con: python servidor/subir-acceso.py");
			return new ConfigAcceso(null, null);
		}

		try {
			String texto = Files.readString(ruta, StandardCharsets.UTF_8);
			JsonObject json = JsonParser.parseString(texto).getAsJsonObject();
			String secreto = json.has("secreto") ? json.get("secreto").getAsString() : null;
			String link = json.has("link") ? json.get("link").getAsString() : "";

			if (secreto == null || secreto.isBlank()) {
				log.error("{} no tiene el campo \"secreto\". Sin eso no entra nadie.", ruta.toAbsolutePath());
				return new ConfigAcceso(null, link);
			}

			log.info("Acceso configurado. El cartel manda a: {}", link);
			return new ConfigAcceso(secreto, link);
		} catch (Exception e) {
			log.error("No pude leer " + ruta.toAbsolutePath() + ". Sin eso no entra nadie.", e);
			return new ConfigAcceso(null, null);
		}
	}
}
