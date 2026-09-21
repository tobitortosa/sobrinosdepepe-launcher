package pe.sobrinosdepepe.duelos;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * La casa de apuestas de un duelo: quien le puso cuanto a quien, y como se
 * reparte despues.
 *
 * Es el reparto de una rifa de carrera, el mismo de un hipodromo: **todo lo que
 * pusieron los que perdieron se reparte entre los que ganaron, en proporcion a lo
 * que cada uno arriesgo**. El que apostó 100 cobra el doble que el que apostó 50.
 * El que le acerto recupera ademas lo suyo.
 *
 * No hay comision de la casa. El servidor no se queda con nada, porque la gracia
 * no es sacarle shards a nadie: es que el duelo lo mire todo el mundo.
 *
 * Los shards se cobran **al apostar** y no al final. Si se cobraran al final, el
 * que apostó 500 y los gasto en la tienda mientras miraba la pelea pagaria con
 * shards que ya no tiene, y el objetivo del scoreboard se iria a numeros
 * negativos sin que nada se queje.
 *
 * Cuando la division no da entera, lo que sobra se lo lleva el que gano la pelea.
 * Son unos pocos shards y tienen que ir a algun lado: si se quedaran sin dueño,
 * la cuenta del servidor dejaria de cerrar y los shards se irian evaporando de a
 * uno por duelo.
 */
public final class Apuestas {
	private final Map<String, Integer> porUno = new LinkedHashMap<>();
	private final Map<String, Integer> porOtro = new LinkedHashMap<>();
	/** El nombre tal cual lo escribe el jugador, para los carteles. */
	private final Map<String, String> comoSeLlaman = new LinkedHashMap<>();

	public void poner(String jugador, boolean aFavorDeUno, int cuanto) {
		String clave = jugador.toLowerCase(Locale.ROOT);
		comoSeLlaman.put(clave, jugador);
		Map<String, Integer> lado = aFavorDeUno ? porUno : porOtro;
		lado.merge(clave, cuanto, Integer::sum);
	}

	/** A cual de los dos le aposto, o null si todavia no aposto a nadie. */
	public Boolean aQuienLeAposto(String jugador) {
		String clave = jugador.toLowerCase(Locale.ROOT);
		if (porUno.containsKey(clave)) return Boolean.TRUE;
		if (porOtro.containsKey(clave)) return Boolean.FALSE;
		return null;
	}

	public int loQuePuso(String jugador) {
		String clave = jugador.toLowerCase(Locale.ROOT);
		return porUno.getOrDefault(clave, porOtro.getOrDefault(clave, 0));
	}

	public int total(boolean aFavorDeUno) {
		return (aFavorDeUno ? porUno : porOtro).values().stream().mapToInt(Integer::intValue).sum();
	}

	public int cuantosApostaron() {
		return porUno.size() + porOtro.size();
	}

	public boolean hay() {
		return cuantosApostaron() > 0;
	}

	/**
	 * Lo que cobra cada uno, ya con lo suyo adentro. La sobra de la division va
	 * aparte, para el que gano la pelea.
	 */
	public record Reparto(Map<String, Integer> cobran, int sobra) {}

	public Reparto repartir(boolean ganoUno) {
		Map<String, Integer> acertaron = ganoUno ? porUno : porOtro;
		Map<String, Integer> erraron = ganoUno ? porOtro : porUno;

		int pozo = erraron.values().stream().mapToInt(Integer::intValue).sum();
		int arriesgado = acertaron.values().stream().mapToInt(Integer::intValue).sum();

		Map<String, Integer> cobran = new LinkedHashMap<>();

		// Nadie le acerto al que gano: la plata de los que erraron no tiene a quien
		// ir, asi que se les devuelve. Perder porque nadie mas jugo no es perder.
		if (arriesgado == 0) {
			erraron.forEach((clave, puso) -> cobran.put(comoSeLlaman.get(clave), puso));
			return new Reparto(cobran, 0);
		}

		int repartido = 0;
		for (Map.Entry<String, Integer> quien : acertaron.entrySet()) {
			int suParte = (int) ((long) pozo * quien.getValue() / arriesgado);
			repartido += suParte;
			cobran.put(comoSeLlaman.get(quien.getKey()), quien.getValue() + suParte);
		}
		return new Reparto(cobran, pozo - repartido);
	}

	/** Todo para atras, tal cual lo puso cada uno. Para el duelo que se cancela. */
	public Map<String, Integer> devolverTodo() {
		Map<String, Integer> vuelta = new LinkedHashMap<>();
		porUno.forEach((clave, puso) -> vuelta.merge(comoSeLlaman.get(clave), puso, Integer::sum));
		porOtro.forEach((clave, puso) -> vuelta.merge(comoSeLlaman.get(clave), puso, Integer::sum));
		return vuelta;
	}
}
