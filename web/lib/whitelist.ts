import { createHash } from 'node:crypto';
import { eq } from 'drizzle-orm';
import { db } from './db';
import { users } from './db/schema';
import { runCommand, writeFile } from './pterodactyl';

/**
 * El identificador que el servidor le da a un jugador cuando corre en online-mode=false:
 * un UUID versión 3 del MD5 de "OfflinePlayer:<nombre>", sensible a mayúsculas.
 */
export function offlineUuid(username: string): string {
  const hash = createHash('md5').update(`OfflinePlayer:${username}`, 'utf8').digest();
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;

  const hex = hash.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * Escribe la whitelist entera a partir de las cuentas activas y le pide al servidor
 * que la recargue.
 *
 * No se usa el comando `whitelist add` a propósito. Ese comando le pregunta primero a
 * Mojang por el nombre y, si existe una cuenta premium que se llame igual, guarda el
 * identificador de esa cuenta. En un servidor offline el jugador entra con otro
 * identificador, así que la entrada no le sirve y queda afuera. Escribiendo el archivo
 * nosotros, cada entrada lleva el identificador que el servidor realmente va a usar.
 */
/**
 * Deja la cuenta activa y la mete en la whitelist del servidor, todo junto.
 *
 * El orden es al revés que en aprobar: primero la base y después el servidor, porque
 * la whitelist se arma leyendo las cuentas activas y la cuenta tiene que existir para
 * poder escribirla. Por eso, si el panel no contesta, se deshace: nunca queda una
 * cuenta marcada como activa que el servidor rechaza.
 *
 * Devuelve `false` cuando el panel falló y la cuenta quedó pendiente.
 */
export async function activarCuenta(id: number): Promise<boolean> {
  await db.update(users).set({ status: 'active', approvedAt: new Date(), bannedAt: null }).where(eq(users.id, id));
  try {
    await syncWhitelist();
    return true;
  } catch {
    await db.update(users).set({ status: 'pending', approvedAt: null }).where(eq(users.id, id));
    return false;
  }
}

export async function syncWhitelist(): Promise<{ count: number; names: string[] }> {
  const active = await db
    .select({ username: users.username })
    .from(users)
    .where(eq(users.status, 'active'));

  const entries = active.map((u) => ({ uuid: offlineUuid(u.username), name: u.username }));

  await writeFile('/whitelist.json', JSON.stringify(entries, null, 2));
  await runCommand('whitelist reload');

  return { count: entries.length, names: entries.map((e) => e.name) };
}
