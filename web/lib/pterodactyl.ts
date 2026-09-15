import { env } from './env';

/**
 * Cliente del panel de Minehost. La clave da control total del servidor
 * (consola, archivos, apagarlo), así que vive únicamente acá, en el backend.
 * El launcher nunca la ve ni conoce la URL del panel.
 */
export class PterodactylError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly serverOffline: boolean,
  ) {
    super(message);
  }
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  const response = await fetch(`${env.pterodactylUrl}/api/client${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${env.pterodactylKey}`,
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...init?.headers,
    },
    cache: 'no-store',
  });

  if (!response.ok) {
    // 502 y 412 son la forma que tiene el panel de decir "el servidor está apagado".
    const offline = response.status === 502 || response.status === 412;
    throw new PterodactylError(
      offline
        ? 'El servidor de Minecraft está apagado.'
        : `El panel respondió ${response.status}.`,
      response.status,
      offline,
    );
  }

  return response;
}

/** Ejecuta un comando en la consola del servidor. */
export async function runCommand(command: string): Promise<void> {
  await request(`/servers/${env.pterodactylServerId}/command`, {
    method: 'POST',
    body: JSON.stringify({ command }),
  });
}

export async function serverState(): Promise<{ state: string; online: boolean }> {
  const response = await request(`/servers/${env.pterodactylServerId}/resources`);
  const body = (await response.json()) as { attributes?: { current_state?: string } };
  const state = body.attributes?.current_state ?? 'unknown';
  return { state, online: state === 'running' };
}

export const kick = (username: string, reason: string) =>
  runCommand(`kick ${username} ${reason}`);

export type ServerFile = { name: string; size: number; isFile: boolean };

/** Lista una carpeta del servidor, por ejemplo /mods. */
export async function listFiles(directory: string): Promise<ServerFile[]> {
  const response = await request(
    `/servers/${env.pterodactylServerId}/files/list?directory=${encodeURIComponent(directory)}`,
  );
  const body = (await response.json()) as {
    data: { attributes: { name: string; size: number; is_file: boolean } }[];
  };
  return body.data.map((f) => ({
    name: f.attributes.name,
    size: f.attributes.size,
    isFile: f.attributes.is_file,
  }));
}

/**
 * Sube un archivo al servidor. El panel no recibe el archivo directamente: primero
 * entrega una dirección temporal firmada y el archivo va ahí.
 */
export async function uploadFile(directory: string, filename: string, bytes: Uint8Array): Promise<void> {
  const ticket = await request(`/servers/${env.pterodactylServerId}/files/upload`);
  const { attributes } = (await ticket.json()) as { attributes: { url: string } };

  const form = new FormData();
  form.append('files', new File([bytes as BlobPart], filename, { type: 'application/java-archive' }));

  const response = await fetch(`${attributes.url}&directory=${encodeURIComponent(directory)}`, {
    method: 'POST',
    body: form,
  });

  if (!response.ok) {
    throw new PterodactylError(`No se pudo subir ${filename} (HTTP ${response.status}).`, response.status, false);
  }
}

/** Lee un archivo de texto del servidor, por ejemplo banned-ips.json. */
export async function readFile(path: string): Promise<string> {
  const response = await request(
    `/servers/${env.pterodactylServerId}/files/contents?file=${encodeURIComponent(path)}`,
  );
  return response.text();
}

/** Escribe un archivo de texto en el servidor, por ejemplo whitelist.json. */
export async function writeFile(path: string, contents: string): Promise<void> {
  await request(`/servers/${env.pterodactylServerId}/files/write?file=${encodeURIComponent(path)}`, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain' },
    body: contents,
  });
}

export async function deleteFiles(root: string, names: string[]): Promise<void> {
  if (names.length === 0) return;
  await request(`/servers/${env.pterodactylServerId}/files/delete`, {
    method: 'POST',
    body: JSON.stringify({ root, files: names }),
  });
}

/** "start", "stop", "restart" o "kill". */
export async function power(signal: string): Promise<void> {
  await request(`/servers/${env.pterodactylServerId}/power`, {
    method: 'POST',
    body: JSON.stringify({ signal }),
  });
}
