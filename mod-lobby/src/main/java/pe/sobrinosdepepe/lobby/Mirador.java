package pe.sobrinosdepepe.lobby;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;

import java.util.Set;

/**
 * El modo de recorrer el coliseo: se entra de espectador y se sale con `/lobby`.
 *
 * Es para mirar la cancha, no para jugar. De espectador se vuela, se atraviesan
 * las paredes y no se puede tocar nada, que es exactamente lo que hace falta para
 * darse una vuelta sin ensuciar nada ni que te maten.
 *
 * Tres cosas que no son obvias y por las que este archivo existe:
 *
 *  1. **De espectador no se puede usar la perla.** El juego no dispara el click
 *     derecho de un item para el que mira, asi que el menu no abre y el unico
 *     camino de vuelta es escribir `/lobby`. Por eso se lo dice al entrar, en el
 *     chat y bien claro.
 *  2. **Hay que encerrarlo.** De espectador se cruza el mundo entero volando, y
 *     el coliseo esta a 500.000 bloques del spawn: el que se va derecho termina
 *     en cualquier lado sin forma de volver. El tick lo devuelve a las gradas
 *     apenas sale de la caja.
 *  3. **Al salir siempre queda en survival.** La primera version le devolvia el
 *     modo que tenia antes, para no romperle el creativo a los operadores. Estuvo
 *     mal: el que entraba a mirar desde el creativo volvia al lobby en creativo y
 *     seguia volando, apretaba SURVIVAL y entraba al mundo volando tambien. Del
 *     lobby se sale a jugar, y jugando no se vuela. El operador que quiera su
 *     creativo se lo pone con `/gamemode`, que es un comando que ya sabe escribir.
 */
final class Mirador {

	/** La marca que dice "a este lo pusimos de espectador nosotros". */
	static final String MARCA = "sdp_mirando";

	/**
	 * Lo deja de espectador arriba de la cancha.
	 *
	 * No le toca el inventario ni le anota nada: sus cosas ya estan guardadas
	 * desde que entro al lobby, y este modo no se las saca ni se las devuelve.
	 */
	void entrar(ServerPlayer quien, MinecraftServer servidor) {
		quien.addTag(MARCA);
		alCentro(quien, servidor);
		quien.setGameMode(GameType.SPECTATOR);
	}

	/** Si lo pusimos nosotros de espectador. */
	static boolean mirando(ServerPlayer quien) {
		return quien.entityTags().contains(MARCA);
	}

	/**
	 * Lo saca del modo espectador y le quita la marca. No lo mueve: de eso se
	 * ocupa quien llama, que es `/lobby`.
	 */
	void salir(ServerPlayer quien) {
		if (!mirando(quien)) return;
		quien.removeTag(MARCA);
		quien.setGameMode(GameType.SURVIVAL);
		quien.setDeltaMovement(0, 0, 0);
		quien.resetFallDistance();
	}

	/**
	 * Devuelve a las gradas al que se fue volando de la caja del coliseo.
	 *
	 * Corre una vez por tick sobre los marcados, que normalmente son cero. El que
	 * esta adentro no se entera de que esto existe.
	 */
	void tick(MinecraftServer servidor) {
		for (ServerPlayer quien : servidor.getPlayerList().getPlayers()) {
			if (!mirando(quien)) continue;
			if (LobbyServidor.enColiseo(quien)) continue;

			// Marcado y en el lobby: alguien lo saco del coliseo sin pasar por
			// `salir`. Se le limpia la marca en vez de traerlo de vuelta, porque
			// traerlo seria mandarlo al coliseo veinte veces por segundo mientras
			// el mod del lobby lo devuelve al patio. Un ida y vuelta por tick es
			// exactamente el bug que ya se pago una vez con `execute in`.
			if (LobbyServidor.enLobby(quien)) {
				salir(quien);
				continue;
			}

			alCentro(quien, servidor);
			Carteles.elColiseoTermina(quien);
		}
	}

	/**
	 * Al que entra marcado se le devuelve el modo de juego, este donde este.
	 *
	 * Pasa cuando el servidor se cae con alguien mirando: la marca queda escrita
	 * en su archivo y el modo de juego tambien, asi que sin esto vuelve de
	 * espectador para siempre.
	 */
	void alEntrar(ServerPlayer quien) {
		if (mirando(quien)) salir(quien);
	}

	private void alCentro(ServerPlayer quien, MinecraftServer servidor) {
		quien.teleportTo(servidor.overworld(),
				LobbyServidor.CX, LobbyServidor.CY, LobbyServidor.CZ,
				Set.<Relative>of(), LobbyServidor.CGIRO, LobbyServidor.CMIRA, false);
		quien.setDeltaMovement(0, 0, 0);
		quien.resetFallDistance();
	}
}
