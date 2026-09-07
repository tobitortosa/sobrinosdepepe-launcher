package pe.sobrinosdepepe.precios;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Muestra en la descripcion de cada item cuanta plata paga el servidor por
 * venderlo, para no tener que abrir la tienda ni escribir /worth cada vez.
 *
 * Aparece en el inventario, en la mano y adentro de cualquier contenedor de
 * verdad: cofres, barriles, shulkers, el cofre de ender. Donde NO aparece es
 * donde ya hay un precio escrito, que son los menus de EconomyCraft, y en los
 * botones de nuestros propios menus de cofre.
 *
 * Los precios viajan adentro del mod: son los mismos que tiene el servidor en
 * config/economycraft/prices.json. Si alguna vez cambian, hay que rearmar el
 * mod y publicarlo de nuevo, que es el precio de no depender del servidor para
 * dibujar un cartelito.
 */
public class PreciosCliente implements ClientModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("preciosdepepe");
	private static final Map<String, Integer> VENTA = new HashMap<>();

	@Override
	public void onInitializeClient() {
		cargarPrecios();

		ItemTooltipCallback.EVENT.register((pila, contexto, bandera, lineas) -> {
			if (soloEspacios(lineas)) {
				lineas.clear();
				return;
			}

			if (esBoton(pila) || yaMuestraPrecio(lineas)) return;

			var id = BuiltInRegistries.ITEM.getKey(pila.getItem());
			Integer unidad = VENTA.get(id.toString());
			if (unidad == null) return;

			lineas.add(linea(unidad, pila.getCount()));
		});
	}

	/**
	 * Los botones de los menus de cofre del servidor no son objetos: son iconos.
	 * Ponerle "Precio: $180" al lingote de oro que abre ECONOMIA no informa nada
	 * y ensucia un menu que ya tiene su propio texto.
	 *
	 * La marca la pone el servidor en custom_data, y tiene que ser ese componente
	 * y no otro. La primera version miraba tooltip_display, que parecia la marca
	 * natural porque los items de menu lo llevan para esconder el dano de ataque.
	 * Pero tooltip_display esta en COMMON_ITEM_COMPONENTS: TODOS los items lo
	 * traen por defecto, asi que el chequeo daba verdadero siempre y desaparecio
	 * el precio del inventario entero. custom_data no esta en esa lista, o sea
	 * que un item normal no lo tiene nunca.
	 */
	private static boolean esBoton(ItemStack pila) {
		CustomData datos = pila.get(DataComponents.CUSTOM_DATA);
		return datos != null && datos.copyTag().contains("sdp");
	}

	/**
	 * Un cartel cuyo texto es todo espacios se borra, asi no queda el cuadrito
	 * negro vacio flotando.
	 *
	 * Es para el relleno de los menus de EconomyCraft: su MenuUiSupport.filler()
	 * es un vidrio gris con custom_name de un solo espacio y sin tooltip_display,
	 * asi que el juego le dibuja una caja con una linea en blanco adentro. Antes
	 * no se notaba porque este mod le metia el precio del vidrio adentro.
	 *
	 * Nuestros propios menus no pasan por aca: su relleno lleva tooltip_display
	 * con hide_tooltip, y con eso ItemStack.getTooltipLines devuelve la lista
	 * vacia directamente. Y una lista vacia no dibuja nada, porque
	 * setTooltipForNextFrameInternal arranca con "if (!lines.isEmpty())".
	 *
	 * Borrar es seguro: solo saca carteles que iban a verse vacios igual. Un item
	 * de verdad siempre tiene el nombre, que no es blanco.
	 */
	private static boolean soloEspacios(java.util.List<Component> lineas) {
		if (lineas.isEmpty()) return false;

		for (Component linea : lineas) {
			if (!linea.getString().isBlank()) return false;
		}

		return true;
	}

	/**
	 * No repetir el precio donde ya hay uno escrito.
	 *
	 * Es el reemplazo de un filtro anterior que solo mostraba el precio en el
	 * inventario del jugador, comparando por identidad de ItemStack contra los
	 * slots del inventario. Ese filtro cumplia su objetivo (no ensuciar el /shop
	 * de EconomyCraft, que ya escribe cuanto sale y cuanto paga) pero se llevaba
	 * puesto justo el caso mas util: abrir un cofre y ver por cuanto se vende
	 * cada cosa. Los items de un cofre son copias aparte y nunca coincidian.
	 *
	 * Este chequeo mira el contenido en vez del contenedor, y por eso acierta en
	 * los dos lados sin tener que distinguirlos: del lado del cliente un cofre y
	 * el menu de una tienda son los dos un ChestMenu y no hay forma de saber cual
	 * es cual.
	 *
	 * El ancla es que EconomyCraft.formatMoney SIEMPRE arranca con "$"
	 * (return "$" + new DecimalFormat("#,##0", symbols).format(amount)), asi que
	 * cualquier precio que el mod dibuje (en el /shop, en el /ah, en las ordenes o
	 * en el /eco admin) trae un signo peso seguido de un digito. Donde aparezca
	 * eso, nos callamos.
	 *
	 * Es preferible a filtrar por el titulo del menu: MenuUiSupport.openMenu recibe
	 * el titulo como un String literal del codigo que llama, asi que atarse a esos
	 * textos se rompe en silencio en cuanto el mod cambia una palabra.
	 *
	 * Lo unico que se pierde: un item que alguien haya renombrado con algo como
	 * "$5" no muestra el precio. No molesta a nadie.
	 */
	private static boolean yaMuestraPrecio(java.util.List<Component> lineas) {
		for (Component linea : lineas) {
			String texto = linea.getString();
			int peso = texto.indexOf('$');

			while (peso >= 0) {
				if (peso + 1 < texto.length() && Character.isDigit(texto.charAt(peso + 1))) return true;
				peso = texto.indexOf('$', peso + 1);
			}
		}

		return false;
	}

	/** "Precio: $2" y, cuando hay varios, cuanto vale el monton entero. */
	private static Component linea(int unidad, int cantidad) {
		MutableComponent texto = Component.literal("Precio: ")
				.withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal("$" + conPuntos(unidad)).withStyle(ChatFormatting.GOLD));

		if (cantidad > 1) {
			texto = texto
					.append(Component.literal("   x" + cantidad + " = ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal("$" + conPuntos((long) unidad * cantidad))
							.withStyle(ChatFormatting.YELLOW));
		}

		return texto;
	}

	/** 12345 queda 12.345, igual que como muestra la plata el servidor. */
	private static String conPuntos(long valor) {
		String base = Long.toString(valor);
		StringBuilder salida = new StringBuilder();
		int corte = base.length() % 3;

		if (corte > 0) salida.append(base, 0, corte);

		for (int i = corte; i < base.length(); i += 3) {
			if (!salida.isEmpty()) salida.append('.');
			salida.append(base, i, i + 3);
		}

		return salida.toString();
	}

	private static void cargarPrecios() {
		try (InputStream entrada = PreciosCliente.class.getResourceAsStream("/precios.json")) {
			if (entrada == null) {
				LOG.error("El mod no trae precios.json adentro");
				return;
			}

			JsonObject json = JsonParser
					.parseReader(new InputStreamReader(entrada, StandardCharsets.UTF_8))
					.getAsJsonObject();

			for (Map.Entry<String, JsonElement> par : json.entrySet()) {
				VENTA.put(par.getKey(), par.getValue().getAsInt());
			}

			LOG.info("Cargados {} precios de venta", VENTA.size());
		} catch (Exception e) {
			LOG.error("No pude leer los precios", e);
		}
	}
}
