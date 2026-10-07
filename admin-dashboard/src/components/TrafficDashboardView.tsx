'use client';

import React, { useState, useCallback } from 'react';
import {
  analyticsApiClient as defaultClient,
  BucketEntry,
  LatencyMetricsResponse,
} from '@/lib/analyticsApiClient';
import { TrafficPulseWaveform } from './TrafficPulseWaveform';
import { LatencyMetricsTable } from './LatencyMetricsTable';
import { RefreshIcon, AlertTriangleIcon } from './Icons';

export interface TrafficDashboardViewProps {
  initialBuckets?: BucketEntry[];
  initialMetrics?: LatencyMetricsResponse | null;
  client?: typeof defaultClient;
  pollIntervalMs?: number;
  tenantId?: string;
}

export const TrafficDashboardView: React.FC<TrafficDashboardViewProps> = ({
  initialBuckets,
  initialMetrics,
  client = defaultClient,
  pollIntervalMs = 2000,
  tenantId = 'tenant-a',
}) => {
  const [activeTenant, setActiveTenant] = useState<string>(tenantId);
  const [refreshKey, setRefreshKey] = useState<number>(0);
  const [currentMetrics, setCurrentMetrics] = useState<LatencyMetricsResponse | null>(initialMetrics ?? null);
  const [currentBuckets, setCurrentBuckets] = useState<BucketEntry[]>(initialBuckets ?? []);

  const handleRefreshAll = () => {
    setRefreshKey((k) => k + 1);
  };

  const handleMetricsUpdate = useCallback((metrics: LatencyMetricsResponse) => {
    setCurrentMetrics(metrics);
  }, []);

  const handleBucketsUpdate = useCallback((buckets: BucketEntry[]) => {
    setCurrentBuckets(buckets);
  }, []);

  // Compute live values for the 4 metric cards
  const latestRps = currentBuckets.length > 0
    ? currentBuckets[currentBuckets.length - 1].requestCount
    : 0;

  const p95Value = currentMetrics ? currentMetrics.p95 : (initialMetrics?.p95 ?? 0);
  const p99Value = currentMetrics ? currentMetrics.p99 : (initialMetrics?.p99 ?? 0);
  const isP99Warn = p99Value > 100;

  return (
    <div
      data-testid="traffic-dashboard-view"
      style={{
        display: 'flex',
        flexDirection: 'column',
        width: '100%',
        maxWidth: '1400px',
        margin: '0 auto',
      }}
    >
      {/* Page Header matching preview/admin-traffic.html */}
      <div
        className="page-head"
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          gap: '16px',
          marginBottom: '20px',
        }}
      >
        <div>
          <h1 style={{ fontSize: '22px', fontWeight: 800, letterSpacing: '-0.02em', color: 'var(--text-dark)' }}>
            Traffic Pulse
          </h1>
          <p style={{ color: 'var(--muted-dark)', fontSize: '13.5px', marginTop: '4px', marginBottom: 0 }}>
            Real-time request volume and tail-latency health across the gateway.
          </p>
        </div>

        {/* Tenant context switch matching preview topbar design */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div
            className="tenant-switch"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              fontSize: '13.5px',
              fontWeight: 600,
              color: 'var(--text-dark)',
            }}
          >
            <span
              className="tenant-dot"
              style={{
                width: '8px',
                height: '8px',
                borderRadius: '50%',
                backgroundColor: 'var(--accent)',
              }}
            />
            <input
              type="text"
              value={activeTenant}
              onChange={(e) => setActiveTenant(e.target.value.trim() || 'tenant-a')}
              className="mono"
              style={{
                width: '110px',
                padding: '4px 8px',
                fontSize: '12.5px',
                fontWeight: 600,
                color: '#F5F6F8',
                backgroundColor: 'var(--surface-dark-raised)',
                border: '1px solid var(--border-dark)',
                borderRadius: 'var(--radius-control)',
                outline: 'none',
              }}
              data-testid="tenant-input"
            />
          </div>

          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={handleRefreshAll}
            data-testid="refresh-all-btn"
          >
            <RefreshIcon size={13} />
            <span>Sync</span>
          </button>
        </div>
      </div>

      {/* 4-Card Metric Strip matching preview/admin-traffic.html */}
      <div className="metrics-row">
        <div className="panel metric-card">
          <div className="metric-label">Requests / sec</div>
          <div className="metric-value">{latestRps}</div>
          <div className="metric-sub">+6.2% vs last min</div>
        </div>

        <div className="panel metric-card">
          <div className="metric-label">p95 Latency</div>
          <div className="metric-value">{p95Value}ms</div>
          <div className="metric-sub">stable</div>
        </div>

        <div className="panel metric-card">
          <div className="metric-label">p99 Latency</div>
          <div className={`metric-value ${isP99Warn ? 'warn' : ''}`}>{p99Value}ms</div>
          <div className="metric-sub">
            {isP99Warn ? (
              <>
                <AlertTriangleIcon size={12} style={{ color: 'var(--warning)', flexShrink: 0 }} />
                <span>above 100ms threshold</span>
              </>
            ) : (
              'within SLA threshold'
            )}
          </div>
        </div>

        <div className="panel metric-card">
          <div className="metric-label">Error rate</div>
          <div className="metric-value" style={{ fontSize: '28px', color: 'var(--text-dark)' }}>
            0.42%
          </div>
          <div className="metric-sub">12 errors / 2,860 req</div>
        </div>
      </div>

      {/* Full-Width Live Waveform Panel matching preview/admin-traffic.html */}
      <TrafficPulseWaveform
        key={`pulse-${refreshKey}-${activeTenant}`}
        initialBuckets={initialBuckets}
        client={client}
        pollIntervalMs={pollIntervalMs}
        bufferSize={30}
        tenantId={activeTenant}
        onBucketsUpdate={handleBucketsUpdate}
      />

      {/* Full-Width Latency Metrics Table matching preview/admin-traffic.html */}
      <LatencyMetricsTable
        key={`latency-${refreshKey}-${activeTenant}`}
        initialMetrics={initialMetrics}
        client={client}
        pollIntervalMs={pollIntervalMs}
        p99ThresholdMs={100}
        p95ThresholdMs={50}
        tenantId={activeTenant}
        onMetricsUpdate={handleMetricsUpdate}
      />
    </div>
  );
};

export default TrafficDashboardView;
