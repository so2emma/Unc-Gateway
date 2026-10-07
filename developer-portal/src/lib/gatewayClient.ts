export interface GatewayRequestOptions {
  path: string;
  method?: string;
  apiKey?: string;
  headers?: Record<string, string>;
  body?: any;
  tenantId?: string;
  baseUrl?: string;
  fetchFn?: typeof fetch;
}

export interface GatewayResponseResult {
  status: number;
  statusText: string;
  headers: Record<string, string>;
  data: any;
  rawBody: string;
  durationMs: number;
  timestamp: string;
  isSuccess: boolean;
  error?: string;
}

export function resolveGatewayBaseUrl(options?: GatewayRequestOptions): string {
  if (options?.baseUrl !== undefined) {
    return options.baseUrl.replace(/\/+$/, '');
  }
  if (typeof process !== 'undefined' && process.env?.NEXT_PUBLIC_GATEWAY_URL) {
    return process.env.NEXT_PUBLIC_GATEWAY_URL.replace(/\/+$/, '');
  }
  if (typeof process !== 'undefined' && process.env?.GATEWAY_URL) {
    return process.env.GATEWAY_URL.replace(/\/+$/, '');
  }
  if (typeof window !== 'undefined') {
    return '/api/gateway';
  }
  return 'http://localhost:8080';
}

function resolveTenantId(options?: GatewayRequestOptions): string | undefined {
  if (options?.tenantId) return options.tenantId;
  if (typeof window !== 'undefined') {
    const stored = window.localStorage.getItem('unc_tenant_id');
    if (stored) return stored;
  }
  if (typeof process !== 'undefined' && process.env?.NEXT_PUBLIC_TENANT_ID) {
    return process.env.NEXT_PUBLIC_TENANT_ID;
  }
  return undefined;
}

export async function sendGatewayRequest(
  options: GatewayRequestOptions
): Promise<GatewayResponseResult> {
  const fetcher = options.fetchFn || fetch;
  const baseUrl = resolveGatewayBaseUrl(options);
  const normalizedPath = options.path.startsWith('/') ? options.path : `/${options.path}`;
  const url = `${baseUrl}${normalizedPath}`;

  const headers: Record<string, string> = {
    Accept: 'application/json, text/plain, */*',
    ...(options.headers || {}),
  };

  if (options.apiKey) {
    headers['X-Api-Key'] = options.apiKey;
  }

  const tenantId = resolveTenantId(options);
  if (tenantId && !headers['X-Tenant-Id']) {
    headers['X-Tenant-Id'] = tenantId;
  }

  const method = (options.method || 'GET').toUpperCase();
  const init: RequestInit = {
    method,
    headers,
  };

  if (options.body !== undefined && method !== 'GET' && method !== 'HEAD') {
    if (typeof options.body === 'string') {
      init.body = options.body;
    } else {
      headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(options.body);
    }
  }

  const startTime = Date.now();
  try {
    const response = await fetcher(url, init);
    const durationMs = Date.now() - startTime;
    const rawBody = await response.text();

    let data: any = null;
    if (rawBody) {
      try {
        data = JSON.parse(rawBody);
      } catch {
        data = rawBody;
      }
    }

    const responseHeaders: Record<string, string> = {};
    if (response.headers && typeof response.headers.forEach === 'function') {
      response.headers.forEach((value, key) => {
        responseHeaders[key.toLowerCase()] = value;
      });
    }

    return {
      status: response.status,
      statusText: response.statusText || (response.status === 200 ? 'OK' : ''),
      headers: responseHeaders,
      data,
      rawBody,
      durationMs,
      timestamp: new Date().toISOString(),
      isSuccess: response.status >= 200 && response.status < 300,
    };
  } catch (err: any) {
    const durationMs = Date.now() - startTime;
    return {
      status: 0,
      statusText: 'Network Error',
      headers: {},
      data: null,
      rawBody: err?.message || 'Failed to connect to gateway',
      durationMs,
      timestamp: new Date().toISOString(),
      isSuccess: false,
      error: err?.message || 'Network request failed',
    };
  }
}

export const gatewayClient = {
  sendGatewayRequest,
  resolveGatewayBaseUrl,
};

export default gatewayClient;
