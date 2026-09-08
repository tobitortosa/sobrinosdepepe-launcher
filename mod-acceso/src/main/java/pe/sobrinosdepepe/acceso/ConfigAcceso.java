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
 *     { "exigir": false, "secreto": "...", "link": "sobrinosdepepe.vercel.app" }
 *
 * **exigir** es la llave de todo: en false el servidor revisa igual y anota en el
 * log quien entro sin el launcher, pero no echa a nadie. En true no entra nadie
 * sin el launcher. Sale de REQUIRE_LAUNCHER en el .env del backend.
 *
 * El secreto es el mismo que la variable ACCESS_SECRET del backend, y el link
 * sale de SITE_URL. Los tres los sube servidor/subir-acceso.py, que los lee de
 * web/.env.local: una sola fuente de verdad, asi que el dia que compremos un
 * dominio se cambia ahi y se vuelve a subir.
 *
 * Si el archivo no esta o le falta el secreto, y exigir esta en true, NO entra
 * nadie. Es a proposito: un control de acceso que se abre solo cuando su
 * configuracion falla no es un control de acceso. El log dice que archivo
 * escribir. Con exigir en false eso mismo no molesta a nadie.
 */
public record ConfigAcceso(String secreto, String link, boolean exigir) {
	public static final String ARCHIVO = "acceso-de-pepe.json";

	/**
	 * Que hacer cuando el archivo no esta o no se puede leer: exigir el launcher.
	 * Es el lado que no deja entrar por error a quien no corresponde; el que abre
	 * el servidor entero tiene que estar escrito a mano en el archivo.
	 */
	private static final boolean EXIGIR_POR_DEFECTO = true;

	public boolean configurado() {
		return secreto != null && !secreto.isBlank();
	}

	/**
	 * @param log donde contar lo que se leyo, o null para no decir nada. Se lee en
	 *            cada intento de entrar, y repetir todo esto en cada login llenaria
	 *            el log del servidor: solo habla la lectura del arranque.
	 */
	public static ConfigAcceso leer(Logger log) {
		Path ruta = FabricLoader.getInstance().getConfigDir().resolve(ARCHIVO);

		if (!Files.isReadable(ruta)) {
			if (log != null) {
				log.error("No encontre {}. Con exigir en true, sin ese archivo no entra nadie.",
						ruta.toAbsolutePath());
				log.error("Se sube con: python servidor/subir-acceso.py");
			}
			return new ConfigAcceso(null, null, EXIGIR_POR_DEFECTO);
		}

		try {
			String texto = Files.readString(ruta, StandardCharsets.UTF_8);
			JsonObject json = JsonParser.parseString(texto).getAsJsonObject();
			String secreto = json.has("secreto") ? json.get("secreto").getAsString() : null;
			String link = json.has("link") ? json.get("link").getAsString() : "";
			boolean exigir = json.has("exigir") ? json.get("exigir").getAsBoolean() : EXIGIR_POR_DEFECTO;

			if (secreto == null || secreto.isBlank()) {
				if (log != null) {
					log.error("{} no tiene el campo \"secreto\". Con exigir en true no entra nadie.",
							ruta.toAbsolutePath());
				}
				return new ConfigAcceso(null, link, exigir);
			}

			if (log != null) log.info("Acceso configurado. El cartel manda a: {}", link);
			return new ConfigAcceso(secreto, link, exigir);
		} catch (Exception e) {
			if (log != null) {
				log.error("No pude leer " + ruta.toAbsolutePath() + ". Con exigir en true no entra nadie.", e);
			}
			return new ConfigAcceso(null, null, EXIGIR_POR_DEFECTO);
		}
	}
}
