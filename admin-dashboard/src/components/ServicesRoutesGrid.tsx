'use client';

import React, { useState, useEffect, useCallback } from 'react';
import {
  adminApiClient as defaultClient,
  ServiceItem,
  RouteItem,
  CreateServiceInput,
  CreateRouteInput,
} from '@/lib/adminApiClient';
import {
  PlusIcon,
  EditIcon,
  TrashIcon,
  RefreshIcon,
  SearchIcon,
  CloseIcon,
  CheckIcon,
  ServerIcon,
  GitRouteIcon,
  AlertTriangleIcon,
} from './Icons';

export interface ServicesRoutesGridProps {
  initialServices?: ServiceItem[];
  initialRoutes?: RouteItem[];
  client?: typeof defaultClient;
}

export const ServicesRoutesGrid: React.FC<ServicesRoutesGridProps> = ({
  initialServices,
  initialRoutes,
  client = defaultClient,
}) => {
  const [activeTab, setActiveTab] = useState<'services' | 'routes'>('services');
  const [services, setServices] = useState<ServiceItem[]>(initialServices || []);
  const [routes, setRoutes] = useState<RouteItem[]>(initialRoutes || []);
  const [loading, setLoading] = useState<boolean>(!initialServices && !initialRoutes);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Modals state
  const [isServiceModalOpen, setIsServiceModalOpen] = useState<boolean>(false);
  const [editingService, setEditingService] = useState<ServiceItem | null>(null);
  const [serviceFormData, setServiceFormData] = useState<CreateServiceInput>({
    name: '',
    url: '',
    connectTimeout: 6000,
    readTimeout: 60000,
  });

  const [isRouteModalOpen, setIsRouteModalOpen] = useState<boolean>(false);
  const [editingRoute, setEditingRoute] = useState<RouteItem | null>(null);
  const [routeFormData, setRouteFormData] = useState<CreateRouteInput>({
    serviceId: '',
    name: '',
    paths: '',
    methods: 'GET,POST',
    protocols: 'http,https',
    stripPath: true,
  });

  const [deleteConfirm, setDeleteConfirm] = useState<{
    type: 'service' | 'route';
    id: string;
    name: string;
  } | null>(null);

  const loadData = useCallback(async () => {
    try {
      setLoading(true);
      setErrorMessage(null);
      const [fetchedServices, fetchedRoutes] = await Promise.all([
        client.listServices(),
        client.listRoutes(),
      ]);
      setServices(fetchedServices || []);
      setRoutes(fetchedRoutes || []);
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to load services and routes');
    } finally {
      setLoading(false);
    }
  }, [client]);

  useEffect(() => {
    if (!initialServices && !initialRoutes) {
      loadData();
    }
  }, [initialServices, initialRoutes, loadData]);

  const showNotification = (msg: string) => {
    setSuccessMessage(msg);
    setTimeout(() => setSuccessMessage(null), 3500);
  };

  // ==================== Service CRUD Handlers ====================

  const openCreateService = () => {
    setEditingService(null);
    setServiceFormData({
      name: '',
      url: '',
      connectTimeout: 6000,
      readTimeout: 60000,
    });
    setIsServiceModalOpen(true);
  };

  const openEditService = (svc: ServiceItem) => {
    setEditingService(svc);
    setServiceFormData({
      name: svc.name,
      url: svc.url || svc.upstreamUrl || '',
      connectTimeout: svc.connectTimeout ?? 6000,
      readTimeout: svc.readTimeout ?? 60000,
    });
    setIsServiceModalOpen(true);
  };

  const handleSaveService = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!serviceFormData.name.trim()) return;

    try {
      if (editingService) {
        const updated = await client.updateService(editingService.id, serviceFormData);
        setServices((prev) =>
          prev.map((s) => (s.id === editingService.id ? { ...s, ...updated } : s))
        );
        showNotification(`Service "${serviceFormData.name}" updated`);
      } else {
        const created = await client.createService(serviceFormData);
        setServices((prev) => [...prev, created]);
        showNotification(`Service "${serviceFormData.name}" created`);
      }
      setIsServiceModalOpen(false);
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to save service');
    }
  };

  const handleDeleteService = async (id: string) => {
    try {
      await client.deleteService(id);
      setServices((prev) => prev.filter((s) => s.id !== id));
      setDeleteConfirm(null);
      showNotification('Service deleted successfully');
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to delete service');
    }
  };

  // ==================== Route CRUD Handlers ====================

  const openCreateRoute = () => {
    setEditingRoute(null);
    setRouteFormData({
      serviceId: services[0]?.id || '',
      name: '',
      paths: '',
      methods: 'GET,POST',
      protocols: 'http,https',
      stripPath: true,
    });
    setIsRouteModalOpen(true);
  };

  const openEditRoute = (r: RouteItem) => {
    setEditingRoute(r);
    setRouteFormData({
      serviceId: r.serviceId || '',
      name: r.name,
      paths: r.paths || r.path || '',
      methods: r.methods || 'GET,POST',
      protocols: r.protocols || 'http,https',
      stripPath: r.stripPath ?? true,
    });
    setIsRouteModalOpen(true);
  };

  const handleSaveRoute = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!routeFormData.name.trim()) return;

    try {
      if (editingRoute) {
        const updated = await client.updateRoute(editingRoute.id, routeFormData);
        setRoutes((prev) =>
          prev.map((r) => (r.id === editingRoute.id ? { ...r, ...updated } : r))
        );
        showNotification(`Route "${routeFormData.name}" updated`);
      } else {
        const created = await client.createRoute(routeFormData);
        setRoutes((prev) => [...prev, created]);
        showNotification(`Route "${routeFormData.name}" created`);
      }
      setIsRouteModalOpen(false);
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to save route');
    }
  };

  const handleDeleteRoute = async (id: string) => {
    try {
      await client.deleteRoute(id);
      setRoutes((prev) => prev.filter((r) => r.id !== id));
      setDeleteConfirm(null);
      showNotification('Route deleted successfully');
    } catch (err: any) {
      setErrorMessage(err.message || 'Failed to delete route');
    }
  };

  // Filtered items
  const filteredServices = services.filter(
    (s) =>
      s.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (s.url && s.url.toLowerCase().includes(searchQuery.toLowerCase())) ||
      s.id.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const filteredRoutes = routes.filter(
    (r) =>
      r.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      ((r.paths || r.path) && (r.paths || r.path)!.toLowerCase().includes(searchQuery.toLowerCase())) ||
      r.id.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div data-testid="services-routes-grid" style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
      {/* Header and Actions Bar */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '22px', fontWeight: 700, color: '#F5F6F8', marginBottom: '4px' }}>
            Services & Routes
          </h1>
          <p style={{ fontSize: '13px', color: 'var(--text-muted)' }}>
            Dense operator management for upstream backend services and reverse proxy routes
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <button
            onClick={loadData}
            className="btn btn-secondary btn-sm"
            title="Refresh from Admin API"
            aria-label="Refresh grid"
          >
            <RefreshIcon size={14} />
            <span>Refresh</span>
          </button>

          {activeTab === 'services' ? (
            <button
              data-testid="create-service-btn"
              onClick={openCreateService}
              className="btn btn-primary"
            >
              <PlusIcon size={15} />
              <span>Create Service</span>
            </button>
          ) : (
            <button
              data-testid="create-route-btn"
              onClick={openCreateRoute}
              className="btn btn-primary"
            >
              <PlusIcon size={15} />
              <span>Create Route</span>
            </button>
          )}
        </div>
      </div>

      {/* Notifications Banner */}
      {errorMessage && (
        <div
          role="alert"
          style={{
            padding: '12px 16px',
            backgroundColor: 'var(--danger-tint)',
            border: '1px solid rgba(229, 72, 77, 0.3)',
            borderRadius: 'var(--radius-control)',
            color: 'var(--danger)',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <AlertTriangleIcon size={16} />
            <span>{errorMessage}</span>
          </div>
          <button
            onClick={() => setErrorMessage(null)}
            className="btn-icon btn-icon-danger"
            aria-label="Dismiss error"
          >
            <CloseIcon size={14} />
          </button>
        </div>
      )}

      {successMessage && (
        <div
          role="status"
          style={{
            padding: '12px 16px',
            backgroundColor: 'var(--success-tint)',
            border: '1px solid rgba(28, 174, 104, 0.3)',
            borderRadius: 'var(--radius-control)',
            color: 'var(--success)',
            fontSize: '13px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <CheckIcon size={16} />
          <span>{successMessage}</span>
        </div>
      )}

      {/* Segmented Controls & Search Bar */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '16px',
          flexWrap: 'wrap',
        }}
      >
        {/* Tab Switcher */}
        <div
          style={{
            display: 'inline-flex',
            backgroundColor: 'var(--surface-dark)',
            border: '1px solid var(--border-dark)',
            borderRadius: 'var(--radius-control)',
            padding: '3px',
            gap: '2px',
          }}
        >
          <button
            data-testid="tab-services"
            onClick={() => setActiveTab('services')}
            style={{
              padding: '6px 14px',
              fontSize: '13px',
              fontWeight: 600,
              borderRadius: '6px',
              border: 'none',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              transition: 'all 0.15s ease',
              backgroundColor: activeTab === 'services' ? 'var(--surface-hover)' : 'transparent',
              color: activeTab === 'services' ? '#FFFFFF' : 'var(--text-muted)',
              borderBottom: activeTab === 'services' ? '2px solid var(--accent)' : '2px solid transparent',
            }}
          >
            <ServerIcon size={14} style={{ color: activeTab === 'services' ? 'var(--accent)' : 'inherit' }} />
            <span>Services</span>
            <span
              className="badge badge-muted mono"
              style={{ padding: '1px 6px', fontSize: '10.5px' }}
            >
              {services.length}
            </span>
          </button>

          <button
            data-testid="tab-routes"
            onClick={() => setActiveTab('routes')}
            style={{
              padding: '6px 14px',
              fontSize: '13px',
              fontWeight: 600,
              borderRadius: '6px',
              border: 'none',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              transition: 'all 0.15s ease',
              backgroundColor: activeTab === 'routes' ? 'var(--surface-hover)' : 'transparent',
              color: activeTab === 'routes' ? '#FFFFFF' : 'var(--text-muted)',
              borderBottom: activeTab === 'routes' ? '2px solid var(--accent)' : '2px solid transparent',
            }}
          >
            <GitRouteIcon size={14} style={{ color: activeTab === 'routes' ? 'var(--accent)' : 'inherit' }} />
            <span>Routes</span>
            <span
              className="badge badge-muted mono"
              style={{ padding: '1px 6px', fontSize: '10.5px' }}
            >
              {routes.length}
            </span>
          </button>
        </div>

        {/* Filter / Search input */}
        <div style={{ position: 'relative', width: '280px' }}>
          <SearchIcon
            size={14}
            style={{
              position: 'absolute',
              left: '10px',
              top: '50%',
              transform: 'translateY(-50%)',
              color: 'var(--text-muted)',
            }}
          />
          <input
            type="text"
            className="input"
            placeholder={activeTab === 'services' ? 'Filter services...' : 'Filter routes...'}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{ paddingLeft: '32px', fontSize: '12.5px' }}
          />
        </div>
      </div>

      {/* Main Dense Table Grid */}
      <div className="dense-table-container">
        {activeTab === 'services' ? (
          <table className="dense-table" data-testid="services-table">
            <thead>
              <tr>
                <th style={{ width: '130px' }}>Service ID</th>
                <th style={{ width: '180px' }}>Name</th>
                <th>Upstream URL</th>
                <th style={{ width: '130px' }}>Timeouts (C/R)</th>
                <th style={{ width: '140px' }}>Created</th>
                <th style={{ width: '90px', textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredServices.length === 0 ? (
                <tr>
                  <td colSpan={6} className="table-empty-cell">
                    <div className="table-empty-content">
                      {loading ? (
                        <>
                          <div className="table-empty-title">Fetching Services</div>
                          <div className="table-empty-desc">Loading upstream configurations from Admin API...</div>
                        </>
                      ) : searchQuery.trim() ? (
                        <>
                          <div className="table-empty-title">No matching services</div>
                          <div className="table-empty-desc">
                            No services matched &ldquo;{searchQuery}&rdquo;. Check your query or clear the filter.
                          </div>
                          <button
                            type="button"
                            onClick={() => setSearchQuery('')}
                            className="btn btn-secondary btn-sm"
                          >
                            Clear Filter
                          </button>
                        </>
                      ) : (
                        <>
                          <div className="table-empty-title">No upstream services registered</div>
                          <div className="table-empty-desc">
                            Register a backend upstream service to enable reverse-proxy routing at the gateway.
                          </div>
                          <button
                            type="button"
                            onClick={openCreateService}
                            className="btn btn-primary btn-sm"
                          >
                            <PlusIcon size={14} />
                            <span>Register Service</span>
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ) : (
                filteredServices.map((service) => (
                  <tr key={service.id} data-testid={`service-row-${service.id}`}>
                    <td className="mono" style={{ fontSize: '11px', color: 'var(--text-muted)' }} title={service.id}>
                      {service.id.slice(0, 8)}...
                    </td>
                    <td style={{ fontWeight: 600, color: '#F5F6F8' }}>{service.name}</td>
                    <td className="mono" style={{ color: 'var(--accent)' }}>
                      {service.url || service.upstreamUrl || '—'}
                    </td>
                    <td className="mono" style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>
                      {service.connectTimeout ?? 6000}ms / {service.readTimeout ?? 60000}ms
                    </td>
                    <td style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>
                      {service.createdAt ? new Date(service.createdAt).toLocaleDateString() : '—'}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                        <button
                          data-testid={`edit-service-${service.id}`}
                          onClick={() => openEditService(service)}
                          className="btn-icon"
                          title="Edit Service"
                          aria-label={`Edit service ${service.name}`}
                        >
                          <EditIcon size={14} />
                        </button>
                        <button
                          data-testid={`delete-service-${service.id}`}
                          onClick={() => setDeleteConfirm({ type: 'service', id: service.id, name: service.name })}
                          className="btn-icon btn-icon-danger"
                          title="Delete Service"
                          aria-label={`Delete service ${service.name}`}
                        >
                          <TrashIcon size={14} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        ) : (
          <table className="dense-table" data-testid="routes-table">
            <thead>
              <tr>
                <th style={{ width: '130px' }}>Route ID</th>
                <th style={{ width: '160px' }}>Name</th>
                <th>Path / Pattern</th>
                <th style={{ width: '140px' }}>Target Service</th>
                <th style={{ width: '100px' }}>Strip Path</th>
                <th style={{ width: '130px' }}>Created</th>
                <th style={{ width: '90px', textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredRoutes.length === 0 ? (
                <tr>
                  <td colSpan={7} className="table-empty-cell">
                    <div className="table-empty-content">
                      {loading ? (
                        <>
                          <div className="table-empty-title">Fetching Routes</div>
                          <div className="table-empty-desc">Loading routing rules from Admin API...</div>
                        </>
                      ) : searchQuery.trim() ? (
                        <>
                          <div className="table-empty-title">No matching routes</div>
                          <div className="table-empty-desc">
                            No routes matched &ldquo;{searchQuery}&rdquo;. Check your query or clear the filter.
                          </div>
                          <button
                            type="button"
                            onClick={() => setSearchQuery('')}
                            className="btn btn-secondary btn-sm"
                          >
                            Clear Filter
                          </button>
                        </>
                      ) : (
                        <>
                          <div className="table-empty-title">No routing rules configured</div>
                          <div className="table-empty-desc">
                            Define a route path to map incoming gateway traffic to your upstream services.
                          </div>
                          <button
                            type="button"
                            onClick={openCreateRoute}
                            className="btn btn-primary btn-sm"
                          >
                            <PlusIcon size={14} />
                            <span>Create Route</span>
                          </button>
                        </>
                      )}
                    </div>
                  </td>
                </tr>
              ) : (
                filteredRoutes.map((route) => {
                  const targetSvc = services.find((s) => s.id === route.serviceId);
                  return (
                    <tr key={route.id} data-testid={`route-row-${route.id}`}>
                      <td className="mono" style={{ fontSize: '11px', color: 'var(--text-muted)' }} title={route.id}>
                        {route.id.slice(0, 8)}...
                      </td>
                      <td style={{ fontWeight: 600, color: '#F5F6F8' }}>{route.name}</td>
                      <td className="mono" style={{ color: 'var(--accent)' }}>
                        {route.paths || route.path || '—'}
                      </td>
                      <td style={{ fontSize: '12px' }}>
                        {targetSvc ? (
                          <span style={{ color: '#F5F6F8' }}>{targetSvc.name}</span>
                        ) : route.serviceId ? (
                          <span className="mono" style={{ color: 'var(--text-muted)', fontSize: '11px' }}>
                            {route.serviceId.slice(0, 8)}...
                          </span>
                        ) : (
                          <span style={{ color: 'var(--text-muted)' }}>—</span>
                        )}
                      </td>
                      <td>
                        <span
                          className={`badge ${route.stripPath ? 'badge-success' : 'badge-muted'}`}
                          style={{ fontSize: '10px' }}
                        >
                          {route.stripPath ? 'Enabled' : 'Disabled'}
                        </span>
                      </td>
                      <td style={{ fontSize: '11.5px', color: 'var(--text-muted)' }}>
                        {route.createdAt ? new Date(route.createdAt).toLocaleDateString() : '—'}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                          <button
                            data-testid={`edit-route-${route.id}`}
                            onClick={() => openEditRoute(route)}
                            className="btn-icon"
                            title="Edit Route"
                            aria-label={`Edit route ${route.name}`}
                          >
                            <EditIcon size={14} />
                          </button>
                          <button
                            data-testid={`delete-route-${route.id}`}
                            onClick={() => setDeleteConfirm({ type: 'route', id: route.id, name: route.name })}
                            className="btn-icon btn-icon-danger"
                            title="Delete Route"
                            aria-label={`Delete route ${route.name}`}
                          >
                            <TrashIcon size={14} />
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        )}
      </div>

      {/* ==================== Service Create/Edit Modal ==================== */}
      {isServiceModalOpen && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal-content">
            <div className="modal-header">
              <h2 style={{ fontSize: '15px', fontWeight: 600, color: '#F5F6F8' }}>
                {editingService ? `Edit Service: ${editingService.name}` : 'Create Backend Service'}
              </h2>
              <button
                onClick={() => setIsServiceModalOpen(false)}
                className="btn-icon"
                aria-label="Close dialog"
              >
                <CloseIcon size={16} />
              </button>
            </div>
            <form onSubmit={handleSaveService}>
              <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div>
                  <label className="label" htmlFor="svc-name">Service Name</label>
                  <input
                    id="svc-name"
                    data-testid="input-service-name"
                    type="text"
                    className="input"
                    placeholder="e.g. orders-service"
                    value={serviceFormData.name}
                    onChange={(e) => setServiceFormData({ ...serviceFormData, name: e.target.value })}
                    required
                  />
                </div>

                <div>
                  <label className="label" htmlFor="svc-url">Upstream URL</label>
                  <input
                    id="svc-url"
                    data-testid="input-service-url"
                    type="text"
                    className="input mono"
                    placeholder="http://upstream-host:port"
                    value={serviceFormData.url}
                    onChange={(e) => setServiceFormData({ ...serviceFormData, url: e.target.value })}
                    required
                  />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                  <div>
                    <label className="label" htmlFor="svc-connect-timeout">Connect Timeout (ms)</label>
                    <input
                      id="svc-connect-timeout"
                      data-testid="input-service-connect-timeout"
                      type="number"
                      className="input mono"
                      value={serviceFormData.connectTimeout ?? 6000}
                      onChange={(e) =>
                        setServiceFormData({ ...serviceFormData, connectTimeout: parseInt(e.target.value) || 0 })
                      }
                    />
                  </div>
                  <div>
                    <label className="label" htmlFor="svc-read-timeout">Read Timeout (ms)</label>
                    <input
                      id="svc-read-timeout"
                      data-testid="input-service-read-timeout"
                      type="number"
                      className="input mono"
                      value={serviceFormData.readTimeout ?? 60000}
                      onChange={(e) =>
                        setServiceFormData({ ...serviceFormData, readTimeout: parseInt(e.target.value) || 0 })
                      }
                    />
                  </div>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  onClick={() => setIsServiceModalOpen(false)}
                  className="btn btn-secondary"
                >
                  Cancel
                </button>
                <button
                  data-testid="submit-service-btn"
                  type="submit"
                  className="btn btn-primary"
                >
                  {editingService ? 'Save Changes' : 'Create Service'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================== Route Create/Edit Modal ==================== */}
      {isRouteModalOpen && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal-content">
            <div className="modal-header">
              <h2 style={{ fontSize: '15px', fontWeight: 600, color: '#F5F6F8' }}>
                {editingRoute ? `Edit Route: ${editingRoute.name}` : 'Create Routing Rule'}
              </h2>
              <button
                onClick={() => setIsRouteModalOpen(false)}
                className="btn-icon"
                aria-label="Close dialog"
              >
                <CloseIcon size={16} />
              </button>
            </div>
            <form onSubmit={handleSaveRoute}>
              <div className="modal-body" style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div>
                  <label className="label" htmlFor="route-name">Route Name</label>
                  <input
                    id="route-name"
                    data-testid="input-route-name"
                    type="text"
                    className="input"
                    placeholder="e.g. orders-v1"
                    value={routeFormData.name}
                    onChange={(e) => setRouteFormData({ ...routeFormData, name: e.target.value })}
                    required
                  />
                </div>

                <div>
                  <label className="label" htmlFor="route-svc">Target Service</label>
                  <select
                    id="route-svc"
                    data-testid="select-route-service"
                    className="select"
                    value={routeFormData.serviceId}
                    onChange={(e) => setRouteFormData({ ...routeFormData, serviceId: e.target.value })}
                    required
                  >
                    <option value="" disabled>Select an upstream service</option>
                    {services.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name} ({s.url || s.upstreamUrl})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="label" htmlFor="route-paths">Matching Paths</label>
                  <input
                    id="route-paths"
                    data-testid="input-route-paths"
                    type="text"
                    className="input mono"
                    placeholder="e.g. /api/orders, /api/v1/orders"
                    value={routeFormData.paths}
                    onChange={(e) => setRouteFormData({ ...routeFormData, paths: e.target.value })}
                    required
                  />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                  <div>
                    <label className="label" htmlFor="route-methods">HTTP Methods</label>
                    <input
                      id="route-methods"
                      data-testid="input-route-methods"
                      type="text"
                      className="input mono"
                      placeholder="e.g. GET,POST"
                      value={routeFormData.methods || ''}
                      onChange={(e) => setRouteFormData({ ...routeFormData, methods: e.target.value })}
                    />
                  </div>
                  <div>
                    <label className="label" htmlFor="route-protocols">Protocols</label>
                    <input
                      id="route-protocols"
                      data-testid="input-route-protocols"
                      type="text"
                      className="input mono"
                      placeholder="e.g. http,https"
                      value={routeFormData.protocols || ''}
                      onChange={(e) => setRouteFormData({ ...routeFormData, protocols: e.target.value })}
                    />
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginTop: '4px' }}>
                  <input
                    id="route-strip-path"
                    data-testid="checkbox-route-strip-path"
                    type="checkbox"
                    checked={routeFormData.stripPath ?? true}
                    onChange={(e) => setRouteFormData({ ...routeFormData, stripPath: e.target.checked })}
                    style={{ accentColor: 'var(--accent)', width: '16px', height: '16px' }}
                  />
                  <label htmlFor="route-strip-path" style={{ fontSize: '13px', color: 'var(--text-primary)', cursor: 'pointer' }}>
                    Strip matched prefix before forwarding upstream
                  </label>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  onClick={() => setIsRouteModalOpen(false)}
                  className="btn btn-secondary"
                >
                  Cancel
                </button>
                <button
                  data-testid="submit-route-btn"
                  type="submit"
                  className="btn btn-primary"
                >
                  {editingRoute ? 'Save Changes' : 'Create Route'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ==================== Delete Confirmation Modal ==================== */}
      {deleteConfirm && (
        <div className="modal-backdrop" role="dialog" aria-modal="true">
          <div className="modal-content" style={{ maxWidth: '440px' }}>
            <div className="modal-header">
              <h2 style={{ fontSize: '15px', fontWeight: 600, color: 'var(--danger)' }}>
                Confirm Deletion
              </h2>
              <button onClick={() => setDeleteConfirm(null)} className="btn-icon" aria-label="Cancel deletion">
                <CloseIcon size={16} />
              </button>
            </div>
            <div className="modal-body">
              <p style={{ fontSize: '13.5px', color: 'var(--text-primary)', lineHeight: 1.5 }}>
                Are you sure you want to delete {deleteConfirm.type}{' '}
                <strong style={{ color: '#FFFFFF' }}>&ldquo;{deleteConfirm.name}&rdquo;</strong>?
              </p>
              <p style={{ fontSize: '12.5px', color: 'var(--text-muted)', marginTop: '8px' }}>
                This action cannot be undone and will immediately affect routing at the gateway.
              </p>
            </div>
            <div className="modal-footer">
              <button
                type="button"
                onClick={() => setDeleteConfirm(null)}
                className="btn btn-secondary"
              >
                Cancel
              </button>
              <button
                data-testid="confirm-delete-btn"
                type="button"
                onClick={() => {
                  if (deleteConfirm.type === 'service') {
                    handleDeleteService(deleteConfirm.id);
                  } else {
                    handleDeleteRoute(deleteConfirm.id);
                  }
                }}
                className="btn btn-danger"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ServicesRoutesGrid;
