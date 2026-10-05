import { NextRequest, NextResponse } from 'next/server';

function getAnalyticsBaseUrl(): string {
  if (process.env.ANALYTICS_API_URL) {
    return process.env.ANALYTICS_API_URL.replace(/\/+$/, '');
  }
  if (process.env.NEXT_PUBLIC_ANALYTICS_API_URL && !process.env.NEXT_PUBLIC_ANALYTICS_API_URL.includes(':3001')) {
    return process.env.NEXT_PUBLIC_ANALYTICS_API_URL.replace(/\/+$/, '');
  }
  // In Docker container environment
  if (process.env.HOSTNAME || process.env.NODE_ENV === 'production') {
    return 'http://analytics-api:8083';
  }
  return 'http://localhost:8083';
}

function resolveTenantId(req: NextRequest): string {
  const headerVal = req.headers.get('x-tenant-id');
  if (headerVal && headerVal.trim()) {
    return headerVal.trim();
  }
  return process.env.DEFAULT_TENANT_ID || process.env.NEXT_PUBLIC_TENANT_ID || 'tenant-a';
}

async function handleProxy(req: NextRequest, { params }: { params: { path: string[] } }) {
  const subpath = params.path ? params.path.join('/') : '';
  const baseUrl = getAnalyticsBaseUrl();
  const search = req.nextUrl.search;
  const targetUrl = `${baseUrl}/api/analytics/${subpath}${search}`;

  const headers = new Headers();
  req.headers.forEach((value, key) => {
    if (key.toLowerCase() !== 'host' && key.toLowerCase() !== 'connection') {
      headers.set(key, value);
    }
  });

  const tenantId = resolveTenantId(req);
  if (!headers.get('x-tenant-id')) {
    headers.set('X-Tenant-Id', tenantId);
  }

  let body: BodyInit | null = null;
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    body = await req.text();
  }

  try {
    const backendRes = await fetch(targetUrl, {
      method: req.method,
      headers,
      body,
    });

    const responseHeaders = new Headers();
    backendRes.headers.forEach((value, key) => {
      if (key.toLowerCase() !== 'content-encoding' && key.toLowerCase() !== 'transfer-encoding') {
        responseHeaders.set(key, value);
      }
    });

    const isBodylessStatus = backendRes.status === 204 || backendRes.status === 205 || backendRes.status === 304;
    const data = isBodylessStatus ? null : await backendRes.text();
    return new NextResponse(data, {
      status: backendRes.status,
      headers: responseHeaders,
    });
  } catch (err: any) {
    console.error(`Analytics proxy error connecting to ${targetUrl}:`, err);
    return NextResponse.json(
      { message: `Failed to connect to Analytics API at ${baseUrl}: ${err.message}` },
      { status: 502 }
    );
  }
}

export const GET = handleProxy;
export const POST = handleProxy;
export const PUT = handleProxy;
export const DELETE = handleProxy;
export const PATCH = handleProxy;
export const OPTIONS = handleProxy;
