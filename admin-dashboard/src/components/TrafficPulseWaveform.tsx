'use client';

import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
  analyticsApiClient as defaultClient,
  BucketEntry,
  TrafficPulseResponse,
} from '@/lib/analyticsApiClient';
import { RefreshIcon, PauseIcon, PlayIcon } from './Icons';

export interface TrafficPulseWaveformProps {
  initialBuckets?: BucketEntry[];
  client?: typeof defaultClient;
  pollIntervalMs?: number;
  bufferSize?: number;
  isPolling?: boolean;
  tenantId?: string;
  onBucketsUpdate?: (buckets: BucketEntry[]) => void;
  height?: number;
}

/**
 * Merges incoming buckets into the waveform buffer, preserving strict chronological
 * order, evicting the oldest buckets once maxWindowSize is exceeded.
 */
export function updateWaveformBuffer(
  currentBuffer: BucketEntry[],
  incomingBuckets: BucketEntry[],
  maxWindowSize: number
): BucketEntry[] {
  if (!incomingBuckets || incomingBuckets.length === 0) {
    return currentBuffer;
  }

  const map = new Map<string, BucketEntry>();
  for (const b of currentBuffer) {
    map.set(b.bucketStart, b);
  }
  for (const b of incomingBuckets) {
    map.set(b.bucketStart, b);
  }

  // Sort strictly ascending by timestamp (never mutates or disrupts chronological order)
  const sorted = Array.from(map.values()).sort(
    (a, b) => new Date(a.bucketStart).getTime() - new Date(b.bucketStart).getTime()
  );

  // Evict oldest buckets once buffer exceeds maxWindowSize
  if (sorted.length > maxWindowSize) {
    return sorted.slice(sorted.length - maxWindowSize);
  }
  return sorted;
}

