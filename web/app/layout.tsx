import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { Nav } from './nav';
import './globals.css';

export const metadata: Metadata = {
  title: 'SOBRINOS DE PEPE',
  description: 'Servidor de Minecraft. Copiá la IP y entrá.',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="es">
      <body>
        <Nav />
        {children}
      </body>
    </html>
  );
}
