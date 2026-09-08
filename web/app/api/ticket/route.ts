import { ok, requireActiveUser } from '@/lib/api';
import { emitirTicket } from '@/lib/ticket';

/**
 * El permiso para entrar al servidor. El launcher lo pide justo antes de abrir el
 * juego y se lo pasa por una variable de entorno; el mod del servidor lo revisa
 * durante el login. Quien abre Minecraft por su cuenta no tiene ninguno.
 *
 * Va firmado con el nombre de la cuenta que lo pide, así que no se puede pasar de
 * uno a otro. No se guarda nada: el servidor lo valida con el secreto compartido.
 */
export async function GET(request: Request) {
  const guard = await requireActiveUser(request);
  if ('response' in guard) return guard.response;

  const { ticket, vence } = emitirTicket(guard.user.username);
  return ok({ ticket, vence });
}
