package pe.sobrinosdepepe.duelos;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Marcar la arena con la misma varita de las zonas.
 *
 * La varita es el palo de depuracion, el mismo que usa Safe Zone para marcar las
 * esquinas de una zona protegida. Los dos gestos conviven porque son distintos:
 *
 *   click DERECHO sobre un bloque  ->  Safe Zone, las esquinas de una zona
 *   click IZQUIERDO sobre un bloque ->  esto, las esquinas de la arena
 *   click DERECHO sobre un bicho   ->  el mod de la varita, lo hace intocable
 *
 * No hay forma de que se pisen, y esa es toda la razon por la que esto va con el
 * click izquierdo y no con el derecho: si fueran el mismo gesto, marcar una arena
 * reclamaria un terreno sin querer, o al reves, y el orden entre dos mods
 * escuchando el mismo evento no esta garantizado.
 *
 * En supervivencia el palo de depuracion no hace nada por su cuenta
 * (`canUseGameMasterBlocks()` da false), asi que cancelar el golpe no le saca
 * ninguna funcion a nadie. Y como es solo para operadores, al resto el click
 * izquierdo le sigue rompiendo bloques como siempre.
 *
 * La seleccion vive en memoria y por jugador. No se guarda: es de los proximos
 * treinta segundos, entre el primer click y el `/pvp arena guardar`.
 */
public final class Varita {
	/** Las dos esquinas que lleva marcadas cada uno. */
	private final Map<UUID, BlockPos[]> marcas = new HashMap<>();

	public void registrar() {
		AttackBlockCallback.EVENT.register((jugador, mundo, mano, donde, cara) -> {
			if (!(mundo instanceof ServerLevel nivel)) return InteractionResult.PASS;
			if (!(jugador instanceof ServerPlayer quien)) return InteractionResult.PASS;
			if (!jugador.getItemInHand(mano).is(Items.DEBUG_STICK)) return InteractionResult.PASS;
			if (!esOperador(nivel.getServer(), quien)) return InteractionResult.PASS;

			marcar(nivel, quien, donde);
			return InteractionResult.SUCCESS;
		});
	}

	/** Las dos esquinas que tiene marcadas ese jugador, o null si le falta alguna. */
	public BlockPos[] seleccionDe(ServerPlayer quien) {
		BlockPos[] esquinas = marcas.get(quien.getUUID());
		if (esquinas == null || esquinas[0] == null || esquinas[1] == null) return null;
		return esquinas;
	}

	public void olvidar(ServerPlayer quien) {
		marcas.remove(quien.getUUID());
	}

	private void marcar(ServerLevel nivel, ServerPlayer quien, BlockPos donde) {
		BlockPos[] esquinas = marcas.computeIfAbsent(quien.getUUID(), k -> new BlockPos[2]);

		// Se alterna: primer click la esquina 1, segundo la 2, tercero otra vez la 1.
		// Asi se corrige una esquina mal puesta sin ningun comando de por medio.
		int cual = (esquinas[0] == null || esquinas[1] != null) ? 0 : 1;
		if (cual == 0) {
			esquinas[0] = donde;
			esquinas[1] = null;
		} else {
			esquinas[1] = donde;
		}

		nivel.sendParticles(ParticleTypes.END_ROD,
				donde.getX() + 0.5, donde.getY() + 1.0, donde.getZ() + 0.5, 25, 0.25, 0.5, 0.25, 0.02);
		nivel.playSound(null, donde, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				0.8f, cual == 0 ? 1.0f : 1.4f);

		quien.sendSystemMessage(Carteles.esquinaMarcada(cual + 1, donde));

		if (esquinas[1] != null) {
			Arena tentativa = Arena.deLasEsquinas(nivel, esquinas[0], esquinas[1]);
			quien.sendSystemMessage(tentativa.volumen() > Arena.TOPE_BLOQUES
					? Carteles.cajaMuyGrande(tentativa.volumen())
					: Carteles.listaParaGuardar(tentativa.volumen(), tentativa.ancho(),
							tentativa.largo(), tentativa.alto()));
		}
	}

	/**
	 * En 26.1 los permisos ya no son un numero: se le pregunta a la lista de
	 * jugadores del servidor si esa cuenta esta opeada. Es el mismo chequeo que
	 * hace el mod de la varita para proteger bichos.
	 */
	static boolean esOperador(MinecraftServer servidor, ServerPlayer jugador) {
		return servidor != null && servidor.getPlayerList().isOp(jugador.nameAndId());
	}
}
