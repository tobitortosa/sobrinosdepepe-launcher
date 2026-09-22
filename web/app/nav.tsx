'use client';

import Image from 'next/image';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

/**
 * Las dos páginas que hay, arriba de todo y en el orden en que se ofrecen: primero
 * entrar, que no pide bajar nada, y después el launcher.
 *
 * Es client component por una sola cosa: marcar en cuál estás. Sin eso las dos
 * solapas se ven iguales y no se entiende que son dos lugares distintos.
 */
export function Nav() {
  const ruta = usePathname();

  return (
    <header className="nav">
      <div className="nav-interior">
        <Link href="/" className="marca">
          <Image src="/logo.png" alt="" width={28} height={28} className="marca-logo" />
          <span>SOBRINOS DE PEPE</span>
        </Link>

        <nav className="solapas" aria-label="Secciones">
          <Link href="/" className="solapa" aria-current={ruta === '/' ? 'page' : undefined}>
            Entrar
          </Link>
          <Link
            href="/launcher"
            className="solapa"
            aria-current={ruta === '/launcher' ? 'page' : undefined}
          >
            Launcher
          </Link>
        </nav>
      </div>
    </header>
  );
}
