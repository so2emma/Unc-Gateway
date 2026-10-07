export interface BucketEntry {
  bucketStart: string;
  requestCount: number;
}

export interface TrafficPulseResponse {
  buckets: BucketEntry[];
}

export interface LatencyMetricsResponse {
  p95: number;
  p99: number;
}

export interface AnalyticsApiClientOptions {
  baseUrl?: string;
  tenantId?: string;
  fetchFn?: typeof fetch;
}

export interface TrafficPulseOptions extends AnalyticsApiClientOptions {
  limit?: number;
}

export class AnalyticsApiError extends Error {
  status: number;
  data?: any;

  constructor(message: string, status: number, data?: any) {
    super(message);
    this.name = 'AnalyticsApiError';
    this.status = status;
    this.data = data;
  }
}

export function resolveAnalyticsBaseUrl(options?: AnalyticsApiClientOptions): string {
  if (options?.baseUrl !== undefined) {
    return options.baseUrl.replace(/\/+$/, '');
  }
  if (typeof process !== 'undefined' && process.env?.NEXT_PUBLIC_ANALYTICS_API_URL) {
    return process.env.NEXT_PUBLIC_ANALYTICS_API_URL.replace(/\/+$/, '');
  }
  if (typeof process !== 'undefined' && process.env?.ANALYTICS_API_URL) {
    return process.env.ANALYTICS_API_URL.replace(/\/+$/, '');
  }
  // In browser, relative URL proxies through Next.js route handler
  if (typeof window !== 'undefined') {
    return '';
  }
  return 'http://localhost:8083';
}

export function resolveTenantId(options?: AnalyticsApiClientOptions): string {
  if (options?.tenantId) return options.tenantId;
  if (typeof window !== 'undefined') {
    const sessionVal = window.sessionStorage?.getItem('unc_tenant_id');
    if (sessionVal) return sessionVal;
    const localVal = window.localStorage?.getItem('unc_tenant_id');
    if (localVal) return localVal;
  }
  if (typeof process !== 'undefined' && process.env?.NEXT_PUBLIC_TENANT_ID) {
    return process.env.NEXT_PUBLIC_TENANT_ID;
  }
  if (typeof process !== 'undefined' && process.env?.DEFAULT_TENANT_ID) {
    return process.env.DEFAULT_TENANT_ID;
  }
  // Default operator tenant fallback
  return 'tenant-a';
}

function buildHeaders(options?: AnalyticsApiClientOptions): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  const tenantId = resolveTenantId(options);
  if (tenantId) {
    headers['X-Tenant-Id'] = tenantId;
  }
  return headers;
}

async function handleResponse<T>(res: Response, fallbackMessage: string): Promise<T> {
  if (!res.ok) {
    let errorMessage = `${fallbackMessage}: HTTP ${res.status}`;
    let errorData: any = null;
    try {
      errorData = await res.json();
      if (errorData && typeof errorData.message === 'string' && errorData.message.trim()) {
        errorMessage = errorData.message;
      }
    } catch {
      // Non-JSON response
    }
    throw new AnalyticsApiError(errorMessage, res.status, errorData);
  }
  return res.json();
}

/**
 * Normalizes backend bucket payload structure to standard BucketEntry array.
 * Supports { buckets: [...] }, { entries: [...] }, { data: [...] }, or raw arrays.
 */
function normalizeBuckets(raw: any): BucketEntry[] {
  let list: any[] = [];
  if (Array.isArray(raw)) {
    list = raw;
  } else if (raw && Array.isArray(raw.buckets)) {
    list = raw.buckets;
  } else if (raw && Array.isArray(raw.entries)) {
    list = raw.entries;
  } else if (raw && Array.isArray(raw.data)) {
    list = raw.data;
  } else if (raw && Array.isArray(raw.items)) {
    list = raw.items;
  }

  return list.map((item) => {
    const bucketStart = item.bucketStart || item.start || item.timestamp || item.time || new Date().toISOString();
    const requestCount = Number(item.requestCount ?? item.count ?? item.requests ?? item.total ?? 0);
    return {
      bucketStart: typeof bucketStart === 'string' ? bucketStart : new Date(bucketStart).toISOString(),
      requestCount: Number.isFinite(requestCount) ? requestCount : 0,
    };
  });
}

/**
 * Normalizes backend latency response structure to { p95, p99 }.
 */
function normalizeLatency(raw: any): LatencyMetricsResponse {
  const p95 = Number(raw?.p95 ?? 0);
  const p99 = Number(raw?.p99 ?? 0);
  return {
    p95: Number.isFinite(p95) ? p95 : 0,
    p99: Number.isFinite(p99) ? p99 : 0,
  };
}

/**
 * Fetches time-bucketed request volume for oscilloscope waveform rendering.
 */
export async function getTrafficPulse(options?: TrafficPulseOptions): Promise<TrafficPulseResponse> {
  const baseUrl = resolveAnalyticsBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;
  const limit = options?.limit;
  const query = limit ? `?limit=${encodeURIComponent(limit)}` : '';

  const res = await fetcher(`${baseUrl}/api/analytics/traffic-pulse${query}`, {
    method: 'GET',
    headers: buildHeaders(options),
  });

  const data = await handleResponse<any>(res, 'Failed to fetch traffic pulse');
  return {
    buckets: normalizeBuckets(data),
  };
}

/**
 * Fetches current p95 and p99 request duration percentiles in milliseconds.
 */
export async function getLatencyMetrics(options?: AnalyticsApiClientOptions): Promise<LatencyMetricsResponse> {
  const baseUrl = resolveAnalyticsBaseUrl(options);
  const fetcher = options?.fetchFn || fetch;

  const res = await fetcher(`${baseUrl}/api/analytics/metrics/latency`, {
    method: 'GET',
    headers: buildHeaders(options),
  });

  const data = await handleResponse<any>(res, 'Failed to fetch latency metrics');
  return normalizeLatency(data);
}

export const analyticsApiClient = {
  getTrafficPulse,
  getLatencyMetrics,
};

export default analyticsApiClient;
