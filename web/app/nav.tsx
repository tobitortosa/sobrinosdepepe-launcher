import Link from 'next/link';

/**
 * Las dos formas de entrar, arriba de todo y en el mismo orden en que las ofrecemos:
 * primero la IP, que no pide bajar nada, y después el launcher.
 */
export function Nav() {
  return (
    <nav className="nav" aria-label="Secciones">
      <Link href="/" className="marca">
        SOBRINOS DE PEPE
      </Link>
      <div className="solapas">
        <Link href="/" className="solapa">
          Entrar
        </Link>
        <Link href="/launcher" className="solapa">
          Launcher
        </Link>
      </div>
    </nav>
  );
}
