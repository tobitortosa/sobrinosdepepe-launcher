package pe.sobrinosdepepe.duelos;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Las clases con las que se pelea. Una al azar por duelo, y **la misma para los
 * dos**.
 *
 * Esa es la regla entera: el kit se arma UNA vez —con sus cantidades al azar
 * incluidas— y despues se le copia igual a cada uno. Si se armara dos veces, uno
 * podria salir con diez gapples y el otro con seis, y ahi ya no gana el que pelea
 * mejor. Por eso `armar()` devuelve un kit y `dar()` lo reparte, y nunca al reves.
 *
 * Que sea al azar es a proposito y es la mitad de la gracia: nadie se especializa
 * en una sola forma de pelear porque no sabe con que le va a tocar. El que sabe
 * de crystals un dia tiene que arreglarselas con un arco.
 *
 * Nada de esto se queda. Todo lo que sale de aca lleva la marca `sdp_duelo`
 * adentro, y al terminar el duelo se limpia lo que haya quedado tirado. El
 * inventario de verdad de cada uno vuelve entero; ver `Guardado`.
 */
public final class Kits {
	private Kits() {}

	/** Lo que hace reconocible un item del kit cuando quedo tirado en el piso. */
	public static final CompoundTag MARCA = marca();

	private static CompoundTag marca() {
		CompoundTag tag = new CompoundTag();
		tag.putByte("sdp_duelo", (byte) 1);
		return tag;
	}

