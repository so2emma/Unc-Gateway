import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { LatencyMetricsTable } from '../LatencyMetricsTable';
import { analyticsApiClient, LatencyMetricsResponse } from '@/lib/analyticsApiClient';

describe('LatencyMetricsTable Component', () => {
  it('renders a loading/placeholder state before the first successful poll response', () => {
    // Client whose promise remains unresolved
    const pendingClient = {
      ...analyticsApiClient,
      getLatencyMetrics: vi.fn(() => new Promise<LatencyMetricsResponse>(() => {})),
      getTrafficPulse: vi.fn(),
    };

    render(
      <LatencyMetricsTable
        client={pendingClient}
        isPolling={false}
      />
    );

    const loadingElem = screen.getByTestId('latency-loading');
    expect(loadingElem).toBeInTheDocument();
    expect(loadingElem).toHaveTextContent(/loading latency metrics/i);
  });

  it('verifies that given a LatencyMetricsResponse-shaped payload (p95, p99), the component renders both values in the table row unchanged', async () => {
    const payload: LatencyMetricsResponse = {
      p95: 38.4,
      p99: 82.1,
    };

    render(
      <LatencyMetricsTable
        initialMetrics={payload}
        isPolling={false}
      />
    );

    // Verify route row and unchanged values
    const p99Row = screen.getByTestId('latency-row-p99');
    expect(p99Row).toBeInTheDocument();
    const p95Val = screen.getByTestId('p95-value');
    expect(p95Val).toHaveTextContent('38.4ms');
    const p99Val = screen.getByTestId('p99-value');
    expect(p99Val).toHaveTextContent('82.1ms');
  });

  it('renders p99 in bold warning-amber (var(--warning)) when above healthy threshold, against cooler-toned p95', async () => {
    const elevatedPayload: LatencyMetricsResponse = {
      p95: 45.0,
      p99: 500.0, // High outlier above 100ms threshold
    };

    render(
      <LatencyMetricsTable
        initialMetrics={elevatedPayload}
        p99ThresholdMs={100}
        p95ThresholdMs={50}
        isPolling={false}
      />
    );

    const p99Val = screen.getByTestId('p99-value');
    // Emphasized bold and warning-amber var(--warning) matching preview/admin-traffic.html
    expect(p99Val).toHaveStyle({ color: 'var(--warning)', fontWeight: '700' });
    expect(p99Val).toHaveTextContent('500ms');

    // Cooler-toned p95 (healthy under 50ms)
    const p95Val = screen.getByTestId('p95-value');
    expect(p95Val).toHaveStyle({ color: '#8B93A1' });
    expect(p95Val).toHaveTextContent('45ms');
  });

  it('updates metrics on subsequent poll callback', async () => {
    let pollCount = 0;
    const mockClient = {
      ...analyticsApiClient,
      getLatencyMetrics: vi.fn(async () => {
        pollCount++;
        if (pollCount === 1) {
          return { p95: 20.0, p99: 50.0 };
        }
        return { p95: 25.0, p99: 300.0 };
      }),
      getTrafficPulse: vi.fn(),
    };

    render(
      <LatencyMetricsTable
        client={mockClient}
        pollIntervalMs={50}
        p99ThresholdMs={100}
        isPolling={true}
      />
    );

    // Initial poll
    await waitFor(() => {
      expect(screen.getByTestId('p95-value')).toHaveTextContent('20ms');
      expect(screen.getByTestId('p99-value')).toHaveTextContent('50ms');
    });

    // Subsequent poll updates p99 to elevated 300 ms
    await waitFor(() => {
      expect(screen.getByTestId('p95-value')).toHaveTextContent('25ms');
      const p99Val = screen.getByTestId('p99-value');
      expect(p99Val).toHaveTextContent('300ms');
      expect(p99Val).toHaveStyle({ color: 'var(--warning)', fontWeight: '700' });
    });
  });
});
