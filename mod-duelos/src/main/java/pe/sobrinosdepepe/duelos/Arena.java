package pe.sobrinosdepepe.duelos;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * La cancha: una caja de bloques y los dos lugares donde aparece cada uno.
 *
 * La caja se marca con la varita —la misma de las zonas, el palo de depuracion—
 * dandole click IZQUIERDO a una esquina y despues a la otra. El click derecho no
 * se toca a proposito: ese es el de Safe Zone, que marca las esquinas de una zona
 * protegida, y si los dos gestos fueran el mismo marcar una arena reclamaria un
 * terreno sin querer.
 *
 * La caja no es decoracion. Es exactamente de donde no se sale mientras se pelea
 * y adentro de donde no entra nadie mas, asi que las dos esquinas van una ABAJO
 * del piso y la otra BIEN ARRIBA de la pared mas alta. Una caja del alto del piso
 * deja que el primero que salte quede "afuera" y lo devuelva de un tiron.
 *
 * Los dos puntos de aparicion salen solos de la caja: los extremos del lado mas
 * largo, en el medio del otro, parados sobre el primer piso firme que haya. Casi
 * siempre eso es lo que uno quiere; cuando no, /pvp arena punto1 y punto2 los
 * pisan con el lugar exacto donde esta parado el que los escribe.
 */
public final class Arena {
	/**
	 * Cuantos bloques puede tener la caja. El limite existe porque de la arena se
	 * saca una foto entera antes de cada pelea para poder dejarla como estaba: es
	 * un arreglo con un bloque por casillero, y ese arreglo vive en la RAM del
	 * servidor, que es justo lo que a este servidor le sobra menos.
	 *
	 * 400.000 son, por ejemplo, 100 x 100 de piso por 40 de alto. Un coliseo entra
	 * de sobra.
	 */
	public static final int TOPE_BLOQUES = 400_000;

	public final ResourceKey<Level> mundo;
	/** Las dos esquinas ya ordenadas: min tiene los tres valores mas chicos. */
	public final BlockPos min;
	public final BlockPos max;
	public final Vec3 punto1;
	public final Vec3 punto2;
	public final float giro1;
	public final float giro2;

	private Arena(ResourceKey<Level> mundo, BlockPos min, BlockPos max,
			Vec3 punto1, float giro1, Vec3 punto2, float giro2) {
		this.mundo = mundo;
		this.min = min;
		this.max = max;
		this.punto1 = punto1;
		this.giro1 = giro1;
		this.punto2 = punto2;
		this.giro2 = giro2;
	}

