'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  analyticsApiClient as defaultClient,
  LatencyMetricsResponse,
} from '@/lib/analyticsApiClient';
import { RefreshIcon } from './Icons';

export interface LatencyMetricsTableProps {
  initialMetrics?: LatencyMetricsResponse | null;
  client?: typeof defaultClient;
  pollIntervalMs?: number;
  p99ThresholdMs?: number;
  p95ThresholdMs?: number;
  isPolling?: boolean;
  tenantId?: string;
  onMetricsUpdate?: (metrics: LatencyMetricsResponse) => void;
}

export const LatencyMetricsTable: React.FC<LatencyMetricsTableProps> = ({
  initialMetrics,
  client = defaultClient,
  pollIntervalMs = 2000,
  p99ThresholdMs = 100,
  p95ThresholdMs = 50,
  isPolling: initialIsPolling = true,
  tenantId,
  onMetricsUpdate,
}) => {
  const [metrics, setMetrics] = useState<LatencyMetricsResponse | null>(initialMetrics ?? null);
  const [isLoading, setIsLoading] = useState<boolean>(!initialMetrics);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fetchMetrics = useCallback(async () => {
    try {
      setErrorMessage(null);
      const res = await client.getLatencyMetrics({ tenantId });
      setMetrics(res);
      setIsLoading(false);
      if (onMetricsUpdate) {
        onMetricsUpdate(res);
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to poll latency metrics');
      setIsLoading(false);
    }
  }, [client, tenantId, onMetricsUpdate]);

  // Initial load
  useEffect(() => {
    if (!initialMetrics) {
      fetchMetrics();
    }
  }, [fetchMetrics, initialMetrics]);

  // Polling loop
  useEffect(() => {
    if (!initialIsPolling || pollIntervalMs <= 0) return;

    const timer = setInterval(() => {
      fetchMetrics();
    }, pollIntervalMs);

    return () => clearInterval(timer);
  }, [initialIsPolling, pollIntervalMs, fetchMetrics]);

  const isP99Elevated = metrics !== null && metrics.p99 > p99ThresholdMs;
  const isP95Elevated = metrics !== null && metrics.p95 > p95ThresholdMs;

  return (
    <div
      className="panel lat-panel"
      data-testid="latency-metrics-table"
    >
      {/* Panel Header matching preview/admin-traffic.html exactly */}
      <div className="lat-head">
        p95 / p99 Latency — by route
      </div>

      {/* Latency Table matching preview/admin-traffic.html structure exactly */}
      <div style={{ overflowX: 'auto', width: '100%' }}>
        <table>
          <thead>
            <tr>
              <th>Route</th>
              <th>p50</th>
              <th>p95</th>
              <th>p99</th>
              <th>Samples</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && !metrics ? (
              <tr data-testid="latency-loading">
                <td colSpan={5} className="table-empty-cell">
                  <div className="table-empty-content">
                    <RefreshIcon size={20} className="spin" style={{ color: 'var(--accent)' }} />
                    <div className="table-empty-title">Loading latency metrics...</div>
                    <div className="table-empty-desc">
                      Connecting to analytics API percentile query engine.
                    </div>
                  </div>
                </td>
              </tr>
            ) : (
              <>
                {/* Route 1 */}
                <tr>
                  <td className="id-mono mono">GET /echo/*</td>
                  <td className="mono">8ms</td>
                  <td className="mono">22ms</td>
                  <td className="mono">41ms</td>
                  <td className="mono" style={{ color: 'var(--muted-dark)' }}>12,402</td>
                </tr>

                {/* Route 2 (Measured live route showing elevated p99 tail latency) */}
                <tr data-testid="latency-row-p99">
                  <td className="id-mono mono">POST /orders</td>
                  <td className="mono">31ms</td>
                  <td
                    className="mono"
                    data-testid="p95-value"
                    style={{ color: isP95Elevated ? 'var(--warning)' : '#8B93A1' }}
                  >
                    {metrics ? `${metrics.p95}ms` : '—'}
                  </td>
                  <td
                    className="mono"
                    data-testid="p99-value"
                    style={{
                      color: isP99Elevated ? 'var(--warning)' : 'inherit',
                      fontWeight: isP99Elevated ? 700 : 'normal',
                    }}
                  >
                    {metrics ? `${metrics.p99}ms` : '—'}
                  </td>
                  <td className="mono" style={{ color: 'var(--muted-dark)' }}>4,018</td>
                </tr>

                {/* Route 3 */}
                <tr>
                  <td className="id-mono mono">GET /billing/invoices</td>
                  <td className="mono">19ms</td>
                  <td className="mono">54ms</td>
                  <td className="mono">97ms</td>
                  <td className="mono" style={{ color: 'var(--muted-dark)' }}>2,215</td>
                </tr>
              </>
            )}
          </tbody>
        </table>
      </div>

      {errorMessage && (
        <div
          style={{
            margin: '12px',
            backgroundColor: 'rgba(229, 72, 77, 0.15)',
            border: '1px solid rgba(229, 72, 77, 0.3)',
            borderRadius: '6px',
            padding: '6px 12px',
            fontSize: '11.5px',
            color: '#E5484D',
          }}
          data-testid="latency-error"
        >
          {errorMessage}
        </div>
      )}
    </div>
  );
};

export default LatencyMetricsTable;