export const TrafficPulseWaveform: React.FC<TrafficPulseWaveformProps> = ({
  initialBuckets,
  client = defaultClient,
  pollIntervalMs = 2000,
  bufferSize = 30,
  isPolling: initialIsPolling = true,
  tenantId,
  onBucketsUpdate,
  height = 220,
}) => {
  const [buckets, setBuckets] = useState<BucketEntry[]>(() => {
    if (initialBuckets && initialBuckets.length > 0) {
      return updateWaveformBuffer([], initialBuckets, bufferSize);
    }
    return [];
  });
  const [isLive, setIsLive] = useState<boolean>(initialIsPolling);
  const [isLoading, setIsLoading] = useState<boolean>(!initialBuckets || initialBuckets.length === 0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const bucketsRef = useRef<BucketEntry[]>(buckets);
  bucketsRef.current = buckets;

  const fetchPulse = useCallback(async () => {
    try {
      setErrorMessage(null);
      const res = await client.getTrafficPulse({ limit: bufferSize, tenantId });
      const nextBuffer = updateWaveformBuffer(bucketsRef.current, res.buckets, bufferSize);
      setBuckets(nextBuffer);
      setIsLoading(false);
      if (onBucketsUpdate) {
        onBucketsUpdate(nextBuffer);
      }
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to poll traffic pulse');
      setIsLoading(false);
    }
  }, [client, bufferSize, tenantId, onBucketsUpdate]);

  // Initial load
  useEffect(() => {
    if (!initialBuckets || initialBuckets.length === 0) {
      fetchPulse();
    }
  }, [fetchPulse, initialBuckets]);

  // Polling loop
  useEffect(() => {
    if (!isLive || pollIntervalMs <= 0) return;

    const timer = setInterval(() => {
      fetchPulse();
    }, pollIntervalMs);

    return () => clearInterval(timer);
  }, [isLive, pollIntervalMs, fetchPulse]);

  // Key summary statistics
  const { currentRps, peakRps, totalRequests } = useMemo(() => {
    if (buckets.length === 0) {
      return { currentRps: 0, peakRps: 0, totalRequests: 0 };
    }
    const current = buckets[buckets.length - 1].requestCount;
    let peak = 0;
    let total = 0;
    for (const b of buckets) {
      if (b.requestCount > peak) peak = b.requestCount;
      total += b.requestCount;
    }
    return { currentRps: current, peakRps: peak, totalRequests: total };
  }, [buckets]);

  // Waveform SVG Path Geometry Calculation (matches preview/admin-traffic.html 1000x220)
  const viewBoxWidth = 1000;
  const viewBoxHeight = height;
  const paddingTop = 30;
  const paddingBottom = 20;
  const drawHeight = viewBoxHeight - paddingTop - paddingBottom;

  const { linePath, areaPath, lastPoint } = useMemo(() => {
    if (buckets.length === 0) {
      const midY = viewBoxHeight - paddingBottom;
      return {
        linePath: `M 0,${midY} L ${viewBoxWidth},${midY}`,
        areaPath: `M 0,${viewBoxHeight} L 0,${midY} L ${viewBoxWidth},${midY} L ${viewBoxWidth},${viewBoxHeight} Z`,
        lastPoint: null,
      };
    }

    const maxVolume = Math.max(...buckets.map((b) => b.requestCount), 10);
    const n = buckets.length;

    const points = buckets.map((bucket, i) => {
      const x = n > 1 ? (i / (n - 1)) * viewBoxWidth : viewBoxWidth;
      const normalizedY = (bucket.requestCount / maxVolume) * drawHeight;
      const y = viewBoxHeight - paddingBottom - normalizedY;
      return { x, y };
    });

    const lPath = points.reduce((acc, pt, i) => `${acc} ${i === 0 ? 'M' : 'L'} ${pt.x.toFixed(1)},${pt.y.toFixed(1)}`, '');
    const aPath = `${lPath} L ${viewBoxWidth},${viewBoxHeight} L 0,${viewBoxHeight} Z`;

    return {
      linePath: lPath,
      areaPath: aPath,
      lastPoint: points[points.length - 1],
    };
  }, [buckets, viewBoxHeight, paddingBottom, drawHeight]);

  return (
    <div
      className="panel wave-panel"
      data-testid="traffic-pulse-waveform"
      style={{ backgroundColor: '#1C1F26' }}
    >
      {/* Waveform Panel Header matching preview/admin-traffic.html */}
      <div className="wave-head">
        <div className="wave-title">
          <span className="live-dot" />
          <span>Live request volume</span>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <span className="pill pill-accent mono">GET /api/analytics/traffic-pulse</span>

          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => setIsLive((prev) => !prev)}
            title={isLive ? 'Pause live stream' : 'Resume live stream'}
            data-testid="toggle-stream-btn"
            style={{ padding: '4px 8px', fontSize: '11px', height: '24px' }}
          >
            {isLive ? <PauseIcon size={12} /> : <PlayIcon size={12} />}
            <span>{isLive ? 'Pause' : 'Resume'}</span>
          </button>

          <button
            type="button"
            className="btn btn-secondary btn-sm"
            onClick={() => fetchPulse()}
            title="Poll traffic pulse now"
            data-testid="refresh-stream-btn"
            style={{ padding: '4px 8px', fontSize: '11px', height: '24px' }}
          >
            <RefreshIcon size={12} />
          </button>
        </div>
      </div>

      {/* Scope Container matching preview/admin-traffic.html */}
      <div className="scope-wrap">
        <svg
          width="100%"
          height={height}
          viewBox={`0 0 ${viewBoxWidth} ${viewBoxHeight}`}
          preserveAspectRatio="none"
          data-testid="waveform-svg"
        >
          <defs>
            <linearGradient id="waveFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#FF6B35" stopOpacity="0.35" />
              <stop offset="100%" stopColor="#FF6B35" stopOpacity="0" />
            </linearGradient>
          </defs>

          {/* Area gradient fill */}
          <path
            d={areaPath}
            fill="url(#waveFill)"
            data-testid="waveform-area"
          />

          {/* Glowing orange line */}
          <path
            d={linePath}
            fill="none"
            stroke="#FF6B35"
            strokeWidth="2.5"
            strokeLinejoin="round"
            strokeLinecap="round"
            data-testid="waveform-path"
          />

          {lastPoint && (
            <circle cx={lastPoint.x} cy={lastPoint.y} r="3.5" fill="#FF6B35" />
          )}
        </svg>

        {isLoading && (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              backgroundColor: 'rgba(21, 23, 28, 0.65)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '8px',
              color: 'var(--muted-dark)',
              fontSize: '12px',
            }}
            data-testid="waveform-loading"
          >
            <RefreshIcon size={14} className="spin" />
            <span>Connecting to live traffic stream...</span>
          </div>
        )}

        {errorMessage && (
          <div
            style={{
              position: 'absolute',
              bottom: '10px',
              left: '12px',
              right: '12px',
              backgroundColor: 'rgba(229, 72, 77, 0.15)',
              border: '1px solid rgba(229, 72, 77, 0.3)',
              borderRadius: '6px',
              padding: '6px 12px',
              fontSize: '11.5px',
              color: '#E5484D',
            }}
            data-testid="waveform-error"
          >
            {errorMessage}
          </div>
        )}
      </div>

      {/* Accessible telemetry elements preserving unit/integration test compatibility */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          fontSize: '11px',
          color: 'var(--muted-dark)',
          marginTop: '10px',
        }}
      >
        <span className="mono">
          Buffer Window: <strong style={{ color: 'var(--text-dark)' }} data-testid="waveform-bucket-count">{buckets.length}</strong> / {bufferSize} buckets
        </span>
        <div style={{ display: 'flex', gap: '16px' }} className="mono">
          <span>
            Current: <strong style={{ color: 'var(--accent)' }} data-testid="waveform-current-volume">{currentRps}</strong> req
          </span>
          <span>
            Peak: <strong style={{ color: 'var(--text-dark)' }} data-testid="waveform-peak-volume">{peakRps}</strong> req
          </span>
          <span>
            Total: <strong style={{ color: 'var(--text-dark)' }} data-testid="waveform-total-volume">{totalRequests}</strong> req
          </span>
        </div>
      </div>

      <ul
        data-testid="waveform-buffer-list"
        style={{
          position: 'absolute',
          width: '1px',
          height: '1px',
          padding: 0,
          margin: '-1px',
          overflow: 'hidden',
          clip: 'rect(0, 0, 0, 0)',
          whiteSpace: 'nowrap',
          border: 0,
        }}
      >
        {buckets.map((b, idx) => (
          <li
            key={`${b.bucketStart}-${idx}`}
            data-testid={`waveform-bucket-${b.bucketStart}`}
            data-bucket-start={b.bucketStart}
            data-request-count={b.requestCount}
          >
            {b.bucketStart}: {b.requestCount}
          </li>
        ))}
      </ul>
    </div>
  );
};

export default TrafficPulseWaveform;
