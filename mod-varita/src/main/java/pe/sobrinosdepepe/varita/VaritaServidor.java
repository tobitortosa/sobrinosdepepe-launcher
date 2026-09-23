package pe.sobrinosdepepe.varita;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lo que le agregamos a Safe Zone. Son tres cosas y las tres cuelgan de lo que
 * ese mod ya hace.
 *
 * **1. La varita de zonas tambien protege bichos.** Click derecho con la varita
 * sobre un animal —o sobre cualquier cosa que no sea un jugador— y ese bicho pasa
 * a ser intocable: no lo mata nadie, ni un jugador, ni un creeper, ni el fuego,
 * ni una caida. Otro click derecho y vuelve a ser un bicho normal. Es para el
 * caballo, el perro o la vaca que uno no quiere perder.
 *
 * Es la misma varita del mod Safe Zone (un palo de depuracion, que no tiene
 * receta y solo puede dar un operador). Las dos cosas no se pisan: Safe Zone
 * trabaja con el click derecho sobre un BLOQUE, para marcar las esquinas de una
 * zona, y esto con el click derecho sobre una ENTIDAD, que es un evento aparte.
 *
 * Solo para operadores. No porque sea peligroso, sino porque un bicho que no se
 * puede matar es una decision del que administra. Los jugadores quedan afuera a
 * proposito: un jugador invulnerable en un servidor de PvP libre no es una
 * comodidad, es hacer trampa. Lo unico que igual lo mata: el vacio y el /kill,
 * que el juego deja pasar por encima de la invulnerabilidad para que nada quede
 * trabado para siempre.
 *
 * No hace falta guardar nada: "invulnerable" es una marca que Minecraft ya guarda
 * con cada entidad, asi que sobrevive a los reinicios sola.
 *
 * **2. `/sz bichos`: una zona donde no entran hostiles.** Ver `barrer`.
 *
 * **3. `/sz construir`: una zona donde construye cualquiera.** Ver
 * `ClaimManagerMixin`, que es donde de verdad pasa.
 */
public final class VaritaServidor implements DedicatedServerModInitializer {
	private static final Logger LOG = LoggerFactory.getLogger("varitadepepe");

	/**
	 * Cada cuantos ticks se barren las zonas sin bichos. Una vez por segundo: el
	 * que aparece se va igual en el acto por el enganche de abajo, y esto es para
	 * el que entro caminando.
	 */
	private static final int CADA_CUANTO_BARRO = 20;

	@Override
	public void onInitializeServer() {
		ServerLifecycleEvents.SERVER_STARTED.register(Modos::alArrancar);
		CommandRegistrationCallback.EVENT.register(
				(dispatcher, registros, entorno) -> ComandoZonas.registrar(dispatcher));

		UseEntityCallback.EVENT.register(this::varita);
		ServerEntityEvents.ENTITY_LOAD.register(this::alAparecer);
		ServerTickEvents.END_SERVER_TICK.register(this::barrer);

		LOG.info("La varita protege bichos, y las zonas tienen modos. El archivo es config/{}",
				Modos.ARCHIVO);
	}

	// -------------------------------------------------------------------- la varita

	private InteractionResult varita(Player jugador, Level mundo, net.minecraft.world.InteractionHand mano,
			Entity entidad, net.minecraft.world.phys.EntityHitResult donde) {
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

		// Que no se te desvanezca lo que acabas de proteger: los mobs que no estan
		// domesticados desaparecen solos cuando no hay nadie cerca.
		if (protegido && entidad instanceof Mob mob) mob.setPersistenceRequired();

		avisar(nivel, entidad, protegido);
		quien.sendSystemMessage(Carteles.cambio(entidad.getDisplayName(), protegido));
		LOG.info("{} {} a {} en {}", quien.getName().getString(),
				protegido ? "protegio" : "desprotegio",
				entidad.getType().toShortString(), entidad.blockPosition().toShortString());

		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------ las zonas sin bichos

	/**
	 * El hostil que aparece adentro de una zona sin bichos se va en el acto.
	 *
	 * Este es el que hace el trabajo: casi todos los bichos de una zona aparecen
	 * adentro, de noche o en un rincon oscuro, y asi no se los ve ni un tick. El
	 * barrido de abajo es para el otro caso, el que entro caminando desde afuera.
	 */
	private void alAparecer(Entity entidad, ServerLevel nivel) {
		if (!sobra(entidad)) return;
		if (!Modos.sinBichos(nivel.getServer(), entidad.getBlockX(), entidad.getBlockZ())) return;
		entidad.discard();
	}

	/**
	 * Una vez por segundo, las zonas sin bichos se quedan sin los que entraron
	 * caminando.
	 *
	 * La caja va de lo mas bajo a lo mas alto del mundo porque una zona de Safe
	 * Zone **es la columna entera**: su proteccion compara solo X y Z. Barrer solo
	 * a la altura del piso dejaria a los de la cueva de abajo, que son los que
	 * salen justo cuando uno esta minando adentro de su propia base.
	 */
	private void barrer(MinecraftServer servidor) {
		if (servidor.getTickCount() % CADA_CUANTO_BARRO != 0) return;

		for (Zonas.Zona zona : Zonas.todas(servidor)) {
			if (!Modos.sinBichos(zona.id())) continue;
			// Las zonas son solo del overworld: ClaimWandHandler no deja hacerlas en
			// ningun otro lado, asi que no hay que recorrer las demas dimensiones.
			ServerLevel nivel = servidor.overworld();
			AABB caja = new AABB(
					zona.minX(), nivel.getMinY(), zona.minZ(),
					zona.maxX() + 1, nivel.getMaxY() + 1, zona.maxZ() + 1);
			for (Mob bicho : nivel.getEntitiesOfClass(Mob.class, caja, VaritaServidor::sobra)) {
				bicho.discard();
			}
		}
	}

	/**
	 * Los que sobran: los hostiles que nadie protegio.
	 *
	 * Los animales se quedan —el caballo y las vacas de adentro de una base son de
	 * alguien— y el que se protegio a mano con la varita se queda aunque sea un
	 * hostil: `discard()` se lleva puesto al invulnerable igual, asi que sin
	 * preguntarlo un esqueleto de adorno se borraria solo al segundo de ponerlo.
	 */
	private static boolean sobra(Entity entidad) {
		return entidad instanceof Enemy && !entidad.isInvulnerable();
	}

	// ----------------------------------------------------------------- lo compartido

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
