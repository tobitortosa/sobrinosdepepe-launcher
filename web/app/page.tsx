import Image from 'next/image';
import { env } from '@/lib/env';
import { socials } from './socials';

// El link de descarga viene de una variable de entorno: se lee en cada pedido, así
// cambiarlo no obliga a recompilar el sitio.
export const dynamic = 'force-dynamic';

/** El código del launcher es público: cualquiera puede leerlo antes de instalarlo. */
const REPO = 'https://github.com/tobitortosa/sobrinosdepepe-launcher';

export const metadata = {
  title: 'SOBRINOS DE PEPE · Launcher',
  description: 'Descargá el launcher, creá tu cuenta y entrá a jugar. Seis pasos con fotos.',
};

/**
 * Los seis pasos, en el orden en que le pasan a quien descarga por primera vez.
 *
 * Cada uno es un título de tres o cuatro palabras y una sola línea de texto: la foto
 * tiene que explicar sola, porque nadie lee. Tres llevan una nota abajo, y son
 * justo los tres donde la gente se frena: al abrir un .exe bajado de internet, al
 * ver el aviso de Windows, y al que le pidan crear una cuenta. La respuesta va ahí
 * y no en un apartado al final, que nadie llega a leer.
 */
const pasos = [
  {
    n: 1,
    titulo: 'Abrí el archivo',
    texto: 'Doble clic en el que se te descargó.',
    imagen: '/pasos/paso1.png',
    alt: 'El archivo SobrinosDePepe-win-Setup.exe con el cursor encima',
    nota: (
      <>
        Se instala en su propia carpeta y sin pedir permisos de administrador. Tu
        Minecraft de siempre y tu TLauncher quedan igual que ahora.
      </>
    ),
  },
  {
    n: 2,
    titulo: 'Tocá "Más información"',
    texto: 'Windows avisa porque no conoce el programa. Es normal.',
    imagen: '/pasos/paso2.png',
    alt: 'El aviso azul de Windows con el enlace Más información señalado',
    nota: (
      <>
        Ese aviso le sale a todo programa sin un certificado de 200 dólares por año. El
        código está entero acá:{' '}
        <a href={REPO} target="_blank" rel="noopener noreferrer">
          GitHub
          <ExternalArrow />
        </a>
      </>
    ),
  },
  {
    n: 3,
    titulo: 'Ejecutar de todas formas',
    texto: 'Aparece ese botón nuevo. Apretalo y se instala solo.',
    imagen: '/pasos/paso3.png',
    alt: 'El mismo aviso, ahora con el botón Ejecutar de todas formas',
  },
  {
    n: 4,
    titulo: 'Creá una cuenta',
    texto: 'Abajo del botón verde, "Crear una cuenta".',
    imagen: '/pasos/paso4.png',
    alt: 'La pantalla de inicio de sesión del launcher',
    nota: (
      <>
        No es tu cuenta de Minecraft. Esta la creás acá y sirve solo para este servidor:
        no hace falta tener el juego comprado.
      </>
    ),
  },
  {
    n: 5,
    titulo: 'Elegí tu nombre',
    texto: 'El que pongas es tu nombre en el server. No se puede cambiar.',
    imagen: '/pasos/paso5.png',
    alt: 'El formulario para crear la cuenta en el launcher',
  },
  {
    n: 6,
    titulo: 'JUGAR',
    texto: 'La primera vez tarda unos minutos: baja el Minecraft y los mods.',
    imagen: '/pasos/paso6.png',
    alt: 'La pantalla del launcher con el botón JUGAR',
  },
];

/** Flecha que avisa que el enlace abre en otra pestaña. */
function ExternalArrow() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

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
  const download = env.downloadUrl;

  return (
    <main className="page">
      {/* Lo primero y lo único que hay que hacer: bajarlo. */}
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
        <p className="lead">El launcher instala todo solo. Vos apretás JUGAR.</p>

        {download ? (
          <a className="download" href={download}>
            <DownloadIcon />
            Descargar el launcher
          </a>
        ) : (
          <p className="pending">La descarga todavía no está publicada.</p>
        )}

        <p className="requisitos">Windows 64 bits · gratis · no toca tu Minecraft de siempre</p>
      </header>

      {/* Los pasos, con la foto de lo que va a ver en la pantalla. */}
      <section className="pasos" aria-labelledby="como">
        <h2 id="como">Cómo entrar</h2>
        <p className="subtitulo">Seis pasos. Una sola vez.</p>

        <ol className="grilla">
          {pasos.map((paso) => (
            <li key={paso.n} className="paso">
              <Image
                src={paso.imagen}
                alt={paso.alt}
                width={500}
                height={500}
                className="captura"
                sizes="(max-width: 44rem) 100vw, 22rem"
              />

              <div className="texto">
                <span className="numero">{paso.n}</span>
                <div>
                  <h3>{paso.titulo}</h3>
                  <p>{paso.texto}</p>
                </div>
              </div>

              {paso.nota && <p className="nota">{paso.nota}</p>}
            </li>
          ))}
        </ol>
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
