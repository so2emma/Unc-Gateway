'use client';

import React from 'react';

export const OperatorTopBar: React.FC = () => {
  return (
    <header
      style={{
        height: '56px',
        backgroundColor: 'var(--surface-dark)',
        borderBottom: '1px solid var(--border-dark)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 24px',
        position: 'sticky',
        top: 0,
        zIndex: 40,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span
            style={{
              fontSize: '11px',
              fontWeight: 700,
              textTransform: 'uppercase',
              letterSpacing: '0.6px',
              color: 'var(--text-muted)',
            }}
          >
            Environment
          </span>
          <span
            className="badge badge-accent mono"
            style={{ fontSize: '11px' }}
          >
            operator-local
          </span>
        </div>

        <div
          style={{
            height: '16px',
            width: '1px',
            backgroundColor: 'var(--border-dark)',
          }}
        />

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <span
            style={{
              fontSize: '11px',
              fontWeight: 700,
              textTransform: 'uppercase',
              letterSpacing: '0.6px',
              color: 'var(--text-muted)',
            }}
          >
            Tenant Context
          </span>
          <span
            className="badge badge-muted mono"
            style={{ fontSize: '11px' }}
          >
            Shared Pool (Multi-Tenant)
          </span>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
        <div
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            fontSize: '12px',
            color: 'var(--text-muted)',
          }}
        >
          <span
            style={{
              width: '7px',
              height: '7px',
              borderRadius: '50%',
              backgroundColor: 'var(--success)',
            }}
          />
          <span style={{ fontWeight: 500, color: 'var(--text-primary)' }}>Admin API Connected</span>
        </div>
      </div>
    </header>
  );
};
