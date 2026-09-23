package pe.sobrinosdepepe.duelos;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * La casa de apuestas de un duelo: quien le puso cuanto a quien, y como se
 * reparte despues.
 *
 * **Se paga a la par: le acertaste, cobras el doble de lo que pusiste.** Cada
 * shard del que erro le paga un shard al que acerto, y nada mas. Lo que no
 * encuentra con quien cruzarse vuelve a quien lo puso.
 *
 * Antes era el reparto de un hipodromo —todo lo de los que erraron se repartia
 * entre los que acertaron— y en la primera pelea con apuestas de verdad eso pago
 * 65 shards por una apuesta de 10, porque del otro lado habia uno solo que habia
 * puesto todo lo que tenia. La cuenta cerraba (no se inventa ni un shard, ni
 * antes ni ahora), pero con dos o tres apostadores las cuotas salen de cualquier
 * lado, y un shard vale diez minutos de juego o una kill. Una apuesta tiene que
 * pagar algo que el que la hace pueda calcular antes de hacerla.
 *
 * Como queda entonces, con G lo que arriesgaron los que acertaron y P lo que
 * pusieron los que erraron:
 *
 *  - se cruza `min(G, P)`, que se reparte entre los que acertaron en proporcion
 *    a lo suyo. Si del otro lado habia de sobra, cada uno cobra exactamente el
 *    doble; si no alcanzaba, cobra su parte de lo que si habia;
 *  - `P - G`, si sobro del lado de los que erraron, se les devuelve. Perdieron
 *    la parte que alguien les acepto, no la que nadie quiso.
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
		return sumar(aFavorDeUno ? porUno : porOtro);
	}

	public int cuantosApostaron() {
		return porUno.size() + porOtro.size();
	}

	public boolean hay() {
		return cuantosApostaron() > 0;
	}

	/**
	 * Lo que cobra cada uno cuando termina la pelea.
	 *
	 * Van separados a proposito: al que acerto se le avisa que cobro, y al que erro
	 * y recupera lo que nadie le quiso cruzar hay que decirle que eso no es un
	 * premio. El mismo cartel para los dos le haria creer que gano.
	 */
	public record Reparto(Map<String, Integer> cobran, Map<String, Integer> devuelven, int sobra) {}

	public Reparto repartir(boolean ganoUno) {
		Map<String, Integer> acertaron = ganoUno ? porUno : porOtro;
		Map<String, Integer> erraron = ganoUno ? porOtro : porUno;

		int pozo = sumar(erraron);
		int arriesgado = sumar(acertaron);

		Map<String, Integer> cobran = new LinkedHashMap<>();
		Map<String, Integer> devuelven = new LinkedHashMap<>();

		// Nadie le acerto al que gano: la plata de los que erraron no tiene a quien
		// ir, asi que se les devuelve. Perder porque nadie mas jugo no es perder.
		if (arriesgado == 0) {
			erraron.forEach((clave, puso) -> devuelven.put(comoSeLlaman.get(clave), puso));
			return new Reparto(cobran, devuelven, 0);
		}

		// Lo que de verdad se jugo: ni un shard mas que lo que arriesgo el otro lado.
		int cruzado = Math.min(pozo, arriesgado);

		int repartido = 0;
		for (Map.Entry<String, Integer> quien : acertaron.entrySet()) {
			int suParte = (int) ((long) cruzado * quien.getValue() / arriesgado);
			repartido += suParte;
			cobran.put(comoSeLlaman.get(quien.getKey()), quien.getValue() + suParte);
		}

		// Lo que del lado de los que erraron nadie acepto. Vuelve en proporcion a lo
		// que puso cada uno, que es el orden en el que quedo sin cruzar.
		int sinCruzar = pozo - cruzado;
		int devuelto = 0;
		if (sinCruzar > 0) {
			for (Map.Entry<String, Integer> quien : erraron.entrySet()) {
				int suVuelto = (int) ((long) sinCruzar * quien.getValue() / pozo);
				if (suVuelto <= 0) continue;
				devuelto += suVuelto;
				devuelven.put(comoSeLlaman.get(quien.getKey()), suVuelto);
			}
		}

		return new Reparto(cobran, devuelven, (cruzado - repartido) + (sinCruzar - devuelto));
	}

	private static int sumar(Map<String, Integer> lado) {
		return lado.values().stream().mapToInt(Integer::intValue).sum();
	}

	/** Todo para atras, tal cual lo puso cada uno. Para el duelo que se cancela. */
	public Map<String, Integer> devolverTodo() {
		Map<String, Integer> vuelta = new LinkedHashMap<>();
		porUno.forEach((clave, puso) -> vuelta.merge(comoSeLlaman.get(clave), puso, Integer::sum));
		porOtro.forEach((clave, puso) -> vuelta.merge(comoSeLlaman.get(clave), puso, Integer::sum));
		return vuelta;
	}
}
