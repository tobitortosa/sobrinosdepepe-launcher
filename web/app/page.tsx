import Image from 'next/image';
import Link from 'next/link';
import { env } from '@/lib/env';
import { CopiarIp } from './copiar';
import { socials } from './socials';

// La dirección, la versión y los dos links de descarga salen de variables de entorno:
// se leen en cada pedido, así cambiarlos no obliga a recompilar el sitio.
export const dynamic = 'force-dynamic';

export const metadata = {
  title: 'SOBRINOS DE PEPE · Servidor de Minecraft',
  description: 'Copiá la IP y entrá. Gratis, sin cuenta premium y sin instalar nada.',
};

/**
 * Los tres pasos para poner los mods a mano, para el que ya tiene su Minecraft
 * andando y no quiere bajar el launcher. Son tres y no seis a propósito: el que
 * elige este camino ya sabe lo que es una carpeta de mods.
 */
const pasos = [
  {
    n: 1,
    titulo: 'Instalá Fabric 26.1',
    texto: 'En TLauncher elegís la versión 26.1 con Fabric y la abrís una vez.',
  },
  {
    n: 2,
    titulo: 'Descomprimí el zip',
    texto: 'Adentro hay una carpeta "mods". Va en .minecraft, junto a "saves" y "options.txt".',
  },
  {
    n: 3,
    titulo: 'Entrá con la IP',
    texto: 'Abrís el juego, agregás el servidor y listo. La voz se abre con la tecla V.',
  },
];

function DownloadIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M12 3v12m0 0 4.5-4.5M12 15l-4.5-4.5M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export default function Home() {
  const { serverAddress, minecraftVersion, downloadUrl, modsUrl } = env;

  return (
    <main className="page">
      {/* Lo primero y lo único imprescindible: la dirección. */}
      <header className="hero">
        <Image
          src="/logo.png"
          alt="Sobrinos de Pepe"
          width={124}
          height={124}
          priority
          className="logo"
        />

        <h1>SOBRINOS DE PEPE</h1>
        <p className="lead">Copiá la dirección, pegala en tu Minecraft y entrá.</p>

        <CopiarIp ip={serverAddress} />

        <p className="requisitos">
          Minecraft Java · versión {minecraftVersion} · no hace falta tenerlo comprado
        </p>
      </header>

      {/* Las dos descargas, en el orden en que conviene ofrecerlas. */}
      <section className="opciones" aria-labelledby="mods">
        <h2 id="mods">La voz y los mods</h2>
        <p className="subtitulo">
          Se entra sin nada de esto. Son para hablar con los que tenés al lado y para que el
          juego vaya más rápido.
        </p>

        <div className="tarjetas">
          <article className="tarjeta">
            <h3>Los mods sueltos</h3>
            <p>
              El chat de voz, los shaders y todo lo que le saca FPS al juego. Lo descomprimís en
              tu carpeta de Minecraft.
            </p>
            {modsUrl ? (
              <a className="download" href={modsUrl}>
                <DownloadIcon />
                Descargar los mods
              </a>
            ) : (
              <p className="pending">Todavía no está publicado.</p>
            )}
            <p className="pie">Para el que ya tiene su Minecraft andando</p>
          </article>

          <article className="tarjeta">
            <h3>El launcher</h3>
            <p>
              Hace lo mismo pero solo: te instala el juego, el Java y los mods, y los mantiene
              iguales a los del servidor.
            </p>
            {downloadUrl ? (
              <a className="download secundario" href={downloadUrl}>
                <DownloadIcon />
                Descargar el launcher
              </a>
            ) : (
              <p className="pending">Todavía no está publicado.</p>
            )}
            <p className="pie">
              Windows · <Link href="/launcher">cómo se instala</Link>
            </p>
          </article>
        </div>
      </section>

      {/* Solo para el camino a mano: el del launcher ya tiene su propia página. */}
      <section className="pasos" aria-labelledby="como">
        <h2 id="como">Poner los mods a mano</h2>
        <p className="subtitulo">Tres pasos, una sola vez.</p>

        <ol className="grilla tres">
          {pasos.map((paso) => (
            <li key={paso.n} className="paso simple">
              <div className="texto">
                <span className="numero">{paso.n}</span>
                <div>
                  <h3>{paso.titulo}</h3>
                  <p>{paso.texto}</p>
                </div>
              </div>
            </li>
          ))}
        </ol>

        <p className="aviso">
          Si ya tenías mods de otra versión en esa carpeta, sacalos antes: mezclados no arranca.
        </p>
      </section>

      <footer className="redes">
        {socials.map((social) => (
          <a
            key={social.name}
            href={social.url}
            target="_blank"
            rel="noopener noreferrer"
            className="red"
            style={{ ['--tono' as string]: social.color }}
            aria-label={`${social.name}: @${social.handle}`}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
              <path d={social.path} />
            </svg>
            <span className="nombre">{social.name}</span>
          </a>
        ))}
      </footer>
    </main>
  );
}
