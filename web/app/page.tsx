import Image from 'next/image';
import Link from 'next/link';
import { env } from '@/lib/env';
import { CopiarIp } from './copiar';
import { socials } from './socials';

// La dirección, la versión y el link de descarga salen de variables de entorno: se
// leen en cada pedido, así cambiarlos no obliga a recompilar el sitio.
export const dynamic = 'force-dynamic';

export const metadata = {
  title: 'SOBRINOS DE PEPE · Servidor de Minecraft',
  description: 'Copiá la IP y entrá. Gratis, sin cuenta premium y sin instalar nada.',
};

/**
 * Tres pasos y ni una palabra de más: el que llega hasta acá ya decidió bajarse el
 * zip, y lo único que le falta es dónde va cada cosa.
 *
 * No se nombra ningún launcher en particular. La mayoría usa TLauncher, pero no
 * todos, y el paso es el mismo en cualquiera: elegir la versión con Fabric.
 */
const pasos = [
  { n: 1, titulo: 'Fabric 26.1', texto: 'Elegí esa versión en tu launcher y abrila una vez.' },
  { n: 2, titulo: 'Descomprimí', texto: 'Las dos carpetas van adentro de .minecraft.' },
  { n: 3, titulo: 'Entrá', texto: 'Agregás el servidor con la IP. La voz se abre con la tecla V.' },
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
  const { serverAddress, minecraftVersion, modsUrl } = env;

  return (
    <main className="page">
      <header className="hero">
        <Image
          src="/logo.png"
          alt="Sobrinos de Pepe"
          width={112}
          height={112}
          priority
          className="logo"
        />

        <h1>SOBRINOS DE PEPE</h1>
        <p className="lead">Copiá la IP y entrá.</p>

        <CopiarIp ip={serverAddress} />

        <p className="requisitos">Minecraft Java {minecraftVersion} · no hace falta comprarlo</p>
      </header>

      {/* Una sola descarga acá, y a propósito: si al lado hubiera un botón del
          launcher, la mitad se bajaría el que no quería. */}
      <section className="descarga" aria-labelledby="mods">
        <h2 id="mods">Chat de voz y más FPS</h2>
        <p className="subtitulo">Opcional. Se entra sin nada de esto.</p>

        {modsUrl ? (
          <a className="download" href={modsUrl}>
            <DownloadIcon />
            Descargar los mods
          </a>
        ) : (
          <p className="pending">Todavía no está publicado.</p>
        )}

        <ol className="grilla tres">
          {pasos.map((paso) => (
            <li key={paso.n} className="paso">
              <span className="numero">{paso.n}</span>
              <div>
                <h3>{paso.titulo}</h3>
                <p>{paso.texto}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      {/* El launcher, abajo y en chico: es para el que no quiere hacer nada de lo de
          arriba, no la puerta de entrada. */}
      <p className="alternativa">
        ¿No querés tocar carpetas? <Link href="/launcher">Usá el launcher</Link>, te instala todo
        solo.
      </p>

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
