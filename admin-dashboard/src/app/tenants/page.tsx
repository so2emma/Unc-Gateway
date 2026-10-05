import React from 'react';
import type { Metadata } from 'next';
import Link from 'next/link';
import { ShieldIcon, ServerIcon } from '@/components/Icons';

export const metadata: Metadata = {
  title: 'Tenants & Auth — Unc Operator Control Room',
  description: 'Tenant management and credential administration scheduled for Phase 32',
};

export default function TenantsPage() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div>
        <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#F5F6F8', marginBottom: '4px' }}>
          Tenants & Authentication
        </h1>
        <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
          Multi-tenant isolation and administrative credential management
        </p>
      </div>

      <div
        className="panel"
        style={{
          padding: '40px 32px',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          textAlign: 'center',
          gap: '16px',
          maxWidth: '580px',
          margin: '40px auto',
        }}
      >
        <div
          style={{
            width: '48px',
            height: '48px',
            borderRadius: '50%',
            backgroundColor: 'var(--accent-tint)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'var(--accent)',
          }}
        >
          <ShieldIcon size={24} />
        </div>

        <div>
          <h2 style={{ fontSize: '16px', fontWeight: 600, color: '#F5F6F8', marginBottom: '8px' }}>
            Scheduled for Phase 32
          </h2>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', lineHeight: 1.6 }}>
            Tenant registration modals, secret key rotation, and the top-bar tenant selector dropdown will be
            fully wired here in Phase 32.
          </p>
        </div>

        <Link href="/services" className="btn btn-secondary" style={{ marginTop: '8px' }}>
          <ServerIcon size={14} />
          <span>Return to Services & Routes</span>
        </Link>
      </div>
    </div>
  );
}
