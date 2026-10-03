import { NextRequest, NextResponse } from 'next/server';

function getGatewayBackendBaseUrl(): string {
  if (process.env.GATEWAY_URL) {
    return process.env.GATEWAY_URL.replace(/\/+$/, '');
  }
  if (process.env.NEXT_PUBLIC_GATEWAY_URL && !process.env.NEXT_PUBLIC_GATEWAY_URL.includes(':3000')) {
    return process.env.NEXT_PUBLIC_GATEWAY_URL.replace(/\/+$/, '');
  }
  if (process.env.HOSTNAME || process.env.NODE_ENV === 'production') {
    return 'http://gateway-core:8080';
  }
  return 'http://localhost:8080';
}

async function handleGatewayProxy(req: NextRequest, { params }: { params: { path: string[] } }) {
  const subpath = params.path ? params.path.join('/') : '';
  const baseUrl = getGatewayBackendBaseUrl();
  const search = req.nextUrl.search;
  const targetUrl = `${baseUrl}/${subpath}${search}`;

  const headers = new Headers();
  req.headers.forEach((value, key) => {
    if (key.toLowerCase() !== 'host' && key.toLowerCase() !== 'connection') {
      headers.set(key, value);
    }
  });

  // Attach default tenant ID if not supplied
  if (!headers.get('x-tenant-id')) {
    const defaultTenantId = process.env.DEFAULT_TENANT_ID || process.env.NEXT_PUBLIC_TENANT_ID;
    if (defaultTenantId) {
      headers.set('X-Tenant-Id', defaultTenantId);
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
    console.error(`Gateway proxy error connecting to ${targetUrl}:`, err);
    return NextResponse.json(
      { message: `Failed to connect to Gateway Core at ${baseUrl}: ${err.message}` },
      { status: 502 }
    );
  }
}

export const GET = handleGatewayProxy;
export const POST = handleGatewayProxy;
export const PUT = handleGatewayProxy;
export const DELETE = handleGatewayProxy;
export const PATCH = handleGatewayProxy;
export const OPTIONS = handleGatewayProxy;
