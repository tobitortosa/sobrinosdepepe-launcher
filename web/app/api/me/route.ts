import { currentUser, fail, ok } from '@/lib/api';
import { ipBaneada, ipDeLaPeticion } from '@/lib/baneos';

/**
 * La pantalla de espera del launcher consulta esto para saber si ya lo aprobaron.
 *
 * A diferencia del resto, un baneado sí obtiene respuesta: contesta `banned` en vez
 * de cerrarle la puerta con un 403. Es lo que le permite al launcher mostrarle la
 * pantalla que le explica qué pasó, en lugar de borrarle la sesión y devolverlo al
 * login como si se le hubiera vencido.
 */
export async function GET(request: Request) {
  const user = await currentUser(request);
  if (!user) return fail('Tu sesión venció. Volvé a iniciar sesión.', 401);

  const baneado =
    user.status === 'banned' ||
    (user.role !== 'admin' && (await ipBaneada(ipDeLaPeticion(request))));

  return ok({
    username: user.username,
    status: baneado ? 'banned' : user.status,
    role: user.role,
    mustChangePassword: user.mustChangePassword,
    createdAt: user.createdAt,
  });
}
