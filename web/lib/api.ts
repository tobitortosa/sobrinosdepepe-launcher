import { NextResponse } from 'next/server';
import { bearerToken, userFromToken } from './auth';
import { ipBaneada, ipDeLaPeticion } from './baneos';
import type { User } from './db/schema';
import { PterodactylError } from './pterodactyl';

export function ok<T>(data: T, status = 200) {
  return NextResponse.json(data, { status });
}

/**
 * Los mensajes de error siguen siempre la misma forma: qué se quería hacer,
 * qué falló y qué puede hacer la persona. Nunca "ocurrió un error inesperado".
 */
export function fail(message: string, status = 400, extra?: Record<string, unknown>) {
  return NextResponse.json({ error: message, ...extra }, { status });
}

export async function currentUser(request: Request): Promise<User | null> {
  return userFromToken(bearerToken(request));
}

type Guard = { user: User } | { response: NextResponse };

export async function requireUser(request: Request): Promise<Guard> {
  const user = await currentUser(request);
  if (!user) return { response: fail('Tu sesión venció. Volvé a iniciar sesión.', 401) };
  if (user.status === 'banned') {
    return { response: fail('Tu cuenta está baneada.', 403, { status: 'banned' }) };
  }
  // Un /ban-ip en el juego cierra también el launcher: si no, el baneado se hace
  // otra cuenta y vuelve. Los administradores quedan afuera de esta regla a
  // propósito, porque banear la IP de la casa de uno no puede dejarlo sin panel.
  if (user.role !== 'admin' && (await ipBaneada(ipDeLaPeticion(request)))) {
    return { response: fail('Estás baneado del servidor.', 403, { status: 'banned' }) };
  }
  return { user };
}

export async function requireActiveUser(request: Request): Promise<Guard> {
  const guard = await requireUser(request);
  if ('response' in guard) return guard;
  if (guard.user.status !== 'active') {
    return {
      response: fail('Tu cuenta todavía no fue aprobada.', 403, { status: guard.user.status }),
    };
  }
  return guard;
}

export async function requireAdmin(request: Request): Promise<Guard> {
  const guard = await requireActiveUser(request);
  if ('response' in guard) return guard;
  if (guard.user.role !== 'admin') {
    return { response: fail('No tenés permisos de administrador.', 403) };
  }
  return guard;
}

/** Traduce una falla del panel de Minehost a algo que el admin pueda accionar. */
export function pterodactylFailure(error: unknown, action: string) {
  if (error instanceof PterodactylError) {
    return fail(
      error.serverOffline
        ? `${action} no se pudo hacer: el servidor de Minecraft está apagado. Prendelo y volvé a intentar. No se cambió nada.`
        : `${action} no se pudo hacer: el panel de Minehost respondió ${error.status}. No se cambió nada.`,
      502,
    );
  }
  return fail(`${action} no se pudo hacer: no se pudo contactar al panel de Minehost. No se cambió nada.`, 502);
}

export async function readJson<T>(request: Request): Promise<T | null> {
  try {
    return (await request.json()) as T;
  } catch {
    return null;
  }
}