	public static boolean esDelKit(ItemStack pila) {
		return pila.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).matchedBy(MARCA);
	}

	/** Un kit ya armado: lo puesto y lo que va en la mochila, en orden. */
	public record Kit(String nombre, String comoSePelea,
			Map<EquipmentSlot, ItemStack> puesto, List<ItemStack> mochila) {}

	/** Los bloques con los que se construye. Se elige uno y les toca a los dos. */
	private static final Item[] BLOQUES = {
			Items.COBBLESTONE, Items.OAK_PLANKS, Items.STONE_BRICKS, Items.DEEPSLATE_BRICKS};

	/**
	 * Como se llama cada clase, en el orden en que las arma `armar`. Es la lista
	 * que completa el tabulador de `/pvp <jugador> <clase>`.
	 */
	public static final List<String> NOMBRES = List.of(
			"GLADIADOR", "NETHERITA", "CRISTALERO", "ARQUERO",
			"PERLERO", "BOMBARDERO", "MAZAZO", "CUERO");

	/** Cuantas clases hay. Se usa para el cartel de /pvp. */
	public static final int CUANTAS = NOMBRES.size();

	/** Si ese nombre es una clase, mirando sin distinguir mayusculas. */
	public static boolean existe(String nombre) {
		return NOMBRES.stream().anyMatch(n -> n.equalsIgnoreCase(nombre));
	}

	/**
	 * Arma el kit del duelo. La semilla del azar sale del duelo, asi que los dos
	 * jugadores reciben exactamente lo mismo.
	 *
	 * `pedida` es la clase que eligio el que reto, o null para que salga una al
	 * azar, que es lo normal. Que se pueda elegir no rompe que sea pareja: los dos
	 * pelean con la misma igual, y al retado le llega escrita en el reto antes de
	 * apretar ACEPTAR. Elegir la clase es proponer una pelea, no imponerla.
	 *
	 * El bloque para construir sigue saliendo al azar aunque se elija la clase: es
	 * decoracion, no cambia como se pelea.
	 */
	public static Kit armar(HolderLookup.Provider registros, RandomSource azar, String pedida) {
		Item bloque = BLOQUES[azar.nextInt(BLOQUES.length)];
		int cual = azar.nextInt(CUANTAS);
		if (pedida != null) {
			for (int i = 0; i < CUANTAS; i++) {
				if (NOMBRES.get(i).equalsIgnoreCase(pedida)) cual = i;
			}
		}
		return switch (cual) {
			case 0 -> gladiador(registros, azar, bloque);
			case 1 -> netherita(registros, azar, bloque);
			case 2 -> cristalero(registros, azar);
			case 3 -> arquero(registros, azar, bloque);
			case 4 -> perlero(registros, azar, bloque);
			case 5 -> bombardero(registros, azar, bloque);
			case 6 -> mazazo(registros, azar, bloque);
			default -> cuero(azar, bloque);
		};
	}

	/** Le pone el kit encima al jugador. El inventario ya tiene que estar vacio. */
	public static void dar(ServerPlayer jugador, Kit kit) {
		kit.puesto().forEach((donde, pila) -> jugador.setItemSlot(donde, pila.copy()));
		for (int i = 0; i < kit.mochila().size() && i < 36; i++) {
			jugador.getInventory().setItem(i, kit.mochila().get(i).copy());
		}
		jugador.getInventory().setSelectedSlot(0);
		jugador.getInventory().setChanged();
		jugador.inventoryMenu.broadcastChanges();
	}

	// --------------------------------------------------------------- las ocho clases

	/**
	 * La unica clase que lleva escudo, y por eso la unica que necesita hacha: el
	 * hacha es lo que deshabilita el escudo del otro por cinco segundos. Sin ella
	 * los dos —que pelean con el mismo kit— se tapan y no se sacan vida, y el duelo
	 * lo termina el reloj en vez de la pelea. La regla es esa y vale para las que
	 * vengan: **si una clase tiene escudo, tiene hacha**.
	 */
	private static Kit gladiador(HolderLookup.Provider r, RandomSource azar, Item bloque) {
		return new Kit("GLADIADOR", "Espada, hacha y escudo. La pelea de toda la vida.",
				armadura(r, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS,
						Items.IRON_BOOTS, Enchantments.PROTECTION, 2,
						uno(Items.SHIELD)),
				List.of(
						enc(r, uno(Items.IRON_SWORD), Enchantments.SHARPNESS, 2),
						uno(Items.IRON_AXE),
						varios(Items.GOLDEN_APPLE, entre(azar, 5, 8)),
						varios(Items.ENDER_PEARL, entre(azar, 8, 12)),
						varios(bloque, entre(azar, 32, 64)),
						varios(Items.COOKED_BEEF, 16)));
	}

	private static Kit netherita(HolderLookup.Provider r, RandomSource azar, Item bloque) {
		return new Kit("NETHERITA", "Lo mejor que hay, de los dos lados. Con crystals.",
				armadura(r, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
						Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, Enchantments.PROTECTION, 3,
						ItemStack.EMPTY),
				List.of(
						enc(enc(r, uno(Items.NETHERITE_SWORD), Enchantments.SHARPNESS, 3),
								r, Enchantments.FIRE_ASPECT, 2),
						varios(Items.ENCHANTED_GOLDEN_APPLE, entre(azar, 2, 4)),
						varios(Items.GOLDEN_APPLE, entre(azar, 6, 10)),
						varios(Items.ENDER_PEARL, entre(azar, 8, 16)),
						// Unos pocos crystals y la obsidiana para apoyarlos. No son el arma
						// principal como en CRISTALERO, pero netherita con crystals es
						// exactamente lo que se pelea en Donut, y con esto sale dos de cada
						// ocho duelos en vez de uno.
						varios(Items.END_CRYSTAL, entre(azar, 4, 8)),
						varios(Items.OBSIDIAN, 16),
						varios(bloque, entre(azar, 32, 64)),
						varios(Items.COOKED_BEEF, 16)));
	}

	/**
	 * La clase de crystals, que es con lo que se pelea de verdad en los servidores
	 * grandes: se apoya obsidiana, se planta el crystal al lado del otro y se le
	 * pega con el hacha antes de que reaccione.
	 *
	 * Va con armadura de netherita y Blast Protection IV y no con diamante: sin eso
	 * el primer crystal que explota bien puesto termina el duelo en dos segundos y
	 * no hay pelea, hay sorteo. Y con perlas, que son las que dejan salir de un
	 * rincon cuando el otro te esta encerrando con obsidiana.
	 */
	private static Kit cristalero(HolderLookup.Provider r, RandomSource azar) {
		return new Kit("CRISTALERO", "Crystals y obsidiana. Se gana con la mano rapida.",
				armadura(r, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
						Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
						Enchantments.BLAST_PROTECTION, 4, ItemStack.EMPTY),
				List.of(
						enc(r, uno(Items.NETHERITE_AXE), Enchantments.SHARPNESS, 4),
						varios(Items.END_CRYSTAL, entre(azar, 16, 24)),
						varios(Items.OBSIDIAN, 64),
						varios(Items.ENDER_PEARL, entre(azar, 8, 12)),
						varios(Items.ENCHANTED_GOLDEN_APPLE, entre(azar, 1, 3)),
						varios(Items.GOLDEN_APPLE, entre(azar, 8, 12)),
						varios(Items.COOKED_BEEF, 16)));
	}

	private static Kit arquero(HolderLookup.Provider r, RandomSource azar, Item bloque) {
		return new Kit("ARQUERO", "De lejos. El que se deja acorralar pierde.",
				armadura(r, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS,
						Items.IRON_BOOTS, Enchantments.PROJECTILE_PROTECTION, 3,
						ItemStack.EMPTY),
				List.of(
						enc(enc(r, uno(Items.BOW), Enchantments.POWER, 4), r, Enchantments.PUNCH, 1),
						varios(Items.ARROW, 64),
						uno(Items.STONE_SWORD),
						varios(Items.ENDER_PEARL, entre(azar, 6, 10)),
						varios(Items.GOLDEN_APPLE, entre(azar, 4, 6)),
						varios(bloque, entre(azar, 16, 32)),
						varios(Items.COOKED_BEEF, 16)));
	}

	private static Kit perlero(HolderLookup.Provider r, RandomSource azar, Item bloque) {
		return new Kit("PERLERO", "Perlas y cargas de viento. Nunca estas donde te buscan.",
				armadura(r, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS,
						Items.IRON_BOOTS, Enchantments.FEATHER_FALLING, 4,
						ItemStack.EMPTY),
				List.of(
						enc(r, uno(Items.DIAMOND_SWORD), Enchantments.SHARPNESS, 3),
						varios(Items.ENDER_PEARL, entre(azar, 16, 24)),
						varios(Items.WIND_CHARGE, entre(azar, 32, 64)),
						varios(Items.GOLDEN_APPLE, entre(azar, 4, 8)),
						varios(bloque, 64),
						varios(Items.COOKED_BEEF, 16)));
	}

	private static Kit bombardero(HolderLookup.Provider r, RandomSource azar, Item bloque) {
		return new Kit("BOMBARDERO", "TNT y un mechero. El piso no va a quedar igual.",
				armadura(r, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS,
						Items.IRON_BOOTS, Enchantments.BLAST_PROTECTION, 4,
						ItemStack.EMPTY),
				List.of(
						enc(r, uno(Items.IRON_SWORD), Enchantments.SHARPNESS, 2),
						varios(Items.TNT, entre(azar, 24, 40)),
						uno(Items.FLINT_AND_STEEL),
						enc(r, uno(Items.IRON_PICKAXE), Enchantments.EFFICIENCY, 4),
						varios(Items.ENDER_PEARL, entre(azar, 6, 10)),
						varios(Items.GOLDEN_APPLE, entre(azar, 6, 10)),
						varios(bloque, 64),
						varios(Items.COOKED_BEEF, 16)));
	}

	private static Kit mazazo(HolderLookup.Provider r, RandomSource azar, Item bloque) {
		return new Kit("MAZAZO", "La maza pega mas cuanto mas alto saltes.",
				armadura(r, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS,
						Items.DIAMOND_BOOTS, Enchantments.FEATHER_FALLING, 4,
						ItemStack.EMPTY),
				List.of(
						enc(enc(r, uno(Items.MACE), Enchantments.DENSITY, 4), r, Enchantments.BREACH, 2),
						varios(Items.WIND_CHARGE, entre(azar, 32, 48)),
						varios(Items.ENDER_PEARL, entre(azar, 6, 10)),
						varios(Items.GOLDEN_APPLE, entre(azar, 6, 10)),
						varios(bloque, 64),
						varios(Items.COOKED_BEEF, 16)));
	}

	/**
	 * La clase pobre, que es la que hace que no todos los duelos se parezcan. Igual
	 * lleva perlas: sin nada con que moverse, el que arranca atras no tiene ninguna
	 * carta para jugar y eso no es una pelea pareja, es una pelea corta.
	 */
	private static Kit cuero(RandomSource azar, Item bloque) {
		return new Kit("CUERO", "Casi nada encima. Se termina rapido.",
				sinEncantar(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS,
						Items.LEATHER_BOOTS),
				List.of(
						item(Items.STONE_SWORD, 1),
						item(Items.GOLDEN_APPLE, entre(azar, 3, 5)),
						item(Items.ENDER_PEARL, entre(azar, 3, 5)),
						item(bloque, entre(azar, 16, 32)),
						item(Items.COOKED_BEEF, 16)));
	}

	// ------------------------------------------------------------------ las piezas

	private static Map<EquipmentSlot, ItemStack> armadura(HolderLookup.Provider r,
			Item casco, Item peto, Item pantalon, Item botas,
			ResourceKey<Enchantment> encantamiento, int nivel, ItemStack manoIzquierda) {
		Map<EquipmentSlot, ItemStack> puesto = new EnumMap<>(EquipmentSlot.class);
		puesto.put(EquipmentSlot.HEAD, enc(r, uno(casco), encantamiento, nivel));
		puesto.put(EquipmentSlot.CHEST, enc(r, uno(peto), encantamiento, nivel));
		puesto.put(EquipmentSlot.LEGS, enc(r, uno(pantalon), encantamiento, nivel));
		puesto.put(EquipmentSlot.FEET, enc(r, uno(botas), encantamiento, nivel));
		if (!manoIzquierda.isEmpty()) puesto.put(EquipmentSlot.OFFHAND, manoIzquierda);
		return puesto;
	}

	private static Map<EquipmentSlot, ItemStack> sinEncantar(Item casco, Item peto,
			Item pantalon, Item botas) {
		Map<EquipmentSlot, ItemStack> puesto = new EnumMap<>(EquipmentSlot.class);
		puesto.put(EquipmentSlot.HEAD, item(casco, 1));
		puesto.put(EquipmentSlot.CHEST, item(peto, 1));
		puesto.put(EquipmentSlot.LEGS, item(pantalon, 1));
		puesto.put(EquipmentSlot.FEET, item(botas, 1));
		return puesto;
	}

	private static ItemStack uno(Item item) {
		return item(item, 1);
	}

	private static ItemStack varios(Item item, int cuantos) {
		return item(item, cuantos);
	}

	/** Un item del kit: siempre con la marca, que es lo que lo hace desechable. */
	private static ItemStack item(Item item, int cuantos) {
		ItemStack pila = new ItemStack(item, cuantos);
		pila.set(DataComponents.CUSTOM_DATA, CustomData.of(MARCA.copy()));
		return pila;
	}

	private static ItemStack enc(HolderLookup.Provider r, ItemStack pila,
			ResourceKey<Enchantment> cual, int nivel) {
		pila.enchant(r.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(cual), nivel);
		return pila;
	}

	private static ItemStack enc(ItemStack pila, HolderLookup.Provider r,
			ResourceKey<Enchantment> cual, int nivel) {
		return enc(r, pila, cual, nivel);
	}

	private static int entre(RandomSource azar, int desde, int hasta) {
		return desde + azar.nextInt(hasta - desde + 1);
	}

	/** Las cosas de un kit, para el cartel que lo anuncia. */
	public static List<String> resumen(Kit kit) {
		List<String> lineas = new ArrayList<>();
		for (ItemStack pila : kit.mochila()) {
			if (pila.isEmpty()) continue;
			lineas.add((pila.getCount() > 1 ? pila.getCount() + "x " : "")
					+ pila.getHoverName().getString());
		}
		return lineas;
	}
}
