package pe.sobrinosdepepe.lobby;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.storage.LevelData;

import java.util.Set;

/**
 * Los dos comandos: `/lobby` y `/survival`.
 *
 * `/survival` es lo mismo que aprieta el boton del menu de la perla, y existe
 * suelto por si alguien tira la perla o prefiere escribir.
 *
 * Ninguno lleva `requires`: los usa cualquiera sin ser operador. Los dos son para
 * jugadores y no hacen nada desde la consola, porque mueven a quien los escribe.
 *
 * `/survival` no lleva enfriamiento ni bloqueo por combate y es a proposito: en el
 * lobby no se puede pelear, y el que acaba de entrar no tiene que esperar para
 * jugar. El que si lleva bloqueo es `/lobby`, que desde el Survival seria un boton
 * de escape gratis en medio de una pelea.
 */
final class ComandoLobby {

	private final Vuelta vuelta;
	private final Mirador mirador;

	ComandoLobby(Vuelta vuelta, Mirador mirador) {
		this.vuelta = vuelta;
		this.mirador = mirador;
	}

	void registrar(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("lobby").executes(this::alLobby));
		dispatcher.register(Commands.literal("survival").executes(this::alSurvival));
		dispatcher.register(Commands.literal("coliseo").executes(this::alColiseo));
	}

	/**
	 * Te lleva a recorrer el coliseo, **de espectador**.
	 *
	 * De espectador se vuela, se atraviesan las paredes y no se puede tocar ni
	 * romper nada, que es todo lo que hace falta para darse una vuelta por la
	 * cancha sin ensuciarla. La contra es que de espectador el juego no deja usar
	 * ningun item, asi que la perla no abre el menu: la unica salida es `/lobby`,
	 * y por eso se lo decimos al entrar.
	 *
	 * Solo anda **desde el lobby**, y eso es a proposito: el que esta en el
	 * Survival tiene sus cosas encima, y sacarlo de ahi pide guardarle el
	 * inventario, que es justo lo que hace el mod de duelos cuando arranca un
	 * duelo. Mandarlo de una seria darle una forma de llevar su equipo al coliseo
	 * sin que nadie lo anote.
	 */
	private int alColiseo(CommandContext<CommandSourceStack> contexto) {
		ServerPlayer quien = contexto.getSource().getPlayer();
		if (quien == null) {
			return 0;
		}
		if (LobbyServidor.enColiseo(quien)) {
			Carteles.yaEstasEnColiseo(quien);
			return 0;
		}
		if (!LobbyServidor.enLobby(quien)) {
			Carteles.primeroAlLobby(quien);
			return 0;
		}

		mirador.entrar(quien, contexto.getSource().getServer());
		Carteles.enElColiseo(quien);
		return 1;
	}

	private int alLobby(CommandContext<CommandSourceStack> contexto) {
		ServerPlayer quien = contexto.getSource().getPlayer();
		if (quien == null) {
			return 0;
		}
		if (LobbyServidor.enLobby(quien)) {
			Carteles.yaEstas(quien);
			return 0;
		}
		if (LobbyServidor.peleando(quien)) {
			Carteles.enCombate(quien);
			return 0;
		}

		// Volviendo del coliseo NO se le anota nada. Ahi tiene la perla y nada mas,
		// no sus cosas: anotar eso pisaria la copia de verdad con un inventario
		// vacio, o sea le borraria el equipo. La vuelta que ya tenia sigue valiendo,
		// y es la que lo va a devolver a su casa cuando apriete SURVIVAL.
		if (LobbyServidor.enColiseo(quien)) {
			// Si estaba recorriendo de espectador, esto es lo unico que lo saca: el
			// modo de juego se devuelve antes de moverlo, para que no llegue al
			// patio volando y atravesando paredes.
			mirador.salir(quien);
			if (!LobbyServidor.soloMover(quien, contexto.getSource().getServer())) {
				return 0;
			}
			Carteles.bienvenida(quien);
			return 1;
		}

		if (!LobbyServidor.mandarAlLobby(quien, contexto.getSource().getServer(), vuelta)) {
			return 0;
		}
		Carteles.bienvenida(quien);
		return 1;
	}

	private int alSurvival(CommandContext<CommandSourceStack> contexto) {
		ServerPlayer quien = contexto.getSource().getPlayer();
		if (quien == null) {
			return 0;
		}
		if (!LobbyServidor.enLobby(quien)) {
			Carteles.yaEstasEnSurvival(quien);
			return 0;
		}

		MinecraftServer servidor = contexto.getSource().getServer();
		Guardado guardado = vuelta.de(quien);

		// Sin nada anotado solo queda el spawn. Pasa si el archivo se perdio, o si
		// alguien entro por primera vez: dejarlo encerrado en el lobby seria peor.
		//
		// Aca NO se le vacia el inventario, y es a proposito: si no hay copia es
		// justo cuando no se sabe que tiene encima, asi que se le saca la perla y
		// nada mas. Vaciar seria borrarle las cosas de verdad al unico jugador al
		// que no le podemos devolver nada.
		if (guardado == null) {
			sacarLasPerlas(quien);
			alSpawn(quien, servidor);
			Carteles.alSpawn(quien);
			return 1;
		}

		// `devolverA` escribe los 41 casilleros uno por uno, asi que la perla del
		// casillero 0 se pisa sola: no hay que vaciar nada antes.
		guardado.devolverA(quien, servidor);
		vuelta.borrar(quien);
		Carteles.deVuelta(quien);
		return 1;
	}

	private void sacarLasPerlas(ServerPlayer quien) {
		for (int i = 0; i < quien.getInventory().getContainerSize(); i++) {
			if (Perla.es(quien.getInventory().getItem(i))) {
				quien.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			}
		}
		quien.inventoryMenu.broadcastChanges();
	}

	private void alSpawn(ServerPlayer quien, MinecraftServer servidor) {
		LevelData.RespawnData spawn = servidor.overworld().getRespawnData();
		ServerLevel mundo = servidor.getLevel(spawn.dimension());
		if (mundo == null) {
			mundo = servidor.overworld();
		}
		BlockPos punto = spawn.pos();
		quien.teleportTo(mundo, punto.getX() + 0.5, punto.getY(), punto.getZ() + 0.5,
				Set.<Relative>of(), spawn.yaw(), spawn.pitch(), false);
	}
}
