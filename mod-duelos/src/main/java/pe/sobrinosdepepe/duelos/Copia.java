package pe.sobrinosdepepe.duelos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La foto de la arena antes de la pelea, y como se deshace todo despues.
 *
 * Sin esto el coliseo dura una pelea. Con crystals, TNT y perlas, dos jugadores
 * dejan el piso hecho un crater en tres minutos, y el que lo construyo lo tiene
 * que arreglar a mano cada vez. Con esto se puede romper todo lo que se quiera:
 * al terminar la arena vuelve a como estaba, y vuelve **despues de cada pelea**,
 * no al final del torneo.
 *
 * La foto se saca al empezar cada duelo, asi que lo que alguien haya construido o
 * decorado ENTRE dos peleas queda adentro de la foto siguiente y se respeta. Lo
 * unico que se deshace es lo que cambio mientras se peleaba.
 *
 * Son tres cosas y las tres hacen falta para que de verdad quede igual:
 *
 *  1. **los bloques**, con el estado exacto que tenian;
 *  2. **lo que los bloques tienen adentro** —el contenido de un cofre, el texto
 *     de un cartel, el dibujo de un estandarte, la cabeza de alguien—, porque
 *     devolver el bloque sin eso te deja el cofre vacio y el cartel en blanco;
 *  3. **lo que quedo tirado**, que se barre entero.
 *
 * Los cuadros, los soportes de armadura y los bichos no se guardan aca: se los
 * hace intocables mientras dura el duelo (`cuidarLaDecoracion`), que es mas
 * barato y no tiene forma de duplicarlos.
 *
 * Se guarda la caja entera y no solo lo que cambia. Guardar solo lo que cambia
 * pediria enterarse de cada bloque que se rompe y de cada uno que se pone, y hay
 * formas de cambiar un bloque que ningun evento avisa: la explosion, el fuego que
 * se propaga, la arena que cae, el agua que corre. La foto entera no se puede
 * equivocar, y a cambio cuesta un arreglo de 400.000 casilleros en la RAM, que es
 * de donde sale el tope de `Arena.TOPE_BLOQUES`.
 *
 * Al devolver los bloques se usa `UPDATE_CLIENTS` pelado, sin avisarle a los
 * vecinos: si se avisara, devolver la arena dispararia en cadena la arena que
 * cae, el agua que corre y las antorchas que se caen, y el final de la reposicion
 * se pelearia con el principio. Como se devuelve TODO al estado que tenia, el
 * resultado ya es consistente y no hay nada que recalcular.
 */
public final class Copia {
	/**
	 * Lo que deja una pelea y se borra al terminar, adentro de la caja. Los items
	 * van aparte porque llevan una regla propia; ver `limpiar`.
	 */
	private static final List<Class<? extends Entity>> BASURA = List.of(
			Projectile.class,        // flechas, perlas, tridentes, cohetes
			EndCrystal.class,
			PrimedTnt.class,
			ExperienceOrb.class,
			FallingBlockEntity.class);

	private final Arena arena;
	private final BlockState[] bloques;
	/** Lo que tenian adentro los bloques que tienen adentro: por posicion. */
	private final Map<BlockPos, CompoundTag> contenidos;
	/** Los items que ya estaban tirados en la caja antes de la pelea. */
	private final Set<UUID> yaEstaban;

	private Copia(Arena arena, BlockState[] bloques, Map<BlockPos, CompoundTag> contenidos,
			Set<UUID> yaEstaban) {
		this.arena = arena;
		this.bloques = bloques;
		this.contenidos = contenidos;
		this.yaEstaban = yaEstaban;
	}

