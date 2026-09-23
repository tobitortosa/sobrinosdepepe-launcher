package pe.sobrinosdepepe.duelos.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pe.sobrinosdepepe.duelos.Duelos;

/**
 * El que pelea puede construir adentro de la arena, pero **no** es inmune a las
 * explosiones. Este mixin existe solo para deshacer un efecto de al lado del
 * otro, y sin el la mitad de las clases no pegan.
 *
 * **Lo que pasaba.** Safe Zone decide la inmunidad a explosiones con
 * `hasTrustedClaimAccess`, y ese metodo no pregunta si sos dueño ni si sos
 * trusted: pregunta **`canBuild(vos, donde estas parado) != DENIED`**. O sea que
 * usa el permiso de construir como si fuera la definicion de "este es de los de
 * casa". Y `ClaimManagerMixin` justamente hace que `canBuild` diga que si para
 * los dos que estan peleando, para que puedan poner bloques adentro del coliseo.
 * Resultado: los duelistas quedaban inmunes a las explosiones y los end crystals
 * del kit CRISTALERO se plantaban, reventaban y no le hacian ni un rasguño a
 * nadie. Lo mismo la TNT del BOMBARDERO.
 *
 * **Lo que hace.** Al que esta peleando adentro de la arena le contesta que no,
 * que no es de los de casa. Vuelve a ser exactamente lo que era antes de que
 * existiera el otro mixin: le pegan las explosiones y lo empujan.
 *
 * Con esto, adentro de una zona protegida un duelo queda asi:
 *
 *  - poner y romper bloques: **si**, para los dos que pelean (`ClaimManagerMixin`);
 *  - daño y empujon de las explosiones: **si**, como en cualquier lado (esto);
 *  - que la explosion rompa el piso: **no**, porque eso Safe Zone lo decide por
 *    posicion y no por jugador, y abrirlo dejaria que cualquiera volara el
 *    coliseo. La arena se devuelve entera al terminar igual.
 *
 * El `remap = false` no es decoracion: esto no apunta a una clase de Minecraft
 * sino a una de otro mod, y esos nombres no se remapean.
 */
@Mixin(targets = "com.simpleforapanda.safezone.manager.ClaimEntityProtection", remap = false)
public class ClaimEntityProtectionMixin {

	@Inject(method = "hasTrustedClaimAccess", at = @At("HEAD"), cancellable = true)
	private static void sdp$elQuePeleaNoEsDeLaCasa(ServerPlayer quien,
			CallbackInfoReturnable<Boolean> cir) {
		if (Duelos.dejaConstruir(quien, quien.blockPosition())) {
			cir.setReturnValue(false);
		}
	}
}
