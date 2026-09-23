package pe.sobrinosdepepe.varita;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * El modo de una zona, colgado del `/sz` de Safe Zone.
 *
 *     /sz bichos   parado adentro de una zona: adentro no hay hostiles
 *     /sz modos    que zonas lo tienen puesto
 *
 * **Van adentro del `/sz` que ya existe y no de un comando propio**, porque es
 * donde el que administra lo va a buscar: es una opcion de una zona, y las zonas
 * son `/sz`. Brigadier deja hacerlo sin tocar el otro mod: `CommandNode.addChild`
 * fusiona los hijos cuando dos mods registran el mismo literal de arriba, y pisa
 * el `command` del nodo solo si el nuevo trae uno. El nuestro no trae ninguno en
 * la raiz, asi que el `/sz` pelado de Safe Zone sobrevive igual.
 *
 * **El `requires` de la raiz esta copiado del de Safe Zone a proposito**, y es lo
 * unico delicado de todo esto. De los dos nodos raiz que se fusionan sobrevive el
 * del que registro primero, con SU permiso, y el orden entre dos mods no lo
 * decide nadie. Si el nuestro fuera mas permisivo y ganara la carrera, todo el
 * `/sz` —`reload`, `removeall`, `limits`— le quedaria abierto a cualquiera.
 * Poniendo el mismo permiso el resultado es el mismo gane quien gane: nivel 2,
 * que es `PermissionLevel.GAMEMASTERS`, que es lo que pide
 * `AdminCommand.createRoot`.
 */
public final class ComandoZonas {
	private ComandoZonas() {}

	public static void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("sz")
				.requires(ComandoZonas::comoSafeZone)
				.then(Commands.literal("bichos").executes(ComandoZonas::alternar))
				.then(Commands.literal("modos").executes(ComandoZonas::listar)));
	}

	/** El mismo permiso que pide la raiz de `/sz` en Safe Zone. */
	private static boolean comoSafeZone(CommandSourceStack fuente) {
		return fuente.permissions()
				.hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
	}

	private static int alternar(CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		MinecraftServer servidor = contexto.getSource().getServer();

		Zonas.Zona zona = Zonas.dondeEsta(servidor, quien);
		if (zona == null) {
			quien.sendSystemMessage(Carteles.noEstasEnNinguna());
			return 0;
		}

		boolean queda = Modos.alternarBichos(zona.id());
		quien.sendSystemMessage(Carteles.modoCambiado(zona, queda));
		return 1;
	}

	private static int listar(CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		ServerPlayer quien = contexto.getSource().getPlayerOrException();
		MinecraftServer servidor = contexto.getSource().getServer();

		List<Zonas.Zona> conModo = new ArrayList<>();
		for (String id : Modos.conAlgoPuesto()) {
			Zonas.Zona zona = Zonas.porId(servidor, id);
			// Las que quedaron anotadas pero ya no existen no se muestran: pasa si se
			// borro la zona con el modo puesto. No molestan y no hay que limpiarlas.
			if (zona != null) conModo.add(zona);
		}
		quien.sendSystemMessage(Carteles.losModos(conModo));
		return 1;
	}
}