	public static Copia sacar(ServerLevel nivel, Arena arena) {
		int ancho = arena.ancho();
		int alto = arena.alto();
		int largo = arena.largo();
		BlockState[] bloques = new BlockState[ancho * alto * largo];
		Map<BlockPos, CompoundTag> contenidos = new HashMap<>();
		HolderLookup.Provider registros = nivel.registryAccess();

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int i = 0;
		for (int y = 0; y < alto; y++) {
			for (int x = 0; x < ancho; x++) {
				for (int z = 0; z < largo; z++) {
					cursor.set(arena.min.getX() + x, arena.min.getY() + y, arena.min.getZ() + z);
					BlockState estado = nivel.getBlockState(cursor);
					bloques[i++] = estado;

					// Solo los que tienen algo adentro, que en una arena son cuatro o
					// cinco: los carteles, los cofres de decoracion y los estandartes.
					if (!estado.hasBlockEntity()) continue;
					BlockEntity adentro = nivel.getBlockEntity(cursor);
					if (adentro != null) {
						contenidos.put(cursor.immutable(), adentro.saveWithFullMetadata(registros));
					}
				}
			}
		}
		// De quien es cada item que ya estaba tirado, para no barrerlo despues: el
		// PvP de este servidor es libre y en todo el mundo, asi que alguien pudo
		// morirse adentro del coliseo un minuto antes de que empezara el duelo.
		Set<UUID> yaEstaban = new HashSet<>();
		for (ItemEntity tirado : nivel.getEntities(EntityTypeTest.forClass(ItemEntity.class),
				cajaDe(arena), item -> true)) {
			yaEstaban.add(tirado.getUUID());
		}

		return new Copia(arena, bloques, contenidos, yaEstaban);
	}

	/**
	 * Devuelve la arena a como estaba y limpia lo que quedo tirado adentro.
	 *
	 * Devuelve la cantidad de bloques que hubo que arreglar, que es lo unico que
	 * se escribe en el log: sirve para saber si una pelea destrozo el lugar o si
	 * fue a puro espadazo.
	 */
	public int devolver(ServerLevel nivel) {
		int ancho = arena.ancho();
		int alto = arena.alto();
		int largo = arena.largo();
		HolderLookup.Provider registros = nivel.registryAccess();

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int arreglados = 0;
		int i = 0;
		for (int y = 0; y < alto; y++) {
			for (int x = 0; x < ancho; x++) {
				for (int z = 0; z < largo; z++) {
					BlockState estaba = bloques[i++];
					cursor.set(arena.min.getX() + x, arena.min.getY() + y, arena.min.getZ() + z);
					if (nivel.getBlockState(cursor) == estaba) continue;

					BlockPos donde = cursor.immutable();
					nivel.setBlock(donde, estaba, Block.UPDATE_CLIENTS);
					arreglados++;

					// Y lo que ese bloque tenia adentro. Sin esto, un cofre de
					// decoracion vuelve vacio y un cartel vuelve en blanco, que es
					// justo lo que se nota.
					CompoundTag tenia = contenidos.get(donde);
					if (tenia == null) continue;
					BlockEntity rehecho = BlockEntity.loadStatic(donde, estaba, tenia, registros);
					if (rehecho != null) nivel.setBlockEntity(rehecho);
				}
			}
		}

		barrer(nivel);
		return arreglados;
	}

	/**
	 * Barre lo que dejo la pelea, al terminar.
	 *
	 * Se lleva de la caja las flechas, los crystals, la TNT encendida, la
	 * experiencia, los bloques cayendo y **todos los items que no estaban antes de
	 * empezar**. Adentro no puede haber quedado nada de nadie —a la caja no entra
	 * el que no pelea, y los dos que pelean no tienen encima su inventario— asi que
	 * lo que aparecio durante la pelea es residuo de la pelea. Si no se barriera,
	 * cada bloque que alguien rompe del piso dejaria su drop tirado para siempre,
	 * porque el bloque vuelve a su lugar igual: seria una fabrica de items.
	 *
	 * Lo que YA estaba tirado cuando empezo el duelo no se toca. Eso es de alguien
	 * que se murio ahi adentro antes, y perderlo por haber muerto en el lugar
	 * equivocado seria bastante peor que dejar el piso un poco sucio.
	 */
	public void barrer(ServerLevel nivel) {
		AABB caja = cajaDe(arena);
		for (Class<? extends Entity> tipo : BASURA) {
			for (Entity entidad : nivel.getEntities(EntityTypeTest.forClass(tipo), caja, e -> true)) {
				entidad.discard();
			}
		}
		for (ItemEntity tirado : nivel.getEntities(EntityTypeTest.forClass(ItemEntity.class),
				caja, item -> !yaEstaban.contains(item.getUUID()))) {
			tirado.discard();
		}
		sacarLoDelKit(nivel, arena);
	}