	/** La arena que se acaba de marcar, con los dos puntos calculados de la caja. */
	public static Arena deLasEsquinas(ServerLevel nivel, BlockPos a, BlockPos b) {
		BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
				Math.min(a.getZ(), b.getZ()));
		BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
				Math.max(a.getZ(), b.getZ()));

		// Los dos extremos del lado mas largo, en el medio del otro. Asi los dos
		// arrancan lo mas lejos que se puede, que es lo que hace que la cuenta de
		// 3 2 1 tenga sentido: nadie empieza pegado al otro.
		double centroX = (min.getX() + max.getX() + 1) / 2.0;
		double centroZ = (min.getZ() + max.getZ() + 1) / 2.0;
		boolean aLoLargoDeX = (max.getX() - min.getX()) >= (max.getZ() - min.getZ());

		Vec3 uno;
		Vec3 otro;
		if (aLoLargoDeX) {
			uno = new Vec3(min.getX() + 2.5, 0, centroZ);
			otro = new Vec3(max.getX() - 1.5, 0, centroZ);
		} else {
			uno = new Vec3(centroX, 0, min.getZ() + 2.5);
			otro = new Vec3(centroX, 0, max.getZ() - 1.5);
		}

		// Sin piso firme en esa columna se usa el fondo de la caja, que es el piso
		// que marco el que la marco.
		uno = conY(uno, enElPiso(nivel, uno, min, max), min.getY() + 1);
		otro = conY(otro, enElPiso(nivel, otro, min, max), min.getY() + 1);
		return new Arena(nivel.dimension(), min, max,
				uno, mirandoA(uno, otro), otro, mirandoA(otro, uno));
	}

	/** La misma arena con uno de los dos puntos cambiado a mano. */
	public Arena conPunto(int cual, Vec3 donde) {
		Vec3 uno = cual == 1 ? donde : punto1;
		Vec3 otro = cual == 1 ? punto2 : donde;
		return new Arena(mundo, min, max, uno, mirandoA(uno, otro), otro, mirandoA(otro, uno));
	}

	// ------------------------------------------------------------------ geometria

	public long volumen() {
		return (long) (max.getX() - min.getX() + 1)
				* (max.getY() - min.getY() + 1)
				* (max.getZ() - min.getZ() + 1);
	}

	public int ancho() {
		return max.getX() - min.getX() + 1;
	}

	public int largo() {
		return max.getZ() - min.getZ() + 1;
	}

	public int alto() {
		return max.getY() - min.getY() + 1;
	}

	public boolean esDe(Level nivel) {
		return nivel.dimension().equals(mundo);
	}

	/** Si esa posicion cae adentro de la caja. El bloque del borde cuenta entero. */
	public boolean contiene(Vec3 donde) {
		return donde.x >= min.getX() && donde.x < max.getX() + 1
				&& donde.y >= min.getY() && donde.y < max.getY() + 1
				&& donde.z >= min.getZ() && donde.z < max.getZ() + 1;
	}

	public boolean contiene(BlockPos donde) {
		return donde.getX() >= min.getX() && donde.getX() <= max.getX()
				&& donde.getY() >= min.getY() && donde.getY() <= max.getY()
				&& donde.getZ() >= min.getZ() && donde.getZ() <= max.getZ();
	}

	/**
	 * La caja, para preguntarle al mundo quien esta adentro. Va hasta el borde
	 * de afuera del ultimo bloque (`max + 1`) y no hasta su esquina: si no, lo
	 * que esta parado sobre la ultima fila queda medio adentro y medio afuera.
	 */
	public AABB caja() {
		return new AABB(
				min.getX(), min.getY(), min.getZ(),
				max.getX() + 1, max.getY() + 1, max.getZ() + 1);
	}

	public Vec3 centro() {
		return new Vec3((min.getX() + max.getX() + 1) / 2.0, max.getY(),
				(min.getZ() + max.getZ() + 1) / 2.0);
	}

	/**
	 * El punto de adentro mas cercano al que se fue. Es lo que devuelve al que
	 * tiro una perla contra la pared: queda pegado al borde por donde se iba, y no
	 * teletransportado de vuelta al principio, que seria regalarle la posicion al
	 * otro.
	 */
	public Vec3 devolverAdentro(Vec3 afuera) {
		double x = Math.min(Math.max(afuera.x, min.getX() + 0.3), max.getX() + 0.7);
		double y = Math.min(Math.max(afuera.y, min.getY()), max.getY());
		double z = Math.min(Math.max(afuera.z, min.getZ() + 0.3), max.getZ() + 0.7);
		return new Vec3(x, y, z);
	}

	/**
	 * A donde se manda al que no esta peleando y se metio igual: dos bloques
	 * afuera de la pared mas cercana, parado sobre lo primero firme que haya.
	 *
	 * Sale por la pared mas cercana y no por arriba ni por abajo: de un coliseo se
	 * sale caminando hacia las gradas, que es de donde vino.
	 */
	public Vec3 sacarAfuera(ServerLevel nivel, Vec3 adentro) {
		double aOeste = adentro.x - min.getX();
		double aEste = max.getX() + 1 - adentro.x;
		double aNorte = adentro.z - min.getZ();
		double aSur = max.getZ() + 1 - adentro.z;
		double menor = Math.min(Math.min(aOeste, aEste), Math.min(aNorte, aSur));

		double x = adentro.x;
		double z = adentro.z;
		if (menor == aOeste) x = min.getX() - 2.5;
		else if (menor == aEste) x = max.getX() + 3.5;
		else if (menor == aNorte) z = min.getZ() - 2.5;
		else z = max.getZ() + 3.5;

		Vec3 afuera = new Vec3(x, 0, z);
		Integer piso = enElPiso(nivel, afuera, min.below(4), max.above(8));
		// Afuera de la caja no hay ningun piso que se pueda dar por sentado: si en
		// esa columna no hay lugar para pararse —una pared, un cerro, el agua— se
		// cae a la superficie del mundo, que siempre existe. Sin esto, al que se
		// mete en la arena se lo podria mandar adentro de una pared.
		return conY(afuera, piso, nivel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(int) Math.floor(x), (int) Math.floor(z)));
	}

	/**
	 * Donde se para cada uno de un lado: repartidos en linea, mirando al otro lado.
	 *
	 * Los dos puntos marcados definen el **eje** de la pelea —uno enfrente del
	 * otro—, asi que los companeros se reparten sobre la **perpendicular** a ese
	 * eje, que es lo que hace que un 2v2 se vea como un 2v2 desde las gradas: dos
	 * de un lado, dos del otro, y no cuatro desparramados.
	 *
	 * Sale de la geometria y no de puntos marcados a mano, y eso es a proposito:
	 * marcar seis lugares por lado en cada cancha nueva son doce clicks y una
	 * planilla, y el dia que la cancha cambie hay que rehacerlos todos. Asi, marcar
	 * los dos de siempre alcanza para todos los modos.
	 *
	 * A cada uno se le busca el piso abajo: si el lugar que le toca cae sobre un
	 * pozo o una pared, se lo corre al punto del lado, que siempre sirve.
	 */
	public List<Vec3> puestos(ServerLevel nivel, boolean primerLado, int cuantos) {
		Vec3 centro = primerLado ? punto1 : punto2;
		List<Vec3> lugares = new ArrayList<>();
		if (cuantos <= 1) {
			lugares.add(centro);
			return lugares;
		}

		Vec3 eje = punto2.subtract(punto1);
		Vec3 aLoLargo = new Vec3(-eje.z, 0, eje.x);
		if (aLoLargo.lengthSqr() < 1.0E-6) {
			aLoLargo = new Vec3(0, 0, 1);
		}
		Vec3 perp = aLoLargo.normalize();

		for (int i = 0; i < cuantos; i++) {
			double corrimiento = (i - (cuantos - 1) / 2.0) * SEPARACION;
			Vec3 donde = centro.add(perp.scale(corrimiento));
			donde = devolverAdentro(donde);
			Integer piso = enElPiso(nivel, donde, min.below(2), max.above(4));
			lugares.add(piso == null ? centro : new Vec3(donde.x, piso, donde.z));
		}
		return lugares;
	}

	/** Cuanto se separan los companeros de un mismo lado. */
	private static final double SEPARACION = 3.0;

	private static Vec3 conY(Vec3 donde, Integer piso, int siNoHay) {
		return new Vec3(donde.x, piso != null ? piso : siNoHay, donde.z);
	}

	/**
	 * La misma X y Z, pero parado sobre el primer piso firme buscando de abajo
	 * hacia arriba adentro del rango de alturas que se le pase, y con dos bloques
	 * libres encima para que quepa un jugador.
	 *
	 * De abajo hacia arriba y no al reves porque el piso de una arena es el de
	 * abajo: buscando desde arriba, un coliseo techado deja a los dos peleando
	 * sobre el techo.
	 */
	private static Integer enElPiso(ServerLevel nivel, Vec3 donde, BlockPos desde, BlockPos hasta) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int x = (int) Math.floor(donde.x);
		int z = (int) Math.floor(donde.z);

		for (int y = desde.getY(); y <= hasta.getY(); y++) {
			cursor.set(x, y - 1, z);
			if (!nivel.getBlockState(cursor).isSolidRender()) continue;
			cursor.set(x, y, z);
			if (!nivel.getBlockState(cursor).isAir()) continue;
			cursor.set(x, y + 1, z);
			if (!nivel.getBlockState(cursor).isAir()) continue;
			return y;
		}
		return null;
	}

	/** El angulo con el que uno queda mirando al otro. */
	private static float mirandoA(Vec3 desde, Vec3 hacia) {
		double dx = hacia.x - desde.x;
		double dz = hacia.z - desde.z;
		return (float) Math.toDegrees(Math.atan2(-dx, dz));
	}

	// -------------------------------------------------------------------- archivo

	public JsonObject aJson() {
		JsonObject o = new JsonObject();
		o.addProperty("mundo", mundo.identifier().toString());
		o.addProperty("x1", min.getX());
		o.addProperty("y1", min.getY());
		o.addProperty("z1", min.getZ());
		o.addProperty("x2", max.getX());
		o.addProperty("y2", max.getY());
		o.addProperty("z2", max.getZ());
		o.addProperty("punto1x", punto1.x);
		o.addProperty("punto1y", punto1.y);
		o.addProperty("punto1z", punto1.z);
		o.addProperty("giro1", giro1);
		o.addProperty("punto2x", punto2.x);
		o.addProperty("punto2y", punto2.y);
		o.addProperty("punto2z", punto2.z);
		o.addProperty("giro2", giro2);
		return o;
	}

	public static Arena deJson(JsonObject o) {
		ResourceKey<Level> mundo = ResourceKey.create(Registries.DIMENSION,
				Identifier.parse(o.get("mundo").getAsString()));
		return new Arena(mundo,
				new BlockPos(o.get("x1").getAsInt(), o.get("y1").getAsInt(), o.get("z1").getAsInt()),
				new BlockPos(o.get("x2").getAsInt(), o.get("y2").getAsInt(), o.get("z2").getAsInt()),
				new Vec3(o.get("punto1x").getAsDouble(), o.get("punto1y").getAsDouble(),
						o.get("punto1z").getAsDouble()),
				o.get("giro1").getAsFloat(),
				new Vec3(o.get("punto2x").getAsDouble(), o.get("punto2y").getAsDouble(),
						o.get("punto2z").getAsDouble()),
				o.get("giro2").getAsFloat());
	}

	public ServerLevel nivel(MinecraftServer servidor) {
		return servidor.getLevel(mundo);
	}
}
