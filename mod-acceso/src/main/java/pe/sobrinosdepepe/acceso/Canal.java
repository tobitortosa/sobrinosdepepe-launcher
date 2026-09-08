package pe.sobrinosdepepe.acceso;

import net.minecraft.resources.Identifier;

/** El canal por donde el servidor pide el ticket y el cliente lo contesta. */
public final class Canal {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("sobrinosdepepe", "acceso");

	/** La variable de entorno con la que el launcher le pasa el ticket al juego. */
	public static final String VARIABLE = "SOBRINOSDEPEPE_TICKET";

	private Canal() {}
}
