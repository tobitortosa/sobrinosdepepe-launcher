package pe.sobrinosdepepe.duelos.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pe.sobrinosdepepe.duelos.Duelos;

/**
 * El agujero: los dos que estan peleando pueden poner y romper bloques adentro
 * de la arena, aunque el coliseo este adentro de una zona de Safe Zone.
 *
 * **El problema.** Safe Zone no toca el daño entre jugadores —adentro de una zona
 * dos se siguen pegando igual— pero si cancela poner y romper bloques al que no
 * es dueño ni trusted. Y los kits de duelo son de poner bloques: obsidiana y
 * crystals el CRISTALERO, TNT el BOMBARDERO, y los ocho traen bloques para
 * taparse. Con el coliseo adentro del spawn protegido no se podia jugar ninguno.
 *
 * **Por que un mixin y no un evento nuestro.** Los eventos de Fabric se cancelan
 * si CUALQUIERA de los que escucha dice que no, y no hay forma de des-cancelar lo
 * que ya cancelo otro mod. Safe Zone escucha romper, poner y usar bloques y dice
 * que no antes de que nosotros lleguemos a opinar. La unica forma de abrirle un
 * agujero a una zona es entrar adentro de Safe Zone y contestarle distinto.
 *
 * **Por que `canBuild` y no `getClaimAt`.** `getClaimAt` seria mas corto y
 * apagaria la zona entera en ese punto, pero **no sabe quien pregunta**: recibe
 * una posicion y nada mas. Y el que pregunta es justo lo que hay que mirar, o el
 * agujero se lo lleva puesto tambien el de las gradas y el coliseo se puede picar
 * entre pelea y pelea. `canBuild` recibe el jugador.
 *
 * Lo que eso cuesta: las explosiones van por `getClaimAt` y no por aca, asi que
 * la TNT y los crystals siguen sin romper el piso de la arena. Se puede poner la
 * TNT, se puede prender y **le hace daño al otro igual**, que es de lo que se
 * trata la pelea; lo unico que no pasa es el crater. Es el precio de que nadie
 * mas pueda tocar el coliseo, y es barato: la arena se fotografia antes de cada
 * duelo y vuelve entera al terminar, asi que el crater duraba lo que duraba la
 * pelea.
 *
 * **Si Safe Zone no esta puesto esto no hace nada.** Mixin avisa en el log que no
 * encontro la clase y sigue de largo, que es lo que se quiere: el mod de duelos
 * tiene que andar en un servidor sin zonas.
 *
 * El `remap = false` no es decoracion: esto no apunta a una clase de Minecraft
 * sino a una de otro mod, y esos nombres no se remapean.
 */
@Mixin(targets = "com.simpleforapanda.safezone.manager.ClaimManager", remap = false)
public class ClaimManagerMixin {
	private static final Logger LOG = LoggerFactory.getLogger("duelosdepepe");

	/**
	 * El valor de `PermissionResult.ADMIN_BYPASS`, buscado una sola vez.
	 *
	 * Va por reflexion —el mismo camino que usa el mod de acceso con EasyAuth— para
	 * no atarnos al jar de Safe Zone: sin eso habria que tener su jar para
	 * compilar, y el dia que cambie de version no compilaria mas. Es el valor que
	 * Safe Zone le da al operador que pasa por encima de una zona ajena, que es
	 * exactamente lo que estamos haciendo, y es el unico que ademas le ahorra el
	 * aviso al dueño de la zona.
	 */
	private static Object permiso;
	private static boolean buscado;

	@Inject(method = "canBuild", at = @At("HEAD"), cancellable = true)
	private void sdp$losQuePeleanConstruyen(ServerPlayer quien, BlockPos donde,
			CallbackInfoReturnable<Object> cir) {
		if (!Duelos.dejaConstruir(quien, donde)) return;

		Object si = permiso();
		if (si != null) cir.setReturnValue(si);
	}

	private static Object permiso() {
		if (buscado) return permiso;
		buscado = true;
		try {
			permiso = Class.forName("com.simpleforapanda.safezone.data.PermissionResult")
					.getField("ADMIN_BYPASS").get(null);
		} catch (Exception e) {
			// Sin esto el duelo adentro de una zona se pelea sin poder poner bloques,
			// que es como estaba antes. Se avisa fuerte y no se rompe nada mas.
			LOG.error("No encontre PermissionResult.ADMIN_BYPASS de Safe Zone. "
					+ "Adentro de una zona protegida los duelos no van a poder poner bloques.", e);
		}
		return permiso;
	}
}
