import { NextRequest, NextResponse } from 'next/server';

let cachedDefaultTenant: { id: string; apiKey: string } | null = null;

function getBackendBaseUrl(): string {
  if (process.env.ADMIN_API_URL) {
    return process.env.ADMIN_API_URL.replace(/\/+$/, '');
  }
  if (process.env.NEXT_PUBLIC_ADMIN_API_URL && !process.env.NEXT_PUBLIC_ADMIN_API_URL.includes(':3000')) {
    return process.env.NEXT_PUBLIC_ADMIN_API_URL.replace(/\/+$/, '');
  }
  // In Docker container environment
  if (process.env.HOSTNAME || process.env.NODE_ENV === 'production') {
    return 'http://admin-api:8081';
  }
  return 'http://localhost:8082';
}

async function resolveTenantCredentials(baseUrl: string): Promise<{ id: string; apiKey: string } | null> {
  const envTenantId = process.env.DEFAULT_TENANT_ID || process.env.NEXT_PUBLIC_TENANT_ID;
  const envApiKey = process.env.DEFAULT_ADMIN_API_KEY || process.env.NEXT_PUBLIC_ADMIN_API_KEY;

  if (envTenantId && envApiKey) {
    return { id: envTenantId, apiKey: envApiKey };
  }

  if (cachedDefaultTenant) {
    return cachedDefaultTenant;
  }

  try {
    const res = await fetch(`${baseUrl}/api/admin/tenants`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: 'Default Tenant',
        email: 'ops@unc.dev',
      }),
    });
    if (res.ok) {
      const data = await res.json();
      cachedDefaultTenant = {
        id: data.id || data.tenantId,
        apiKey: data.apiKey,
      };
      return cachedDefaultTenant;
    }
  } catch (err) {
    console.error('Failed to auto-provision default tenant:', err);
  }

  return null;
}

async function handleProxy(req: NextRequest, { params }: { params: { path: string[] } }) {
  const subpath = params.path ? params.path.join('/') : '';
  const baseUrl = getBackendBaseUrl();
  const search = req.nextUrl.search;
  const targetUrl = `${baseUrl}/api/admin/${subpath}${search}`;

  const headers = new Headers();
  req.headers.forEach((value, key) => {
    if (key.toLowerCase() !== 'host' && key.toLowerCase() !== 'connection') {
      headers.set(key, value);
    }
  });

  // Ensure tenant authentication headers exist for admin-api
  if (!headers.get('x-tenant-id') || !headers.get('x-api-key')) {
    const creds = await resolveTenantCredentials(baseUrl);
    if (creds) {
      if (!headers.get('x-tenant-id')) headers.set('X-Tenant-Id', creds.id);
      if (!headers.get('x-api-key')) headers.set('X-Api-Key', creds.apiKey);
    }
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

    const data = await backendRes.text();
    return new NextResponse(data, {
      status: backendRes.status,
      headers: responseHeaders,
    });
  } catch (err: any) {
    console.error(`Proxy error connecting to ${targetUrl}:`, err);
    return NextResponse.json(
      { message: `Failed to connect to Admin API at ${baseUrl}: ${err.message}` },
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
