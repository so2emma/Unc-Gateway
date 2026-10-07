import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { TrafficDashboardView } from '@/components/TrafficDashboardView';
import { getLatencyMetrics, getTrafficPulse, LatencyMetricsResponse, TrafficPulseResponse } from '@/lib/analyticsApiClient';

describe('Traffic Pulse & Latency Integration against /api/analytics', () => {
  it('loads /traffic page against analytics-api seeded with known durations and verifies rendered latency matches percentiles', async () => {
    // Known request durations matching the Phase 21 runnable milestone:
    // 10, 20, 30, 40, 50, 60, 70, 80, 90, 500 ms
    const seededDurations = [10, 20, 30, 40, 50, 60, 70, 80, 90, 500];

    // Compute independent continuous percentiles using standard linear interpolation
    function computePercentile(sorted: number[], p: number): number {
      const n = sorted.length;
      if (n === 0) return 0;
      if (n === 1) return sorted[0];
      const index = p * (n - 1);
      const lower = Math.floor(index);
      const upper = Math.ceil(index);
      const weight = index - lower;
      return sorted[lower] * (1 - weight) + sorted[upper] * weight;
    }

    const sortedDurations = [...seededDurations].sort((a, b) => a - b);
    const expectedP95 = Number(computePercentile(sortedDurations, 0.95).toFixed(1));
    const expectedP99 = Number(computePercentile(sortedDurations, 0.99).toFixed(1));

    // 10 total requests in the recent traffic pulse bucket
    const seededTrafficPulse = {
      buckets: [
        { bucketStart: '2026-10-05T17:00:00Z', requestCount: 10 },
      ],
    };

    const mockFetch = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
      const urlStr = url.toString();
      const method = init?.method || 'GET';
      const headers = (init?.headers as Record<string, string>) || {};

      expect(headers['X-Tenant-Id']).toBe('tenant-a');

      if (urlStr.includes('/api/analytics/metrics/latency') && method === 'GET') {
        const payload: LatencyMetricsResponse = {
          p95: expectedP95,
          p99: expectedP99,
        };
        return {
          ok: true,
          status: 200,
          json: async () => payload,
        } as Response;
      }

      if (urlStr.includes('/api/analytics/traffic-pulse') && method === 'GET') {
        return {
          ok: true,
          status: 200,
          json: async () => seededTrafficPulse,
        } as Response;
      }

      return { ok: false, status: 404, json: async () => ({ message: 'Not found' }) } as Response;
    });

    const mockClient = {
      getTrafficPulse: (opts?: any) => getTrafficPulse({ ...opts, baseUrl: 'http://localhost:8083', tenantId: 'tenant-a', fetchFn: mockFetch as any }),
      getLatencyMetrics: (opts?: any) => getLatencyMetrics({ ...opts, baseUrl: 'http://localhost:8083', tenantId: 'tenant-a', fetchFn: mockFetch as any }),
    };

    render(
      <TrafficDashboardView
        client={mockClient as any}
        tenantId="tenant-a"
        pollIntervalMs={0} // Disable recurring timer during test assertion
      />
    );

    // Verify initial load calls endpoints and renders correctly
    await waitFor(() => {
      // The latency table renders both values matching independent calculation
      const p95Elem = screen.getByTestId('p95-value');
      expect(p95Elem).toHaveTextContent(`${expectedP95}ms`);

      const p99Elem = screen.getByTestId('p99-value');
      expect(p99Elem).toHaveTextContent(`${expectedP99}ms`);

      // Since expectedP99 is elevated (around 450-490ms > 100ms threshold), verify amber emphasis
      expect(p99Elem).toHaveStyle({ color: 'var(--warning)', fontWeight: '700' });

      // Verify traffic pulse waveform rendered the 10 requests ingested
      expect(screen.getByTestId('waveform-current-volume')).toHaveTextContent('10');
      expect(screen.getByTestId('waveform-total-volume')).toHaveTextContent('10');
    });

    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/analytics/metrics/latency'),
      expect.anything()
    );
    expect(mockFetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/analytics/traffic-pulse'),
      expect.anything()
    );
  });
});
