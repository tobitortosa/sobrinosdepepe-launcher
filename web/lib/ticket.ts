import { createHmac } from 'node:crypto';
import { env } from './env';

/**
 * El permiso de entrada al servidor. El launcher lo pide cada vez que el jugador
 * aprieta JUGAR, se lo pasa al juego, y el mod del servidor lo revisa durante el
 * login: sin un ticket firmado, no entra.
 *
 * El formato es una línea sola, la misma que lee mod-acceso/Ticket.java:
 *
 *     1:PEPE:1757260000:a3f1...   ->  versión : nombre : vence : firma
 *
 * La firma es HMAC-SHA256 del cuerpo con ACCESS_SECRET. El servidor tiene ese
 * mismo secreto en su config y no necesita preguntarnos nada: valida solo.
 *
 * El nombre va adentro de la firma para que el ticket de uno no le sirva a otro.
 */

/** La versión del formato. Si cambia, un servidor viejo rechaza los tickets nuevos. */
const VERSION = '1';

/**
 * Doce horas. Tiene que aguantar una sesión entera de juego, porque el ticket se
 * emite al apretar JUGAR y se vuelve a usar si al jugador se le corta y reconecta
 * desde el menú del juego. Y tiene que ser corto para que valga la pena: cada vez
 * que abre el juego pasa por el launcher, y el launcher deja los mods al día.
 */
const DURACION_EN_SEGUNDOS = 12 * 60 * 60;

export type Ticket = { ticket: string; vence: number };

export function emitirTicket(username: string): Ticket {
  const vence = Math.floor(Date.now() / 1000) + DURACION_EN_SEGUNDOS;
  const cuerpo = `${VERSION}:${username}:${vence}`;
  const firma = createHmac('sha256', env.accessSecret).update(cuerpo).digest('hex');
  return { ticket: `${cuerpo}:${firma}`, vence };
}
