'use client';

import { useState } from 'react';

/**
 * La dirección del servidor, con un botón que la copia.
 *
 * Es lo único interactivo de la página de inicio, y es a propósito: quien llega acá
 * viene a buscar la IP. El botón no abre nada ni pregunta nada — la copia y avisa.
 *
 * Si el navegador no deja copiar (pasa sin HTTPS y en algunos navegadores de
 * teléfono), el texto igual se puede seleccionar a mano, así que no hay error que
 * mostrar: se vuelve a "Copiar" y listo.
 */
export function CopiarIp({ ip }: { ip: string }) {
  const [copiada, setCopiada] = useState(false);

  async function copiar() {
    try {
      await navigator.clipboard.writeText(ip);
      setCopiada(true);
      setTimeout(() => setCopiada(false), 2000);
    } catch {
      setCopiada(false);
    }
  }

  return (
    <div className="ip">
      <code className="direccion">{ip}</code>
      <button type="button" onClick={copiar} className="copiar" aria-label={`Copiar ${ip}`}>
        {copiada ? <TildeIcon /> : <CopiarIcon />}
        {copiada ? '¡Copiada!' : 'Copiar'}
      </button>
    </div>
  );
}

function CopiarIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="9" y="9" width="11" height="11" rx="2" stroke="currentColor" strokeWidth="2" />
      <path
        d="M5 15H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v1"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
      />
    </svg>
  );
}

function TildeIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <path
        d="M4 12.5 9.5 18 20 6.5"
        stroke="currentColor"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
