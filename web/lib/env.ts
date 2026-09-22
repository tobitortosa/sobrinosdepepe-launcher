function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Falta la variable de entorno ${name}.`);
  return value;
}

export const env = {
  get databaseUrl() {
    return required('DATABASE_URL');
  },
  /** Clave de la API de Pterodactyl (ptlc_...). Vive solo acá, nunca en el launcher. */
  get pterodactylKey() {
    return required('PTERODACTYL_KEY');
  },
  get pterodactylUrl() {
    return process.env.PTERODACTYL_URL ?? 'https://pterodactyl.minehost.com.ar';
  },
  get pterodactylServerId() {
    return process.env.PTERODACTYL_SERVER_ID ?? 'dbd3f1e9';
  },
  get minecraftVersion() {
    return process.env.MINECRAFT_VERSION ?? '26.1';
  },
  get fabricLoader() {
    return process.env.FABRIC_LOADER ?? '0.19.5';
  },
  get serverName() {
    return process.env.SERVER_NAME ?? 'SOBRINOS DE PEPE';
  },
  /**
   * La que se publicita y la que se copia de la pagina. Desde el 2026-09-21 es el
   * dominio propio: tiene un registro SRV (`_minecraft._tcp`) que apunta a
   * sv36.minehost.pro:25445, asi que el jugador escribe la direccion sola, sin
   * puerto, y el juego resuelve el resto.
   */
  get serverAddress() {
    return process.env.SERVER_ADDRESS ?? 'sobrinosdepepe.com';
  },
  /**
   * La contraseña que se le pone a una cuenta cuando el administrador la restablece.
   * Es a propósito una sola y fácil de dictar: la persona entra con ella y lo primero
   * que hace es elegir la suya.
   */
  get defaultPassword() {
    return process.env.DEFAULT_PASSWORD ?? 'pepe2026';
  },
  get downloadUrl() {
    return process.env.DOWNLOAD_URL ?? '';
  },
  /**
   * El .zip con los mods sueltos, para el que entra por la IP con su propio Minecraft
   * en vez de usar el launcher. Lo arma `npm run mods:zip` desde el pack publicado y
   * queda en public/, así lo sirve el CDN y no hay que pasar 24 MB por una función.
   */
  get modsUrl() {
    return process.env.MODS_URL ?? '/SobrinosDePepe-mods.zip';
  },
  /**
   * Con lo que se firman los permisos de entrada al servidor. El mismo valor está
   * en la config del mod de acceso adentro del servidor, y en ningún otro lugar:
   * quien lo tenga puede emitir permisos.
   */
  get accessSecret() {
    return required('ACCESS_SECRET');
  },
  /**
   * La dirección de la página, tal como se le muestra a quien intenta entrar sin el
   * launcher. Sale de acá para que el día que compremos un dominio se cambie en un
   * solo lugar: servidor/subir-acceso.py la copia a la config del servidor.
   */
  get siteUrl() {
    return process.env.SITE_URL ?? 'sobrinosdepepe.com';
  },
};

export const MODRINTH_USER_AGENT =
  process.env.MODRINTH_USER_AGENT ?? 'SobrinosDePepeLauncher/0.1 (elsobrinodepepe@gmail.com)';
