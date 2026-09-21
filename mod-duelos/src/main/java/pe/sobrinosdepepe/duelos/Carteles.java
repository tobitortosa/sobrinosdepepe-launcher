package pe.sobrinosdepepe.duelos;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.PlayerScoreEntry;

import java.util.List;
import java.util.Map;

/**
 * Todo lo que el jugador lee de los duelos.
 *
 * Los colores y los simbolos son los mismos de servidor/estilo.py, porque el
 * chat, el cartel de la derecha y los menus de cofre tienen que verse como una
 * sola cosa. Los simbolos van del plano 0 de Unicode: los del plano 1 salen como
 * un cuadrado vacio en la fuente de fabrica y aca nadie usa resource pack.
 *
 * Los botones son lo mas importante de este archivo. Nadie escribe
 * "/pvp aceptar Fulano" a mano: al que lo retan le llega un [ ACEPTAR ] y un
 * [ RECHAZAR ] y los clickea, que es exactamente como funcionan las invitaciones
 * de equipo. Lo mismo con las apuestas: el anuncio del duelo trae los dos
 * nombres clickeables y lo unico que hay que tipear es el numero.
 */
public final class Carteles {
	private Carteles() {}

	private static final int MARCA = 0xffb02e;
	private static final int ACENTO = 0x00a6ff;
	private static final int BIEN = 0x55ff7f;
	private static final int ERROR = 0xff5555;
	private static final int SHARDS = 0xa503fc;
	private static final int KILLS = 0xff0000;

	private static final String VINETA = "▪";
	private static final String RAYA = "▬";
	private static final String FLECHA = "»";
	private static final String ESPADAS = "⚔";
	private static final String SHARD = "✦";
	private static final String ESTRELLA = "★";
	private static final String TILDE = "✔";
	private static final String CRUZ = "✖";

	// --------------------------------------------------------------------- el reto

