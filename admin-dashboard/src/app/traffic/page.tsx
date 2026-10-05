import React from 'react';
import type { Metadata } from 'next';
import Link from 'next/link';
import { ActivityIcon, ServerIcon } from '@/components/Icons';

export const metadata: Metadata = {
  title: 'Traffic & Pulse — Unc Operator Control Room',
  description: 'Live traffic waveform and tail latency metrics scheduled for Phase 21',
};

export default function TrafficPage() {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div>
        <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#F5F6F8', marginBottom: '4px' }}>
          Traffic Pulse & Latency
        </h1>
        <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
          Real-time oscilloscope request volume and tail latency tracking
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
          <ActivityIcon size={24} />
        </div>

        <div>
          <h2 style={{ fontSize: '16px', fontWeight: 600, color: '#F5F6F8', marginBottom: '8px' }}>
            Scheduled for Phase 21
          </h2>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)', lineHeight: 1.6 }}>
            The scrolling oscilloscope waveform visualization and p95/p99 tail latency table will be mounted
            here once backed by <span className="mono" style={{ color: '#F5F6F8' }}>analytics-api</span> in Phase 21.
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
