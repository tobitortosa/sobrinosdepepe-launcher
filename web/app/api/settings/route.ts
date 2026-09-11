import { createHash } from 'node:crypto';
import { eq } from 'drizzle-orm';
import { fail, requireActiveUser } from '@/lib/api';
import { db } from '@/lib/db';
import { userSettings } from '@/lib/db/schema';

/**
 * La configuración del juego de cada cuenta, como un zip: `options.txt` y la
 * carpeta `config/`. Ahí viven las teclas, la sensibilidad del mouse, el FOV, el
 * volumen y los ajustes de los mods.
 *
 * El launcher la baja antes de abrir el juego y la sube cuando el juego se cierra.
 * Es por cuenta y no por máquina: quien entra con su cuenta en una computadora
 * prestada juega con sus propias teclas, y las del dueño lo esperan enteras.
 *
 * El backend no mira adentro del zip ni lo necesita. Lo guarda y lo devuelve.
 */

/** Doscientos KB es lo normal; el tope es para cortar una subida absurda. */
const MAX_BYTES = 8 * 1024 * 1024;

/** Los cuatro bytes con los que empieza todo zip. */
const ES_ZIP = [0x50, 0x4b, 0x03, 0x04];

export async function GET(request: Request) {
  const guard = await requireActiveUser(request);
  if ('response' in guard) return guard.response;

  const rows = await db
    .select()
    .from(userSettings)
    .where(eq(userSettings.userId, guard.user.id))
    .limit(1);

  const guardado = rows[0];
  // Todavía no guardó nada: no es un error, es una cuenta nueva. El launcher
  // entiende el 204 como "quedate con lo que tenés en la máquina y subilo".
  if (!guardado) return new Response(null, { status: 204 });

  return new Response(new Uint8Array(guardado.data), {
    headers: {
      'Content-Type': 'application/zip',
      'Content-Length': String(guardado.size),
      // Con esto el launcher sabe si lo que tiene en disco ya es esto mismo, y se
      // ahorra pisar archivos que no cambiaron.
      'X-Sha1': guardado.sha1,
      'X-Actualizado': guardado.updatedAt.toISOString(),
      'Cache-Control': 'no-store',
    },
  });
}

export async function PUT(request: Request) {
  const guard = await requireActiveUser(request);
  if ('response' in guard) return guard.response;

  const bytes = Buffer.from(await request.arrayBuffer());

  if (bytes.byteLength === 0) return fail('No llegó ninguna configuración.');
  if (bytes.byteLength > MAX_BYTES) {
    return fail('La configuración es demasiado grande para guardarla en tu cuenta.', 413);
  }
  if (!ES_ZIP.every((byte, i) => bytes[i] === byte)) {
    return fail('La configuración tiene que ser un zip.');
  }

  const sha1 = createHash('sha1').update(bytes).digest('hex');
  const fila = { userId: guard.user.id, data: bytes, size: bytes.byteLength, sha1, updatedAt: new Date() };

  await db
    .insert(userSettings)
    .values(fila)
    .onConflictDoUpdate({ target: userSettings.userId, set: fila });

  return Response.json({ sha1, size: fila.size, updatedAt: fila.updatedAt.toISOString() });
}
