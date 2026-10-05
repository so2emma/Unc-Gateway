import React from 'react';
import type { Metadata } from 'next';
import '@/styles/globals.css';
import { OperatorSidebar } from '@/components/OperatorSidebar';
import { OperatorTopBar } from '@/components/OperatorTopBar';

export const metadata: Metadata = {
  title: 'Unc Gateway — Admin Dashboard',
  description: 'Operator control room and gateway management shell',
  icons: {
    icon: '/favicon.svg',
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: 'var(--bg-dark)' }}>
          {/* Fixed Dark Left Sidebar */}
          <OperatorSidebar />

          {/* Right Main Container */}
          <div
            style={{
              marginLeft: '250px',
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              minWidth: 0,
            }}
          >
            {/* Top Bar with Tenant / Environment Context */}
            <OperatorTopBar />

            {/* Dark Content Canvas */}
            <main
              style={{
                flex: 1,
                padding: '24px 32px',
                backgroundColor: 'var(--bg-dark)',
                overflowY: 'auto',
              }}
            >
              {children}
            </main>
          </div>
        </div>
      </body>
    </html>
  );
}
