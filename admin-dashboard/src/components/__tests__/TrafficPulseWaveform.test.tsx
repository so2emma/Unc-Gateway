import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import {
  TrafficPulseWaveform,
  updateWaveformBuffer,
} from '../TrafficPulseWaveform';
import { analyticsApiClient, BucketEntry } from '@/lib/analyticsApiClient';

describe('TrafficPulseWaveform Component', () => {
  const seedBuckets: BucketEntry[] = [
    { bucketStart: '2026-10-05T12:00:00Z', requestCount: 10 },
    { bucketStart: '2026-10-05T12:00:01Z', requestCount: 20 },
    { bucketStart: '2026-10-05T12:00:02Z', requestCount: 30 },
  ];

  it('verifies updateWaveformBuffer appends new buckets, preserves order, and evicts oldest when exceeding window size', () => {
    const initial: BucketEntry[] = [
      { bucketStart: '2026-10-05T12:00:01Z', requestCount: 10 },
      { bucketStart: '2026-10-05T12:00:02Z', requestCount: 20 },
    ];

    // Receiving a new bucket
    const incoming: BucketEntry[] = [
      { bucketStart: '2026-10-05T12:00:03Z', requestCount: 30 },
    ];

    const buf3 = updateWaveformBuffer(initial, incoming, 3);
    expect(buf3).toHaveLength(3);
    expect(buf3.map((b) => b.bucketStart)).toEqual([
      '2026-10-05T12:00:01Z',
      '2026-10-05T12:00:02Z',
      '2026-10-05T12:00:03Z',
    ]);
    expect(buf3.map((b) => b.requestCount)).toEqual([10, 20, 30]);

    // Adding another bucket when maxWindowSize = 3 (should evict oldest: 12:00:01Z)
    const nextIncoming: BucketEntry[] = [
      { bucketStart: '2026-10-05T12:00:04Z', requestCount: 40 },
    ];
    const bufExceeded = updateWaveformBuffer(buf3, nextIncoming, 3);
    expect(bufExceeded).toHaveLength(3);
    // Oldest bucket (12:00:01Z) is evicted
    expect(bufExceeded.find((b) => b.bucketStart === '2026-10-05T12:00:01Z')).toBeUndefined();
    // Strictly ordered ascending
    expect(bufExceeded.map((b) => b.bucketStart)).toEqual([
      '2026-10-05T12:00:02Z',
      '2026-10-05T12:00:03Z',
      '2026-10-05T12:00:04Z',
    ]);
    expect(bufExceeded.map((b) => b.requestCount)).toEqual([20, 30, 40]);
  });

  it('renders initial waveform with seeded buckets and displays Ignite dark theme panel attributes', () => {
    render(
      <TrafficPulseWaveform
        initialBuckets={seedBuckets}
        bufferSize={5}
        isPolling={false}
      />
    );

    const panel = screen.getByTestId('traffic-pulse-waveform');
    expect(panel).toBeInTheDocument();
    expect(panel).toHaveStyle({ backgroundColor: '#1C1F26' });

    const svg = screen.getByTestId('waveform-svg');
    expect(svg).toBeInTheDocument();

    const path = screen.getByTestId('waveform-path');
    expect(path).toHaveAttribute('stroke', '#FF6B35');

    const area = screen.getByTestId('waveform-area');
    expect(area).toBeInTheDocument();

    expect(screen.getByTestId('waveform-current-volume')).toHaveTextContent('30');
    expect(screen.getByTestId('waveform-peak-volume')).toHaveTextContent('30');
    expect(screen.getByTestId('waveform-total-volume')).toHaveTextContent('60');
  });

  it('appends new bucket from poll callback, evicts oldest once buffer exceeds fixed window size, and never mutates bucket order', async () => {
    let callCount = 0;
    const mockClient = {
      ...analyticsApiClient,
      getTrafficPulse: vi.fn(async () => {
        callCount++;
        if (callCount === 1) {
          return {
            buckets: [
              { bucketStart: '2026-10-05T12:00:01Z', requestCount: 10 },
              { bucketStart: '2026-10-05T12:00:02Z', requestCount: 20 },
            ],
          };
        }
        if (callCount === 2) {
          // New bucket arriving in second poll
          return {
            buckets: [
              { bucketStart: '2026-10-05T12:00:03Z', requestCount: 30 },
            ],
          };
        }
        // Third poll exceeds windowSize of 3: adds 12:00:04Z
        return {
          buckets: [
            { bucketStart: '2026-10-05T12:00:04Z', requestCount: 40 },
          ],
        };
      }),
      getLatencyMetrics: vi.fn(),
    };

    render(
      <TrafficPulseWaveform
        client={mockClient}
        bufferSize={3}
        pollIntervalMs={50}
        isPolling={true}
      />
    );

    // Initial load: 2 buckets
    await waitFor(() => {
      expect(screen.getByTestId('waveform-bucket-2026-10-05T12:00:01Z')).toBeInTheDocument();
      expect(screen.getByTestId('waveform-bucket-2026-10-05T12:00:02Z')).toBeInTheDocument();
    });

    // Second poll: appends 12:00:03Z, total 3 buckets
    await waitFor(() => {
      expect(screen.getByTestId('waveform-bucket-2026-10-05T12:00:03Z')).toBeInTheDocument();
      expect(screen.getByTestId('waveform-bucket-count')).toHaveTextContent('3');
    });

    // Third poll: exceeds bufferSize of 3. Bucket 12:00:01Z evicted!
    await waitFor(() => {
      expect(screen.getByTestId('waveform-bucket-2026-10-05T12:00:04Z')).toBeInTheDocument();
      expect(screen.queryByTestId('waveform-bucket-2026-10-05T12:00:01Z')).not.toBeInTheDocument();
      expect(screen.getByTestId('waveform-bucket-count')).toHaveTextContent('3');
    });

    // Verify bucket order is never mutated: remaining elements are 12:00:02Z, 12:00:03Z, 12:00:04Z
    const list = screen.getByTestId('waveform-buffer-list');
    const items = list.querySelectorAll('li');
    expect(items).toHaveLength(3);
    expect(items[0]).toHaveAttribute('data-bucket-start', '2026-10-05T12:00:02Z');
    expect(items[1]).toHaveAttribute('data-bucket-start', '2026-10-05T12:00:03Z');
    expect(items[2]).toHaveAttribute('data-bucket-start', '2026-10-05T12:00:04Z');
  });

  it('renders waveform reflecting sequence of seeded payloads with oldest no longer present after window exceeded', async () => {
    // Seeded sequence with window size 3
    const seededPayloadSequence = [
      { bucketStart: '2026-10-05T12:00:10Z', requestCount: 15 },
      { bucketStart: '2026-10-05T12:00:20Z', requestCount: 25 },
      { bucketStart: '2026-10-05T12:00:30Z', requestCount: 35 },
      { bucketStart: '2026-10-05T12:00:40Z', requestCount: 45 },
    ];

    let step = 0;
    const mockClient = {
      ...analyticsApiClient,
      getTrafficPulse: vi.fn(async () => {
        const item = seededPayloadSequence[step];
        if (step < seededPayloadSequence.length - 1) {
          step++;
        }
        return { buckets: [item] };
      }),
      getLatencyMetrics: vi.fn(),
    };

    render(
      <TrafficPulseWaveform
        client={mockClient}
        bufferSize={3}
        pollIntervalMs={30}
        isPolling={true}
      />
    );

    // After all 4 seeded payloads are processed through the scrolling buffer window of 3:
    await waitFor(() => {
      // The newest bucket 12:00:40Z must be present
      expect(screen.getByTestId('waveform-bucket-2026-10-05T12:00:40Z')).toBeInTheDocument();
      // The first seeded bucket 12:00:10Z must NO LONGER be present
      expect(screen.queryByTestId('waveform-bucket-2026-10-05T12:00:10Z')).not.toBeInTheDocument();
    });

    // Assert the rendered waveform path has been updated
    const path = screen.getByTestId('waveform-path');
    expect(path).toBeInTheDocument();
    expect(screen.getByTestId('waveform-current-volume')).toHaveTextContent('45');
  });
});
