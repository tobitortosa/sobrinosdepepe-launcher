import { readFile } from './pterodactyl';

/**
 * Los baneos por IP del servidor de Minecraft, aplicados también a la web.
 *
 * `/ban-ip` solo cierra la puerta del juego: el baneado igual puede abrir el
 * launcher, crear cuentas nuevas y llenar la whitelist, porque el registro se
 * activa solo y no mira de dónde viene nadie. Leyendo la misma lista que usa el
 * juego, un solo `/ban-ip` alcanza para las dos puertas y no hay una segunda
 * lista que mantener.
 */

/** Cuánto vale una lectura de la lista antes de volver a pedirla al panel. */
const CACHE_MS = 30_000;

let cache: { ips: Set<string>; hasta: number } | null = null;

interface BaneoDelJuego {
  ip?: string;
  expires?: string;
}

/**
 * La IP del que hace la petición. En Vercel llega en `x-forwarded-for`, y el
 * primero de la lista es el cliente: los que siguen son los proxys por los que
 * pasó. El header lo pone la plataforma, no el launcher, así que no se puede
 * falsear desde afuera.
 */
export function ipDeLaPeticion(request: Request): string | null {
  const header = request.headers.get('x-forwarded-for');
  if (!header) return null;
  const primera = header.split(',')[0]?.trim();
  return primera || null;
}

/**
 * Si esa IP está baneada en el servidor de Minecraft.
 *
 * Cuando el panel no contesta devuelve false, o sea deja pasar. Es a propósito:
 * un problema de red del panel no puede dejar afuera a todo el mundo, y el
 * `/ban-ip` del juego sigue frenando al baneado en la puerta que importa.
 */
export async function ipBaneada(ip: string | null): Promise<boolean> {
  if (!ip) return false;

  try {
    const ips = await listaDeBaneos();
    return ips.has(ip);
  } catch {
    return false;
  }
}

async function listaDeBaneos(): Promise<Set<string>> {
  const ahora = Date.now();
  if (cache && cache.hasta > ahora) return cache.ips;

  const crudo = await readFile('/banned-ips.json');
  const ips = new Set<string>();

  // El archivo lo escribe el juego. Si viniera cortado o con otra forma, mejor
  // una lista vacía que una excepción en el login de todos.
  const parsed: unknown = crudo.trim() ? JSON.parse(crudo) : [];
  if (Array.isArray(parsed)) {
    for (const entrada of parsed as BaneoDelJuego[]) {
      if (typeof entrada?.ip === 'string' && vigente(entrada.expires)) ips.add(entrada.ip);
    }
  }

  cache = { ips, hasta: ahora + CACHE_MS };
  return ips;
}

/** Los baneos temporales traen fecha; los de siempre dicen "forever". */
function vigente(expires: string | undefined): boolean {
  if (!expires || expires === 'forever') return true;
  const vence = Date.parse(expires);
  return Number.isNaN(vence) ? true : vence > Date.now();
}
