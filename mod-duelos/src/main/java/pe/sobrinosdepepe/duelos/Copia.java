package pe.sobrinosdepepe.duelos;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * La foto de la arena antes de la pelea, y como se deshace todo despues.
 *
 * Sin esto el coliseo dura una pelea. Con crystals, TNT y perlas, dos jugadores
 * dejan el piso hecho un crater en tres minutos, y el que lo construyo lo tiene
 * que arreglar a mano cada vez. Con esto se puede romper todo lo que se quiera:
 * al terminar la arena vuelve bloque por bloque a como estaba.
 *
 * Se guarda la caja entera y no solo lo que cambia. Guardar solo lo que cambia
 * pedria enterarse de cada bloque que se rompe y de cada uno que se pone, y hay
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
	 * Lo que deja una pelea y se borra al terminar. Lo demas se deja donde esta:
	 * un coliseo tiene cuadros, soportes de armadura y a veces bichos, y borrarlos
	 * seria arruinarselo al que lo construyo.
	 */
	private static final List<Class<? extends Entity>> BASURA = List.of(
			Projectile.class,        // flechas, perlas, tridentes, cohetes
			EndCrystal.class,
			PrimedTnt.class,
			ExperienceOrb.class,
			FallingBlockEntity.class);

	private final Arena arena;
	private final BlockState[] bloques;

	private Copia(Arena arena, BlockState[] bloques) {
		this.arena = arena;
		this.bloques = bloques;
	}

	public static Copia sacar(ServerLevel nivel, Arena arena) {
		int ancho = arena.ancho();
		int alto = arena.alto();
		int largo = arena.largo();
		BlockState[] bloques = new BlockState[ancho * alto * largo];

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int i = 0;
		for (int y = 0; y < alto; y++) {
			for (int x = 0; x < ancho; x++) {
				for (int z = 0; z < largo; z++) {
					cursor.set(arena.min.getX() + x, arena.min.getY() + y, arena.min.getZ() + z);
					bloques[i++] = nivel.getBlockState(cursor);
				}
			}
		}
		return new Copia(arena, bloques);
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

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int arreglados = 0;
		int i = 0;
		for (int y = 0; y < alto; y++) {
			for (int x = 0; x < ancho; x++) {
				for (int z = 0; z < largo; z++) {
					BlockState estaba = bloques[i++];
					cursor.set(arena.min.getX() + x, arena.min.getY() + y, arena.min.getZ() + z);
					if (nivel.getBlockState(cursor) == estaba) continue;
					nivel.setBlock(cursor, estaba, Block.UPDATE_CLIENTS);
					arreglados++;
				}
			}
		}

		limpiar(nivel, arena);
		return arreglados;
	}

	/**
	 * Saca de la arena todo lo que dejo la pelea.
	 *
	 * Los items van con dos reglas distintas que el resto, y las dos importan:
	 *
	 *  - se borran **solo los del kit**, que se reconocen por la marca que les pone
	 *    `Kits`. Si se borraran todos, lo que alguien haya dejado tirado en el
	 *    coliseo antes de que empezara la pelea desapareceria sin aviso;
	 *  - se los busca en la arena **y diez bloques alrededor**. Un jugador no puede
	 *    salir de la arena, pero si puede tirar un item por arriba de la pared, y
	 *    esa es la unica forma que hay de que una pieza de netherita del kit termine
	 *    en la economia del servidor.
	 */
	public static void limpiar(ServerLevel nivel, Arena arena) {
		AABB caja = new AABB(
				arena.min.getX(), arena.min.getY(), arena.min.getZ(),
				arena.max.getX() + 1, arena.max.getY() + 1, arena.max.getZ() + 1);

		for (Class<? extends Entity> tipo : BASURA) {
			for (Entity entidad : nivel.getEntities(EntityTypeTest.forClass(tipo), caja, e -> true)) {
				entidad.discard();
			}
		}

		for (ItemEntity tirado : nivel.getEntities(EntityTypeTest.forClass(ItemEntity.class),
				caja.inflate(10), item -> Kits.esDelKit(item.getItem()))) {
			tirado.discard();
		}
	}
}
