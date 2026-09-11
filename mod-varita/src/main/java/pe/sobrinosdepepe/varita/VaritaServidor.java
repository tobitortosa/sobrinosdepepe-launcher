package pe.sobrinosdepepe.varita;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * La varita de zonas tambien protege bichos.
 *
 * Click derecho con la varita sobre un animal —o sobre cualquier cosa que no sea
 * un jugador— y ese bicho pasa a ser intocable: no lo mata nadie, ni un jugador,
 * ni un creeper, ni el fuego, ni una caida. Otro click derecho y vuelve a ser un
 * bicho normal. Es para el caballo, el perro o la vaca que uno no quiere perder.
 *
 * **Es la misma varita del mod Safe Zone** (un palo de depuracion, que no tiene
 * receta y solo puede dar un operador). Las dos cosas no se pisan: Safe Zone
 * trabaja con el click derecho sobre un BLOQUE, para marcar las esquinas de una
 * zona, y esto con el click derecho sobre una ENTIDAD, que es un evento aparte.
 *
 * **Solo para operadores.** No porque sea peligroso, sino porque un bicho que no
 * se puede matar es una decision del que administra el servidor. Al que no es op
 * no le pasa nada: el click derecho sigue de largo como siempre.
 *
 * **Los jugadores quedan afuera a proposito.** Un jugador invulnerable en un
 * servidor de PvP libre no es una comodidad, es hacer trampa: no se le puede
 * quitar la plata ni el equipo que es de lo que vive el servidor.
 *
 * Lo unico que igual lo mata: el vacio y el /kill. El juego los deja pasar por
 * encima de la invulnerabilidad a proposito, para que nada quede trabado para
 * siempre. Por eso empujar un bicho protegido al vacio del End todavia lo mata.
 *
 * No hace falta guardar nada en ningun archivo: "invulnerable" es una marca que
 * Minecraft ya guarda con cada entidad, asi que sobrevive a los reinicios sola.
 */
public final class VaritaServidor implements DedicatedServerModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("varitadepepe");

	@Override
	public void onInitializeServer() {
		LOG.info("La varita de zonas tambien protege bichos: click derecho sobre un animal");

		UseEntityCallback.EVENT.register((jugador, mundo, mano, entidad, donde) -> {
			if (!(mundo instanceof ServerLevel nivel)) return InteractionResult.PASS;
			if (!(jugador instanceof ServerPlayer quien)) return InteractionResult.PASS;
			if (!jugador.getItemInHand(mano).is(Items.DEBUG_STICK)) return InteractionResult.PASS;
			if (!esOperador(nivel.getServer(), quien)) return InteractionResult.PASS;

			// Un jugador protegido rompe el PvP entero, que es de lo que vive esto.
			if (entidad instanceof Player) {
				quien.sendSystemMessage(Carteles.jugadorNo());
				return InteractionResult.SUCCESS;
			}

			boolean protegido = !entidad.isInvulnerable();
			entidad.setInvulnerable(protegido);

			// Que no se te desvanezca lo que acabas de proteger: los mobs que no
			// estan domesticados desaparecen solos cuando no hay nadie cerca.
			if (protegido && entidad instanceof Mob mob) mob.setPersistenceRequired();

			avisar(nivel, entidad, protegido);
			quien.sendSystemMessage(Carteles.cambio(entidad.getDisplayName(), protegido));
			LOG.info("{} {} a {} en {}", quien.getName().getString(),
					protegido ? "protegio" : "desprotegio",
					entidad.getType().toShortString(), entidad.blockPosition().toShortString());

			return InteractionResult.SUCCESS;
		});
	}

	/**
	 * En 26.1 los permisos ya no son un numero: se le pregunta a la lista de
	 * jugadores del servidor si esa cuenta esta opeada.
	 */
	private static boolean esOperador(MinecraftServer servidor, ServerPlayer jugador) {
		return servidor != null && servidor.getPlayerList().isOp(jugador.nameAndId());
	}

	/** El chispazo y el ruidito, que es como se sabe que funciono sin leer nada. */
	private static void avisar(ServerLevel nivel, Entity entidad, boolean protegido) {
		nivel.sendParticles(
				protegido ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.SMOKE,
				entidad.getX(), entidad.getY() + entidad.getBbHeight() * 0.6, entidad.getZ(),
				12, 0.4, 0.4, 0.4, 0.02);

		nivel.playSound(null, entidad.blockPosition(),
				protegido ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.FIRE_EXTINGUISH,
				SoundSource.PLAYERS, 0.7f, 1.0f);
	}
}