	/** Lo que ve el retado. Los dos botones son todo: nadie tipea el nombre. */
	public static Component teRetaron(String retador, int apuesta, int segundos) {
		MutableComponent cartel = Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(rojo("  " + ESPADAS + " TE RETARON A UN DUELO\n\n"))
				.append(gris("  "))
				.append(Component.literal(retador).withStyle(e -> e.withColor(MARCA).withBold(true)))
				.append(gris(" te quiere pelear en la arena.\n"));

		if (apuesta > 0) {
			cartel.append(gris("  Se juegan "))
					.append(shards(apuesta))
					.append(gris(" cada uno: el que gana se lleva los dos.\n"));
		} else {
			cartel.append(gris("  Sin apuesta: se pelea por el honor.\n"));
		}

		return cartel
				.append(Component.literal("\n  "))
				.append(boton("[ ACEPTAR ]", "/pvp aceptar " + retador, BIEN,
						"Entrar a la arena contra " + retador))
				.append(Component.literal("  "))
				.append(boton("[ RECHAZAR ]", "/pvp rechazar " + retador, ERROR,
						"Decirle que no"))
				.append(gris("\n\n  Tenes " + segundos + " segundos para contestar.\n"))
				.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	public static Component retaste(String retado, int apuesta, int segundos) {
		MutableComponent cartel = bien("Lo retaste a " + retado + ". ");
		if (apuesta > 0) cartel.append(gris("Se juegan " + apuesta + " shards cada uno. "));
		return cartel.append(gris("Tiene " + segundos + " segundos para contestar."));
	}

	public static Component teRechazaron(String quien) {
		return aviso(quien + " no te quiso pelear.");
	}

	public static Component rechazaste(String quien) {
		return gris("  Le dijiste que no a " + quien + ".");
	}

	// ------------------------------------------------------------------- el anuncio

	/**
	 * El anuncio que sale para todo el servidor cuando arranca un duelo. Es el que
	 * junta gente en las gradas, asi que lleva los dos nombres clickeables para
	 * apostar y dice cuanto tiempo queda.
	 */
	public static Component arrancaElDuelo(String uno, String otro, int apuesta,
			String kit, int segundos) {
		MutableComponent cartel = Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(dorado("  " + ESPADAS + " DUELO EN LA ARENA\n\n"))
				.append(Component.literal("  "))
				.append(Component.literal(uno).withStyle(e -> e.withColor(BIEN).withBold(true)))
				.append(rojo("  vs  "))
				.append(Component.literal(otro).withStyle(e -> e.withColor(ACENTO).withBold(true)))
				.append(Component.literal("\n"))
				.append(gris("  Clase: "))
				.append(Component.literal(kit).withStyle(e -> e.withColor(MARCA)))
				.append(gris("   los dos con lo mismo\n"));

		if (apuesta > 0) {
			cartel.append(gris("  Se juegan "))
					.append(shards(apuesta))
					.append(gris(" cada uno\n"));
		}

		return cartel
				.append(Component.literal("\n"))
				.append(gris("  Apostale a uno de los dos:\n  "))
				.append(boton("[ " + uno.toUpperCase() + " ]", "/pvp apostar " + uno + " ", BIEN,
						"Completa con cuantos shards le ponés"))
				.append(Component.literal("  "))
				.append(boton("[ " + otro.toUpperCase() + " ]", "/pvp apostar " + otro + " ", ACENTO,
						"Completa con cuantos shards le ponés"))
				.append(gris("\n\n  Las apuestas cierran en " + segundos + " segundos.\n"))
				.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	public static Component apostaste(String aQuien, int cuanto, int aFavor, int enContra) {
		return Component.empty()
				.append(Component.literal("  " + SHARD + " ").withStyle(e -> e.withColor(SHARDS)))
				.append(gris("Le pusiste "))
				.append(shards(cuanto))
				.append(gris(" a "))
				.append(Component.literal(aQuien).withStyle(e -> e.withColor(MARCA).withBold(true)))
				.append(gris(".  A favor " + aFavor + "  /  en contra " + enContra));
	}

	public static Component cobraste(int cuanto, int puesto) {
		return Component.empty()
				.append(Component.literal("  " + SHARD + " ").withStyle(e -> e.withColor(SHARDS)))
				.append(Component.literal("+" + cuanto + " shards")
						.withStyle(e -> e.withColor(SHARDS).withBold(true)))
				.append(gris("  le habias puesto " + puesto));
	}

	public static Component teDevolvemos(int cuanto) {
		return aviso("Se cancelo el duelo: te devolvemos los " + cuanto + " shards.");
	}

	// ------------------------------------------------------------------- el final

	public static Component resultado(String ganador, String perdedor, int apuesta, String comoFue) {
		MutableComponent cartel = Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(Component.literal("  " + ESTRELLA + " GANO ")
						.withStyle(e -> e.withColor(MARCA).withBold(true)))
				.append(Component.literal(ganador.toUpperCase())
						.withStyle(e -> e.withColor(BIEN).withBold(true)))
				.append(Component.literal("\n\n"))
				.append(gris("  " + comoFue + "\n"));

		if (apuesta > 0) {
			cartel.append(gris("  Se lleva "))
					.append(shards(apuesta * 2))
					.append(gris(" de la apuesta de los dos.\n"));
		}
		return cartel.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	public static Component empate(String uno, String otro) {
		return Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(dorado("  EMPATE\n\n"))
				.append(gris("  " + uno + " y " + otro + " terminaron con la misma vida.\n"))
				.append(gris("  Vuelve todo para atras: las apuestas y lo que pusieron.\n"))
				.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	// -------------------------------------------------------------------- la ayuda

	/** `/pvp duelos`: como funciona todo esto, en una ficha. */
	public static Component comoEs(boolean hayArena, int clases) {
		MutableComponent cartel = Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(dorado("  " + ESPADAS + " LOS DUELOS\n\n"))
				.append(gris("  Retas a alguien, caen los dos en la arena con\n"))
				.append(gris("  el MISMO kit sacado al azar entre " + clases + ", cuenta\n"))
				.append(gris("  de 3, 2, 1 y a pelear. Gana el que pelea mejor.\n\n"))
				.append(Component.literal("  " + VINETA + " ").withStyle(e -> e.withColor(BIEN)))
				.append(gris("No perdes NADA: tu inventario te espera\n    afuera y te vuelve entero al terminar.\n"))
				.append(Component.literal("  " + VINETA + " ").withStyle(e -> e.withColor(BIEN)))
				.append(gris("Nadie se puede meter en la arena.\n"))
				.append(Component.literal("  " + VINETA + " ").withStyle(e -> e.withColor(BIEN)))
				.append(gris("Rompan todo lo que quieran: la arena se\n    arregla sola despues.\n"))
				.append(Component.literal("  " + VINETA + " ").withStyle(e -> e.withColor(SHARDS)))
				.append(gris("El resto apuesta shards a quien va a ganar.\n\n"))
				.append(comando("/pvp ", "retar a alguien: /pvp <jugador> [shards]"))
				.append(comando("/pvp apostar ", "ponerle shards a uno de los dos"))
				.append(comando("/pvp rendirse", "abandonar el duelo que estas peleando"))
				.append(comando("/pvp top", "los que mas duelos ganaron"))
				.append(comando("/pvp torneo", "el torneo, cuando hay uno"));

		if (!hayArena) {
			cartel.append(Component.literal("\n"))
					.append(rojo("  Todavia no hay arena marcada: hasta que el\n"))
					.append(rojo("  administrador marque una, no se puede pelear.\n"));
		}
		return cartel.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	public static Component top(List<PlayerScoreEntry> mejores, String vos, int tuyos, int perdidos) {
		MutableComponent cartel = Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(dorado("  " + ESTRELLA + " LOS QUE MAS GANARON\n\n"));

		if (mejores.isEmpty()) {
			cartel.append(gris("  Todavia no gano nadie ni un duelo.\n"));
		} else {
			int puesto = 1;
			for (PlayerScoreEntry entrada : mejores) {
				boolean sosVos = entrada.owner().equalsIgnoreCase(vos);
				int lugar = puesto++;
				cartel.append(apagado("  " + lugar + ". "))
						.append(Component.literal(entrada.owner())
								.withStyle(e -> e.withColor(sosVos ? MARCA : 0xffffff).withBold(sosVos)))
						.append(gris("  " + entrada.value()
								+ (entrada.value() == 1 ? " duelo" : " duelos") + "\n"));
			}
		}

		return cartel.append(Component.literal("\n"))
				.append(gris("  Vos: "))
				.append(Component.literal(tuyos + " ganados").withStyle(e -> e.withColor(BIEN)))
				.append(gris("  /  "))
				.append(Component.literal(perdidos + " perdidos").withStyle(e -> e.withColor(ERROR)))
				.append(Component.literal("\n"))
				.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	// --------------------------------------------------------------------- la arena

	public static Component laArena(Arena arena, boolean hayDuelo, List<String> zonasQuePisan) {
		return Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(dorado("  LA ARENA\n\n"))
				.append(gris("  " + arena.ancho() + " x " + arena.largo() + " de piso, "
						+ arena.alto() + " de alto\n"))
				.append(gris("  " + arena.volumen() + " bloques, de "
						+ arena.min.getX() + " " + arena.min.getY() + " " + arena.min.getZ()
						+ " a " + arena.max.getX() + " " + arena.max.getY() + " "
						+ arena.max.getZ() + "\n\n"))
				.append(gris("  Aparecen en  "))
				.append(Component.literal(redondo(arena.punto1)).withStyle(e -> e.withColor(BIEN)))
				.append(gris("  y  "))
				.append(Component.literal(redondo(arena.punto2)).withStyle(e -> e.withColor(ACENTO)))
				.append(Component.literal("\n\n"))
				.append(hayDuelo
						? rojo("  Ahora mismo se esta peleando.\n")
						: gris("  Libre.\n"))
				.append(zonaEncima(zonasQuePisan))
				.append(Component.literal("\n"))
				.append(comando("/pvp arena punto1", "que el que reta aparezca donde estas parado"))
				.append(comando("/pvp arena punto2", "lo mismo para el otro"))
				.append(comando("/pvp arena borrar", "sacarla; sin arena no se puede pelear"))
				.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	public static Component sinArenaTodavia() {
		return Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(dorado("  TODAVIA NO HAY ARENA\n\n"))
				.append(gris("  Hay dos formas, y las dos usan la misma varita.\n\n"))
				.append(Component.literal("  " + VINETA + " LA CORTA").withStyle(e -> e.withColor(BIEN)))
				.append(gris("  (con la zona que ya sabes marcar)\n"))
				.append(gris("    1. Marca una zona con el click DERECHO, como\n"))
				.append(gris("       siempre: una esquina y la opuesta.\n"))
				.append(gris("    2. Parate adentro y escribi "))
				.append(Component.literal("/pvp arena guardar").withStyle(e -> e.withColor(ACENTO)))
				.append(gris("\n    3. "))
				.append(Component.literal("Borra la zona").withStyle(e -> e.withColor(ERROR)))
				.append(gris(" con /sz remove, parado adentro.\n\n"))
				.append(Component.literal("  " + VINETA + " LA LARGA").withStyle(e -> e.withColor(MARCA)))
				.append(gris("  (sin crear ninguna zona)\n"))
				.append(gris("    click IZQUIERDO en una esquina de ABAJO,\n"))
				.append(gris("    click IZQUIERDO en la de ARRIBA opuesta, y\n"))
				.append(Component.literal("    /pvp arena guardar").withStyle(e -> e.withColor(ACENTO)))
				.append(Component.literal("\n\n"))
				.append(rojo("  El paso 3 de la corta no es opcional. "))
				.append(gris("Adentro de\n  una zona protegida nadie puede romper ni poner un\n"))
				.append(gris("  bloque, y al dueño de la zona las explosiones no le\n"))
				.append(gris("  hacen daño: con la zona puesta, los duelos no andan.\n\n"))
				.append(gris("  De una zona el alto lo pone el comando, porque las\n"))
				.append(gris("  zonas no tienen altura. Son 24 salvo que pidas otro:\n  "))
				.append(Component.literal("/pvp arena guardar 40").withStyle(e -> e.withColor(ACENTO)))
				.append(gris("   o  "))
				.append(Component.literal("/pvp arena zona <id> 40").withStyle(e -> e.withColor(ACENTO)))
				.append(Component.literal("\n"))
				.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	public static Component esquinaMarcada(int cual, net.minecraft.core.BlockPos donde) {
		return Component.empty()
				.append(Component.literal("  [Arena] ").withStyle(e -> e.withColor(MARCA).withBold(true)))
				.append(gris("Esquina " + cual + " en "))
				.append(Component.literal(donde.getX() + " " + donde.getY() + " " + donde.getZ())
						.withStyle(e -> e.withColor(ACENTO)))
				.append(gris(cual == 1 ? ".  Falta la otra." : "."));
	}

	public static Component listaParaGuardar(long bloques, int ancho, int largo, int alto) {
		return Component.empty()
				.append(gris("\n  La caja da " + ancho + " x " + largo + " x " + alto
						+ "  =  " + bloques + " bloques.\n  "))
				.append(boton("[ GUARDAR LA ARENA ]", "/pvp arena guardar", BIEN,
						"Dejar esta caja como la arena del servidor"))
				.append(gris("\n"));
	}

	public static Component cajaMuyGrande(long bloques) {
		return mal("Esa caja tiene " + bloques + " bloques y el tope son "
				+ Arena.TOPE_BLOQUES + ". De la arena se saca una foto entera antes de cada "
				+ "pelea para poder dejarla como estaba, y una foto mas grande no entra en la "
				+ "memoria del servidor. Marcala mas chica.");
	}

	public static Component arenaGuardada(Arena arena, String deDonde, List<String> zonasQuePisan) {
		return Component.empty()
				.append(bien("Arena guardada de " + deDonde + ": " + arena.ancho() + " x "
						+ arena.largo() + " de piso y " + arena.alto() + " de alto, de Y="
						+ arena.min.getY() + " a Y=" + arena.max.getY() + ".\n"))
				.append(zonaEncima(zonasQuePisan));
	}

	/**
	 * El aviso de que la arena quedo abajo de una zona protegida.
	 *
	 * Va aca arriba de todo y en rojo porque es el unico error de marcado que no se
	 * ve hasta la primera pelea, y cuando se ve parece un bug del duelo: los dos
	 * con TNT en la mano y el piso que no se rompe.
	 */
	private static MutableComponent zonaEncima(List<String> zonas) {
		if (zonas.isEmpty()) {
			return Component.empty()
					.append(bien("\n  " + TILDE + " No hay ninguna zona protegida encima.\n"));
		}
		MutableComponent cartel = Component.empty()
				.append(Component.literal("\n  " + CRUZ + " HAY UNA ZONA PROTEGIDA ENCIMA: ")
						.withStyle(e -> e.withColor(ERROR).withBold(true)))
				.append(Component.literal(String.join(", ", zonas))
						.withStyle(e -> e.withColor(ERROR)))
				.append(Component.literal("\n"))
				.append(gris("  Asi los duelos no andan: adentro de una zona nadie puede\n"))
				.append(gris("  romper ni poner un bloque, y al dueño las explosiones no le\n"))
				.append(gris("  hacen daño. Borrala parado adentro con "))
				.append(Component.literal("/sz remove").withStyle(e -> e.withColor(ACENTO)))
				.append(gris("\n  La arena ya quedo guardada: borrar la zona no la borra.\n"));
		return cartel;
	}

	public static Component nadaQueGuardar() {
		return Component.empty()
				.append(mal("No encontre de donde sacar la arena.\n"))
				.append(gris("  O estas parado adentro de una zona protegida "))
				.append(Component.literal("(/sz list)").withStyle(e -> e.withColor(ACENTO)))
				.append(gris(",\n  o marcaste las dos esquinas con la varita y el click\n"))
				.append(gris("  IZQUIERDO. El click derecho es el de las zonas.\n  "))
				.append(boton("[ COMO SE HACE ]", "/pvp arena", MARCA, "Ver las dos formas"));
	}

	public static Component zonaQueNoExiste(String id) {
		return mal("No hay ninguna zona que se llame " + id + ". Los ids salen de /sz list.");
	}

	public static Component puntoGuardado(int cual) {
		return bien("Listo: el jugador " + cual + " va a aparecer donde estas parado.");
	}

	public static Component arenaBorrada() {
		return aviso("Arena borrada. Hasta que marques otra no se puede pelear.");
	}


	// ------------------------------------------------------------------ los errores

	public static Component sinArena() {
		return mal("Todavia no hay arena para pelear. El que administra la marca con /pvp arena.");
	}

	public static Component aVosMismo() {
		return mal("A vos mismo no.");
	}

	public static Component noEstaConectado(String quien) {
		return mal(quien + " no esta conectado.");
	}

	public static Component yaEstaPeleando(String quien) {
		return mal(quien + " ya esta en un duelo. Espera a que termine.");
	}

	public static Component yaEstasPeleando() {
		return mal("Ya estas en un duelo.");
	}

	public static Component yaEstaEnLaCola(String quien) {
		return mal(quien + " ya tiene un duelo esperando turno.");
	}

	public static Component enPelea() {
		return mal("Estas EN PELEA. Esperá los 15 segundos desde el ultimo golpe "
				+ "y despues retalo: si no, entrar a la arena seria escaparse.");
	}

	public static Component elOtroEnPelea(String quien) {
		return mal(quien + " esta EN PELEA ahora mismo. Esperá a que se le vaya la marca.");
	}

	public static Component sinShards(int hacenFalta, int tenes) {
		return mal("Hacen falta " + hacenFalta + " shards y tenes " + tenes + ".");
	}

	public static Component elOtroSinShards(String quien, int hacenFalta) {
		return mal(quien + " no tiene los " + hacenFalta + " shards para esa apuesta.");
	}

	public static Component apuestaNegativa() {
		return mal("La apuesta va de 0 para arriba.");
	}

	public static Component noHayReto(String quien) {
		return mal("No tenes ningun reto de " + quien + ". Los retos duran "
				+ Invitaciones.SEGUNDOS + " segundos.");
	}

	public static Component sinRetos() {
		return mal("No te reto nadie.");
	}

	public static Component yaLoRetaste(String quien) {
		return mal("A " + quien + " ya lo retaste y todavia no contesto.");
	}

	public static Component noHayDuelo() {
		return mal("No se esta peleando nada ahora mismo.");
	}

	public static Component apuestasCerradas() {
		return mal("Las apuestas de este duelo ya cerraron.");
	}

	public static Component yaApostaste(String aQuien) {
		return mal("Ya le apostaste a " + aQuien + ". No se puede apostar a los dos.");
	}

	public static Component eseNoPelea(String quien) {
		return mal(quien + " no es ninguno de los dos que estan peleando.");
	}

	public static Component losQuePeleanNoApuestan() {
		return mal("El que pelea no apuesta. Ya te estas jugando lo tuyo adentro.");
	}

	public static Component noEstasPeleando() {
		return mal("No estas en ningun duelo.");
	}

	public static Component teSacamosDeLaArena() {
		return Component.empty()
				.append(Component.literal("  " + ESPADAS + " ").withStyle(e -> e.withColor(KILLS)))
				.append(rojo("Se esta peleando ahi adentro. "))
				.append(gris("Mirá desde afuera."));
	}

	public static Component enLaCola(int lugar) {
		return aviso(lugar == 1
				? "La arena esta ocupada: son los proximos."
				: "La arena esta ocupada: hay " + (lugar - 1) + " antes que ustedes.");
	}

	public static Component seFueElOtro(String quien) {
		return aviso(quien + " se desconecto, asi que no hay duelo.");
	}

	public static Component teDevolvimosLoTuyo() {
		return bien("Te quedo el inventario de un duelo sin terminar. Ya lo tenes de vuelta.");
	}

	/** Lo que contesta /pvp probar. */
	public static Component resultadoDeLaPrueba(int llenos, int casilleros, List<String> problemas) {
		MutableComponent cartel = Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(dorado("  LA PRUEBA DEL GUARDADO\n\n"))
				.append(gris("  Se guardo tu inventario como lo guardaria un duelo,\n"))
				.append(gris("  se escribio al archivo y se volvio a leer.\n\n"))
				.append(gris("  " + llenos + " casilleros con algo, de " + casilleros + ".\n\n"));

		if (problemas.isEmpty()) {
			cartel.append(Component.literal("  " + TILDE + " TODO VOLVIO IGUAL")
							.withStyle(e -> e.withColor(BIEN).withBold(true)))
					.append(gris("\n  No te toque nada: seguis con lo mismo que tenias.\n"));
		} else {
			cartel.append(Component.literal("  " + CRUZ + " " + problemas.size() + " PROBLEMA"
							+ (problemas.size() == 1 ? "" : "S"))
							.withStyle(e -> e.withColor(ERROR).withBold(true)))
					.append(Component.literal("\n\n"));
			for (String problema : problemas) {
				cartel.append(rojo("  " + VINETA + " " + problema + "\n"));
			}
			cartel.append(gris("\n  Avisale al que administra ANTES de pelear.\n"));
		}
		return cartel.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	public static Component inventarioSinDevolver() {
		return mal("El duelo no arranco: uno de los dos tiene un inventario de otro duelo "
				+ "sin devolver. Salí y volvé a entrar al servidor, que ahí te lo devuelve, "
				+ "y después vuelvan a retarse.");
	}

	// ------------------------------------------------------------------- el torneo

	public static Component torneoAbierto(int entrada, int anotados) {
		MutableComponent cartel = Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(dorado("  " + ESTRELLA + " SE ABRE UN TORNEO\n\n"))
				.append(gris("  Llave de uno contra uno hasta que quede uno solo.\n"))
				.append(gris("  Cada pelea es un duelo normal: kit al azar, igual\n"))
				.append(gris("  para los dos, y el resto apuesta.\n\n"));

		if (entrada > 0) {
			cartel.append(gris("  Entrada: "))
					.append(shards(entrada))
					.append(gris("   se las lleva todas el que gana\n\n"));
		} else {
			cartel.append(gris("  Entrada gratis.\n\n"));
		}

		return cartel
				.append(Component.literal("  "))
				.append(boton("[ ANOTARME ]", "/pvp torneo entrar", BIEN, "Entrar al torneo"))
				.append(gris("   anotados: " + anotados + "\n"))
				.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	public static Component torneoEstado(boolean abierto, boolean jugando, int entrada,
			List<String> anotados, int pozo, String vos) {
		MutableComponent cartel = Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(dorado("  " + ESTRELLA + " EL TORNEO\n\n"));

		if (!abierto && !jugando) {
			return cartel.append(gris("  No hay ningun torneo abierto.\n"))
					.append(gris("  Los abre el que administra el servidor.\n"))
					.append(apagado("  " + RAYA.repeat(26) + "\n"));
		}

		cartel.append(gris(jugando ? "  Ya empezo. Quedan:\n\n" : "  Anotados hasta ahora:\n\n"));
		for (String quien : anotados) {
			boolean sosVos = quien.equalsIgnoreCase(vos);
			cartel.append(Component.literal("  " + VINETA + " ").withStyle(e -> e.withColor(MARCA)))
					.append(Component.literal(quien)
							.withStyle(e -> e.withColor(sosVos ? MARCA : 0xffffff).withBold(sosVos)))
					.append(Component.literal("\n"));
		}
		if (anotados.isEmpty()) cartel.append(gris("  Todavia no se anoto nadie.\n"));

		cartel.append(Component.literal("\n"));
		if (entrada > 0) {
			cartel.append(gris("  Entrada "))
					.append(shards(entrada))
					.append(gris("   pozo "))
					.append(shards(pozo))
					.append(Component.literal("\n\n"));
		}

		if (abierto) {
			boolean anotado = anotados.stream().anyMatch(q -> q.equalsIgnoreCase(vos));
			cartel.append(Component.literal("  "))
					.append(anotado
							? boton("[ BORRARME ]", "/pvp torneo salir", ERROR, "Salir y recuperar la entrada")
							: boton("[ ANOTARME ]", "/pvp torneo entrar", BIEN, "Entrar al torneo"))
					.append(Component.literal("\n"));
		}
		return cartel.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	public static Component torneoAnotado(String quien, int cuantos) {
		return bien(quien + " se anoto al torneo. Van " + cuantos + ".");
	}

	public static Component torneoArranca(int cuantos, int pozo) {
		MutableComponent cartel = Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(dorado("  " + ESTRELLA + " ARRANCA EL TORNEO\n\n"))
				.append(gris("  " + cuantos + " anotados.\n"));
		if (pozo > 0) {
			cartel.append(gris("  El que gane se lleva "))
					.append(shards(pozo))
					.append(Component.literal("\n"));
		}
		return cartel.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	public static Component torneoCampeon(String quien, int pozo) {
		MutableComponent cartel = Component.empty()
				.append(apagado("\n  " + RAYA.repeat(26) + "\n"))
				.append(Component.literal("  " + ESTRELLA + " CAMPEON: ")
						.withStyle(e -> e.withColor(MARCA).withBold(true)))
				.append(Component.literal(quien.toUpperCase())
						.withStyle(e -> e.withColor(BIEN).withBold(true)))
				.append(Component.literal("\n"));
		if (pozo > 0) {
			cartel.append(gris("\n  Se lleva el pozo entero: "))
					.append(shards(pozo))
					.append(Component.literal("\n"));
		}
		return cartel.append(apagado("  " + RAYA.repeat(26) + "\n"));
	}

	public static Component torneoPasa(String quien, int quedan) {
		return bien(quien + " pasa de ronda. Quedan " + quedan + ".");
	}

	public static Component torneoLibre(String quien) {
		return aviso(quien + " pasa de ronda sin pelear: quedo sin rival.");
	}

	public static Component torneoCancelado() {
		return aviso("Se cancelo el torneo. A los anotados les vuelve la entrada.");
	}

	public static Component torneoYaHay() {
		return mal("Ya hay un torneo. Cancelalo antes de abrir otro: /pvp torneo cancelar");
	}

	public static Component torneoNoHay() {
		return mal("No hay ningun torneo abierto.");
	}

	public static Component torneoYaEmpezo() {
		return mal("El torneo ya empezo: no se entra ni se sale.");
	}

	public static Component torneoYaEstas() {
		return mal("Ya estas anotado.");
	}

	public static Component torneoNoEstas() {
		return mal("No estas anotado.");
	}

	public static Component torneoFaltanAnotados(int cuantos) {
		return mal("Con " + cuantos + " no hay torneo: hacen falta dos por lo menos.");
	}

	// -------------------------------------------------------------- las herramientas

	private static String redondo(net.minecraft.world.phys.Vec3 punto) {
		return Math.round(punto.x) + " " + Math.round(punto.y) + " " + Math.round(punto.z);
	}

	private static MutableComponent shards(int cuantos) {
		return Component.literal(SHARD + " " + cuantos)
				.withStyle(e -> e.withColor(SHARDS).withBold(true));
	}

	/** Una linea de "/comando  » para que sirve", clickeable. */
	private static MutableComponent comando(String comando, String paraQue) {
		return Component.empty()
				.append(Component.literal("  "))
				.append(boton(comando.trim(), comando, ACENTO, comando.endsWith(" ")
						? "Completá con lo que falta" : "Clickea para usarlo"))
				.append(gris("  " + FLECHA + " " + paraQue + "\n"));
	}

	private static MutableComponent boton(String texto, String comando, int color, String globito) {
		// Los que piden algo mas van con suggest_command, que lo deja escrito en el
		// chat para completar; los que se ejecutan solos, con run_command.
		ClickEvent click = comando.endsWith(" ")
				? new ClickEvent.SuggestCommand(comando)
				: new ClickEvent.RunCommand(comando);
		return Component.literal(texto).withStyle(estilo -> estilo
				.withColor(color)
				.withBold(true)
				.withClickEvent(click)
				.withHoverEvent(new HoverEvent.ShowText(gris(globito))));
	}

	public static MutableComponent bien(String texto) {
		return Component.literal("  " + texto).withStyle(e -> e.withColor(BIEN));
	}

	public static MutableComponent mal(String texto) {
		return Component.literal("  " + texto).withStyle(e -> e.withColor(ERROR));
	}

	public static MutableComponent aviso(String texto) {
		return Component.literal("  " + texto).withStyle(e -> e.withColor(MARCA));
	}

	private static MutableComponent dorado(String texto) {
		return Component.literal(texto).withStyle(e -> e.withColor(MARCA).withBold(true));
	}

	private static MutableComponent rojo(String texto) {
		return Component.literal(texto).withStyle(e -> e.withColor(KILLS));
	}

	private static MutableComponent gris(String texto) {
		return Component.literal(texto).withStyle(ChatFormatting.GRAY);
	}

	private static MutableComponent apagado(String texto) {
		return Component.literal(texto).withStyle(ChatFormatting.DARK_GRAY);
	}

	// ------------------------------------------------------- los titulos de la pelea

	/** El numero grande y verde de la cuenta regresiva. */
	public static Component numeroDeLaCuenta(int cuanto) {
		return Component.literal(String.valueOf(cuanto))
				.withStyle(e -> e.withColor(BIEN).withBold(true));
	}

	public static Component aPelear() {
		return Component.literal("¡PELEEN!").withStyle(e -> e.withColor(BIEN).withBold(true));
	}

	public static Component contra(String quien) {
		return Component.literal("contra " + quien).withStyle(e -> e.withColor(0xffffff));
	}

	public static Component tituloGanaste() {
		return Component.literal("GANASTE").withStyle(e -> e.withColor(MARCA).withBold(true));
	}

	public static Component tituloPerdiste() {
		return Component.literal("PERDISTE").withStyle(e -> e.withColor(KILLS).withBold(true));
	}

	public static Component tituloGano(String quien) {
		return Component.literal(quien.toUpperCase()).withStyle(e -> e.withColor(MARCA).withBold(true));
	}

	public static Component subtituloGano() {
		return Component.literal("gana el duelo").withStyle(e -> e.withColor(0xffffff));
	}

	public static Component tituloEmpate() {
		return Component.literal("EMPATE").withStyle(e -> e.withColor(MARCA).withBold(true));
	}

	public static Component elKit(String nombre, String comoSePelea) {
		return Component.literal(nombre + " " + FLECHA + " " + comoSePelea)
				.withStyle(e -> e.withColor(0xffffff));
	}

	/** La barra de arriba mientras se apuesta y mientras se pelea. */
	public static Component barraApuestas(String uno, String otro, int porUno, int porOtro) {
		return Component.empty()
				.append(Component.literal(SHARD + " APUESTAS ")
						.withStyle(e -> e.withColor(SHARDS).withBold(true)))
				.append(Component.literal(uno + " " + porUno).withStyle(e -> e.withColor(BIEN)))
				.append(gris("   vs   "))
				.append(Component.literal(otro + " " + porOtro).withStyle(e -> e.withColor(ACENTO)));
	}

	public static Component barraPelea(String uno, String otro) {
		return Component.empty()
				.append(Component.literal(uno).withStyle(e -> e.withColor(BIEN).withBold(true)))
				.append(Component.literal("  " + ESPADAS + "  ").withStyle(e -> e.withColor(KILLS)))
				.append(Component.literal(otro).withStyle(e -> e.withColor(ACENTO).withBold(true)));
	}

	/** Lo que cobra cada uno, en una linea para el log del que administra. */
	public static String repartoParaElLog(Map<String, Integer> cobran) {
		StringBuilder texto = new StringBuilder();
		cobran.forEach((quien, cuanto) -> {
			if (texto.length() > 0) texto.append(", ");
			texto.append(quien).append(" +").append(cuanto);
		});
		return texto.toString();
	}
}