	/**
	 * Lo que se limpia ANTES de sacar la foto: lo que quedo de la pelea anterior.
	 *
	 * Los items comunes no se tocan aca por lo mismo de arriba. Los del kit si,
	 * siempre y en todos lados: no son de nadie.
	 */
	public static void limpiar(ServerLevel nivel, Arena arena) {
		AABB caja = cajaDe(arena);
		for (Class<? extends Entity> tipo : BASURA) {
			for (Entity entidad : nivel.getEntities(EntityTypeTest.forClass(tipo), caja, e -> true)) {
				entidad.discard();
			}
		}
		sacarLoDelKit(nivel, arena);
	}

	/**
	 * Lo prestado, adentro de la arena y diez bloques alrededor.
	 *
	 * El margen es lo que importa: un jugador no puede salir de la arena, pero si
	 * puede tirar un item por arriba de la pared, y esa es la unica forma de que
	 * una pieza de netherita prestada termine en la economia del servidor. Lo que
	 * no tenga la marca de `Kits` no se toca: ahi afuera estan las gradas.
	 */
	private static void sacarLoDelKit(ServerLevel nivel, Arena arena) {
		for (ItemEntity tirado : nivel.getEntities(EntityTypeTest.forClass(ItemEntity.class),
				cajaDe(arena).inflate(10), item -> Kits.esDelKit(item.getItem()))) {
			tirado.discard();
		}
	}

	private static AABB cajaDe(Arena arena) {
		return arena.caja();
	}

	/**
	 * Hace intocable todo lo que decora la arena mientras dura la pelea: los
	 * cuadros, los soportes de armadura, las vitrinas y cualquier bicho que ande
	 * por ahi. Devuelve a quienes hubo que tocar, para poder dejarlos como estaban.
	 *
	 * Se los protege en vez de guardarlos y rehacerlos, que seria lo simetrico con
	 * los bloques, porque rehacer una entidad es crear una entidad: si algo sale
	 * mal en el medio quedan dos cuadros donde habia uno, y un coliseo con la
	 * decoracion duplicada es peor que uno con un cuadro roto. Asi, sencillamente,
	 * no se rompen.
	 *
	 * Los jugadores quedan afuera: los dos que pelean tienen que poder pegarse, y
	 * a los de afuera ya los deja mirar sin tocar `Duelos.dejarPegar`.
	 */
	public static List<UUID> cuidarLaDecoracion(ServerLevel nivel, Arena arena) {
		AABB caja = cajaDe(arena);
		List<UUID> cuidados = new ArrayList<>();
		for (Entity entidad : nivel.getEntities(EntityTypeTest.forClass(Entity.class), caja,
				e -> !(e instanceof Player) && !e.isInvulnerable())) {
			entidad.setInvulnerable(true);
			cuidados.add(entidad.getUUID());
		}
		return cuidados;
	}

	/** Les saca la proteccion a los que se la pusimos, y a nadie mas. */
	public static void soltarLaDecoracion(ServerLevel nivel, List<UUID> cuidados) {
		for (UUID cual : cuidados) {
			Entity entidad = nivel.getEntity(cual);
			if (entidad != null) entidad.setInvulnerable(false);
		}
	}
}
