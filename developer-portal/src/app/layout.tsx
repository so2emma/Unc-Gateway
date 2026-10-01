import React from 'react';
import type { Metadata } from 'next';
import Link from 'next/link';
import '@/styles/globals.css';
import { UncLogo } from '@/components/UncLogo';

export const metadata: Metadata = {
  title: 'Unc Gateway — Developer Portal',
  description: 'Self-serve API credentials and developer gateway management',
  icons: {
    icon: '/favicon.svg',
    apple: '/apple-touch-icon.png',
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="theme-light">
        <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
          {/* Navigation Shell */}
          <header
            style={{
              background: 'var(--surface-light)',
              borderBottom: '1px solid var(--border-light)',
              position: 'sticky',
              top: 0,
              zIndex: 100,
            }}
          >
            <div
              className="container"
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                height: '68px',
              }}
            >
              <Link href="/" style={{ display: 'inline-flex', alignItems: 'center' }}>
                <UncLogo variant="horizontal" theme="light" subtitle="DEVELOPER PORTAL" size={190} />
              </Link>

              <nav
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '28px',
                  fontSize: '14px',
                  fontWeight: 600,
                  color: 'var(--muted-light)',
                }}
              >
                <Link
                  href="/docs"
                  style={{
                    color: 'inherit',
                    transition: 'color 0.15s',
                  }}
                  className="nav-link"
                >
                  Docs
                </Link>
                <Link
                  href="/endpoints"
                  style={{
                    color: 'inherit',
                    transition: 'color 0.15s',
                  }}
                  className="nav-link"
                >
                  Endpoints
                </Link>
                <Link
                  href="/keys"
                  style={{
                    color: 'inherit',
                    transition: 'color 0.15s',
                  }}
                  className="nav-link"
                >
                  API Keys
                </Link>
              </nav>

              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <Link href="/signup" className="btn btn-outline btn-sm">
                  Sign in
                </Link>
                <Link href="/signup" className="btn btn-primary btn-sm">
                  Sign up
                </Link>
              </div>
            </div>
          </header>

          {/* Main Content Shell */}
          <main style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
            {children}
          </main>

          {/* Footer Shell */}
          <footer
            style={{
              textAlign: 'center',
              padding: '24px',
              color: 'var(--muted-light)',
              fontSize: '12.5px',
              borderTop: '1px solid var(--border-light)',
              background: 'var(--surface-light)',
            }}
          >
            <div className="container">
              Unc Gateway · Developer Portal · Ignite Design System
            </div>
          </footer>
        </div>
      </body>
    </html>
  );
}
