'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { UncLogo } from './UncLogo';
import { ServerIcon, UsersIcon, ActivityIcon, ShieldIcon } from './Icons';

export const OperatorSidebar: React.FC = () => {
  const pathname = usePathname();

  const navItems = [
    {
      name: 'Services & Routes',
      href: '/services',
      icon: ServerIcon,
      active: pathname.startsWith('/services') || pathname === '/',
    },
    {
      name: 'Consumers & Plugins',
      href: '/consumers',
      icon: UsersIcon,
      active: pathname.startsWith('/consumers'),
    },
    {
      name: 'Traffic Pulse',
      href: '/traffic',
      icon: ActivityIcon,
      active: pathname.startsWith('/traffic'),
    },
    {
      name: 'Tenants & Auth',
      href: '/tenants',
      icon: ShieldIcon,
      active: pathname.startsWith('/tenants'),
      badge: 'Phase 32',
    },
  ];

  return (
    <aside
      style={{
        width: '250px',
        backgroundColor: 'var(--surface-dark)',
        borderRight: '1px solid var(--border-dark)',
        display: 'flex',
        flexDirection: 'column',
        position: 'fixed',
        top: 0,
        bottom: 0,
        left: 0,
        zIndex: 50,
      }}
    >
      {/* Brand Header */}
      <div
        style={{
          padding: '20px 18px',
          borderBottom: '1px solid var(--border-dark)',
          display: 'flex',
          alignItems: 'center',
        }}
      >
        <Link href="/services" style={{ textDecoration: 'none' }}>
          <UncLogo
            variant="horizontal"
            theme="dark"
            subtitle="OPERATOR CONTROL ROOM"
            size={185}
          />
        </Link>
      </div>

      {/* Navigation Section */}
      <div style={{ flex: 1, padding: '16px 12px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
        <div
          style={{
            fontSize: '11px',
            fontWeight: 700,
            textTransform: 'uppercase',
            letterSpacing: '1px',
            color: 'var(--text-muted)',
            padding: '8px 10px 4px 10px',
          }}
        >
          Management
        </div>

        {navItems.map((item) => {
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '9px 12px',
                borderRadius: 'var(--radius-control)',
                fontSize: '13px',
                fontWeight: item.active ? 600 : 500,
                color: item.active ? '#FFFFFF' : 'var(--text-muted)',
                backgroundColor: item.active ? 'var(--surface-hover)' : 'transparent',
                borderLeft: item.active ? '3px solid var(--accent)' : '3px solid transparent',
                textDecoration: 'none',
                transition: 'all 0.15s ease',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                <Icon
                  size={16}
                  style={{
                    color: item.active ? 'var(--accent)' : 'var(--text-muted)',
                  }}
                />
                <span>{item.name}</span>
              </div>
              {item.badge && (
                <span
                  style={{
                    fontSize: '10px',
                    padding: '2px 6px',
                    borderRadius: '4px',
                    backgroundColor: 'rgba(139, 147, 161, 0.15)',
                    color: 'var(--text-muted)',
                    fontFamily: 'var(--font-mono)',
                  }}
                >
                  {item.badge}
                </span>
              )}
            </Link>
          );
        })}
      </div>

      {/* Sidebar Footer */}
      <div
        style={{
          padding: '14px 16px',
          borderTop: '1px solid var(--border-dark)',
          fontSize: '11.5px',
          color: 'var(--text-muted)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <span>Operator Shell</span>
        <span className="mono" style={{ color: '#F5F6F8' }}>v1.0.0</span>
      </div>
    </aside>
  );
};
