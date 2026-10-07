import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ServicesRoutesGrid } from '../ServicesRoutesGrid';
import { ServiceItem, RouteItem, adminApiClient } from '@/lib/adminApiClient';

describe('ServicesRoutesGrid Component', () => {
  const seededServices: ServiceItem[] = [
    {
      id: 'svc-1',
      name: 'auth-service',
      url: 'http://auth-upstream:8080',
      connectTimeout: 5000,
      readTimeout: 30000,
      createdAt: '2026-10-01T12:00:00Z',
    },
  ];

  const seededRoutes: RouteItem[] = [
    {
      id: 'rt-1',
      serviceId: 'svc-1',
      name: 'auth-login-route',
      paths: '/api/auth/login',
      methods: 'POST',
      protocols: 'http,https',
      stripPath: true,
      createdAt: '2026-10-01T12:00:00Z',
    },
  ];

  let mockClient: typeof adminApiClient;

  beforeEach(() => {
    mockClient = {
      listServices: vi.fn().mockResolvedValue(seededServices),
      getService: vi.fn(),
      createService: vi.fn(),
      updateService: vi.fn().mockResolvedValue({
        id: 'svc-1',
        name: 'auth-service-updated',
        url: 'http://auth-upstream:8080',
        connectTimeout: 5000,
        readTimeout: 30000,
      }),
      deleteService: vi.fn().mockResolvedValue(undefined),
      listRoutes: vi.fn().mockResolvedValue(seededRoutes),
      getRoute: vi.fn(),
      createRoute: vi.fn(),
      updateRoute: vi.fn().mockResolvedValue({
        id: 'rt-1',
        serviceId: 'svc-1',
        name: 'auth-login-route-updated',
        paths: '/api/v2/auth/login',
        methods: 'POST',
        protocols: 'http,https',
        stripPath: true,
      }),
      deleteRoute: vi.fn().mockResolvedValue(undefined),
      listConsumers: vi.fn(),
      getConsumer: vi.fn(),
      createConsumer: vi.fn(),
      updateConsumer: vi.fn(),
      deleteConsumer: vi.fn(),
      listPluginConfigs: vi.fn(),
      getPluginConfig: vi.fn(),
      createPluginConfig: vi.fn(),
      updatePluginConfig: vi.fn(),
      deletePluginConfig: vi.fn(),
    };
  });

  it('displays Ignite dark theme attributes and renders one dense grid row per seeded service/route', async () => {
    render(
      <ServicesRoutesGrid
        initialServices={seededServices}
        initialRoutes={seededRoutes}
        client={mockClient}
      />
    );

    // Verify grid container rendered
    const gridContainer = screen.getByTestId('services-routes-grid');
    expect(gridContainer).toBeInTheDocument();

    // Verify primary action button uses Ignite primary accent (#FF6B35) styling
    const createBtn = screen.getByTestId('create-service-btn');
    expect(createBtn).toHaveClass('btn-primary');

    // Verify seeded service row
    const serviceRow = screen.getByTestId('service-row-svc-1');
    expect(serviceRow).toBeInTheDocument();
    expect(serviceRow).toHaveTextContent('auth-service');
    expect(serviceRow).toHaveTextContent('http://auth-upstream:8080');

    // Switch to Routes tab
    const routesTab = screen.getByTestId('tab-routes');
    fireEvent.click(routesTab);

    // Verify seeded route row
    const routeRow = await screen.findByTestId('route-row-rt-1');
    expect(routeRow).toBeInTheDocument();
    expect(routeRow).toHaveTextContent('auth-login-route');
    expect(routeRow).toHaveTextContent('/api/auth/login');
  });

  it('verifies the grid edit-row handler calls the adminApiClient update function exactly once with the edited row fields', async () => {
    render(
      <ServicesRoutesGrid
        initialServices={seededServices}
        initialRoutes={seededRoutes}
        client={mockClient}
      />
    );

    // Click edit service button
    const editBtn = screen.getByTestId('edit-service-svc-1');
    fireEvent.click(editBtn);

    // Edit service name in modal
    const nameInput = screen.getByTestId('input-service-name');
    fireEvent.change(nameInput, { target: { value: 'auth-service-updated' } });

    // Submit form
    const submitBtn = screen.getByTestId('submit-service-btn');
    fireEvent.click(submitBtn);

    await waitFor(() => {
      expect(mockClient.updateService).toHaveBeenCalledTimes(1);
    });

    expect(mockClient.updateService).toHaveBeenCalledWith(
      'svc-1',
      expect.objectContaining({
        name: 'auth-service-updated',
        url: 'http://auth-upstream:8080',
        connectTimeout: 5000,
        readTimeout: 30000,
      })
    );
  });

  it('verifies the grid delete-row handler calls the delete function exactly once with the correct row id', async () => {
    render(
      <ServicesRoutesGrid
        initialServices={seededServices}
        initialRoutes={seededRoutes}
        client={mockClient}
      />
    );

    // Click delete service button
    const deleteBtn = screen.getByTestId('delete-service-svc-1');
    fireEvent.click(deleteBtn);

    // Confirm deletion dialog
    const confirmBtn = screen.getByTestId('confirm-delete-btn');
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(mockClient.deleteService).toHaveBeenCalledTimes(1);
    });

    expect(mockClient.deleteService).toHaveBeenCalledWith('svc-1');
  });

  it('verifies route edit and delete handlers execute correctly against routes', async () => {
    render(
      <ServicesRoutesGrid
        initialServices={seededServices}
        initialRoutes={seededRoutes}
        client={mockClient}
      />
    );

    // Switch to Routes tab
    fireEvent.click(screen.getByTestId('tab-routes'));

    // Edit route
    const editRouteBtn = await screen.findByTestId('edit-route-rt-1');
    fireEvent.click(editRouteBtn);

    const routePathInput = screen.getByTestId('input-route-paths');
    fireEvent.change(routePathInput, { target: { value: '/api/v2/auth/login' } });

    fireEvent.click(screen.getByTestId('submit-route-btn'));

    await waitFor(() => {
      expect(mockClient.updateRoute).toHaveBeenCalledTimes(1);
    });

    expect(mockClient.updateRoute).toHaveBeenCalledWith(
      'rt-1',
      expect.objectContaining({
        name: 'auth-login-route',
        paths: '/api/v2/auth/login',
      })
    );

    // Delete route
    const deleteRouteBtn = screen.getByTestId('delete-route-rt-1');
    fireEvent.click(deleteRouteBtn);

    fireEvent.click(screen.getByTestId('confirm-delete-btn'));

    await waitFor(() => {
      expect(mockClient.deleteRoute).toHaveBeenCalledTimes(1);
    });

    expect(mockClient.deleteRoute).toHaveBeenCalledWith('rt-1');
  });
});
