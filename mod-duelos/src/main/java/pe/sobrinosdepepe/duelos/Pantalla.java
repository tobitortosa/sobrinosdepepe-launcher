package pe.sobrinosdepepe.duelos;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Un menu de cofre dibujado por el mod, con botones que hacen cosas.
 *
 * ## Por que existe, si ya hay menus
 *
 * Los otros doce menus del servidor los dibuja Inventory Menu leyendo JSON del
 * datapack (`servidor/generar-menus.py`), y estan perfectos para lo que son:
 * botones fijos. Pero un JSON no sabe quien esta conectado, y para elegir a una
 * persona hay que mostrar una lista que cambia cada minuto. Eso solo lo puede
 * dibujar algo que corra adentro del servidor, o sea un mod.
 *
 * Asi que esto convive con el otro sistema y no lo reemplaza: los menus de
 * botones fijos siguen en el datapack, y aca estan **solo los que muestran gente**.
 *
 * ## Como funciona
 *
 * Un `AbstractContainerMenu` con una caja de 9xN adentro. Las dos cosas que hay
 * que saber:
 *
 *  - **Nada se puede mover.** `clicked` no llama nunca al `super`, asi que el
 *    jugador no puede sacar un boton ni meter una espada. Sin esto, el primero
 *    que abra el menu se lleva las cabezas a su inventario.
 *  - **El click se atiende por slot**, y el que se pasa de la caja (o sea, el que
 *    cayo en el inventario del propio jugador) se ignora en silencio.
 *
 * En 26.1 la firma es `clicked(int, int, ContainerInput, Player)` y no la de
 * `ClickType` de las versiones viejas. Sale de mirar como lo hace EconomyCraft,
 * que ya tiene sus menus andando en esta version.
 */
public abstract class Pantalla extends AbstractContainerMenu {

	/**
	 * Lo que hace un boton cuando lo aprietan.
	 *
	 * `boton` es cual del mouse: 0 el izquierdo y 1 el derecho. Casi ningun boton
	 * lo mira, pero el que necesita dos acciones en un solo casillero —subir y
	 * bajar un numero— no tiene otro lugar de donde sacarlo.
	 */
	public interface Accion {
		void hacer(ServerPlayer quien, int boton);
	}

	private final SimpleContainer caja;
	private final Map<Integer, Accion> acciones = new HashMap<>();
	private final int filas;

	protected Pantalla(int id, Inventory inventario, int filas) {
		super(tipoDe(filas), id);
		this.filas = filas;
		this.caja = new SimpleContainer(filas * 9);

		for (int fila = 0; fila < filas; fila++) {
			for (int columna = 0; columna < 9; columna++) {
				addSlot(new Slot(caja, fila * 9 + columna, 8 + columna * 18, 18 + fila * 18));
			}
		}
		// El inventario del jugador tambien va: sin estos slots el cliente dibuja
		// la mitad de abajo vacia y el menu se ve roto.
		int arriba = 18 + filas * 18 + 13;
		for (int fila = 0; fila < 3; fila++) {
			for (int columna = 0; columna < 9; columna++) {
				addSlot(new Slot(inventario, columna + fila * 9 + 9,
						8 + columna * 18, arriba + fila * 18));
			}
		}
		for (int columna = 0; columna < 9; columna++) {
			addSlot(new Slot(inventario, columna, 8 + columna * 18, arriba + 58));
		}
	}

	private static MenuType<?> tipoDe(int filas) {
		return switch (filas) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	/** Pone un boton. La fila y la columna arrancan en 1, como en el datapack. */
	protected void boton(int fila, int columna, ItemStack que, Accion accion) {
		int slot = (fila - 1) * 9 + (columna - 1);
		if (slot < 0 || slot >= caja.getContainerSize()) return;
		caja.setItem(slot, que);
		if (accion != null) acciones.put(slot, accion);
	}

	protected void adorno(int fila, int columna, ItemStack que) {
		boton(fila, columna, que, null);
	}

	protected int filas() {
		return filas;
	}

	/**
	 * Borra todo y lo vuelve a dibujar.
	 *
	 * Es publico porque lo llama tambien quien cambia el estado desde afuera: cada
	 * jugador tiene su propio menu con su propia copia dibujada, asi que cuando
	 * alguien acepta hay que ir a refrescar las pantallas de los demas.
	 */
	public void redibujar() {
		caja.clearContent();
		acciones.clear();
		dibujar();
		broadcastChanges();
	}

	/** Cada pantalla pone aca sus botones. Se llama al abrir y al redibujar. */
	protected abstract void dibujar();

	@Override
	public void clicked(int slot, int boton, ContainerInput entrada, Player jugador) {
		// Nunca se llama al super: eso es lo que hace que no se pueda mover nada.
		if (!(jugador instanceof ServerPlayer quien)) return;
		Accion accion = acciones.get(slot);
		if (accion != null) accion.hacer(quien, boton);
	}

	@Override
	public ItemStack quickMoveStack(Player jugador, int slot) {
		// Shift+click tampoco mueve nada.
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player jugador) {
		return true;
	}

	/**
	 * Abre una pantalla.
	 *
	 * El titulo va como texto y no como Component armado porque todas las
	 * pantallas de este mod usan el mismo estilo, y asi no se puede olvidar.
	 */
	public static void abrir(ServerPlayer quien, String titulo,
			java.util.function.BiFunction<Integer, Inventory, Pantalla> como) {
		quien.openMenu(new SimpleMenuProvider(
				(id, inventario, jugador) -> {
					Pantalla pantalla = como.apply(id, inventario);
					pantalla.dibujar();
					return pantalla;
				},
				Component.literal(titulo)));
	}
}
