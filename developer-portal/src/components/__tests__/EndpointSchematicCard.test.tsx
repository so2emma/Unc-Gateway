import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import {
  EndpointSchematicCard,
  SchematicRouteData,
  SchematicRequestData,
  SchematicResponseData,
} from '../EndpointSchematicCard';

describe('EndpointSchematicCard Component', () => {
  const sampleRoute: SchematicRouteData = {
    routeId: 'route-1',
    routeName: 'milestone-route',
    routePath: '/demo-milestone',
    serviceName: 'demo-service',
    serviceUrl: 'http://mock-upstream:9090',
    stripPath: true,
  };

  const sampleRequest: SchematicRequestData = {
    method: 'GET',
    path: '/demo-milestone',
    headers: {
      'X-Api-Key': 'unc_key_demo1234',
    },
  };

  const successResponse: SchematicResponseData = {
    status: 200,
    statusText: 'OK',
    durationMs: 14,
    body: { message: 'hello from upstream' },
  };

  const errorResponse: SchematicResponseData = {
    status: 401,
    statusText: 'Unauthorized',
    durationMs: 4,
    error: 'Invalid API key supplied',
  };

  it('renders the three Request → Route → Response segments in that order with correct matched-route label and response status', () => {
    render(
      <EndpointSchematicCard
        route={sampleRoute}
        request={sampleRequest}
        response={successResponse}
      />
    );

    // 1. Verify Request Segment
    const reqSegment = screen.getByTestId('schematic-segment-request');
    expect(reqSegment).toBeInTheDocument();
    expect(reqSegment.textContent).toContain('1. INBOUND REQUEST');
    expect(reqSegment.textContent).toContain('/demo-milestone');
    expect(reqSegment.textContent).toContain('GET');

    // 2. Verify First Connector
    const connector1 = screen.getByTestId('schematic-connector-1');
    expect(connector1).toBeInTheDocument();

    // 3. Verify Route Segment
    const routeSegment = screen.getByTestId('schematic-segment-route');
    expect(routeSegment).toBeInTheDocument();
    expect(routeSegment.textContent).toContain('2. ROUTE & SERVICE');
    expect(routeSegment.textContent).toContain('/demo-milestone');
    expect(routeSegment.textContent).toContain('http://mock-upstream:9090');

    // Orange pill badge
    const servicePill = screen.getByTestId('service-pill');
    expect(servicePill).toBeInTheDocument();
    expect(servicePill.textContent).toBe('demo-service');

    // 4. Verify Second Connector
    const connector2 = screen.getByTestId('schematic-connector-2');
    expect(connector2).toBeInTheDocument();

    // 5. Verify Response Segment
    const respSegment = screen.getByTestId('schematic-segment-response');
    expect(respSegment).toBeInTheDocument();
    expect(respSegment.textContent).toContain('3. RESPONSE');
    expect(respSegment.textContent).toContain('200 OK');
    expect(respSegment.textContent).toContain('14ms');
    expect(respSegment.textContent).toContain('hello from upstream');

    // Verify DOM order: Request comes before Route, Route comes before Response
    expect(reqSegment.compareDocumentPosition(routeSegment)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING
    );
    expect(routeSegment.compareDocumentPosition(respSegment)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING
    );

    // Green success badge for 2xx status
    const statusBadge = screen.getByTestId('response-status-badge');
    expect(statusBadge).toHaveClass('pill-success');
    expect(statusBadge.style.color).toBe('rgb(28, 174, 104)'); // #1CAE68
  });

  it('renders red error badge when response indicates 401 Unauthorized', () => {
    render(
      <EndpointSchematicCard
        route={sampleRoute}
        request={sampleRequest}
        response={errorResponse}
      />
    );

    const statusBadge = screen.getByTestId('response-status-badge');
    expect(statusBadge).toBeInTheDocument();
    expect(statusBadge).toHaveClass('pill-danger');
    expect(statusBadge.textContent).toContain('401 Unauthorized');
    expect(statusBadge.style.color).toBe('rgb(229, 72, 77)'); // #E5484D
    expect(screen.getByText('Invalid API key supplied')).toBeInTheDocument();
  });

  it('calls onSendRequest trigger when send request button is clicked', () => {
    const mockSend = vi.fn();
    render(
      <EndpointSchematicCard
        route={sampleRoute}
        request={sampleRequest}
        onSendRequest={mockSend}
      />
    );

    const sendBtn = screen.getByRole('button', { name: /send request/i });
    fireEvent.click(sendBtn);

    expect(mockSend).toHaveBeenCalledTimes(1);
  });
});
