'use client';

import React, { Suspense, useEffect, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { ApiKeyManager } from '@/components/ApiKeyManager';
import { adminApiClient, Consumer } from '@/lib/adminApiClient';
import { theme } from '@/styles/theme';

function KeysPageContent() {
  const searchParams = useSearchParams();
  const queryConsumerId = searchParams.get('consumerId');

  const [consumerId, setConsumerId] = useState<string>(queryConsumerId || '');
  const [consumer, setConsumer] = useState<Consumer | null>(null);
  const [allConsumers, setAllConsumers] = useState<Consumer[]>([]);
  const [loadingConsumer, setLoadingConsumer] = useState<boolean>(true);

  useEffect(() => {
    // If consumerId is in query param, store in localStorage
    if (queryConsumerId) {
      setConsumerId(queryConsumerId);
      if (typeof window !== 'undefined') {
        window.localStorage.setItem('unc_consumer_id', queryConsumerId);
      }
    } else if (typeof window !== 'undefined') {
      const stored = window.localStorage.getItem('unc_consumer_id');
      if (stored) {
        setConsumerId(stored);
      }
    }
  }, [queryConsumerId]);

  useEffect(() => {
    async function loadConsumerDetails() {
      setLoadingConsumer(true);
      try {
        const consumers = await adminApiClient.listConsumers();
        setAllConsumers(consumers);

        let activeId = consumerId;
        if (!activeId && consumers.length > 0) {
          activeId = consumers[0].id;
          setConsumerId(activeId);
        }

        if (activeId) {
          const found = consumers.find((c) => c.id === activeId);
          if (found) {
            setConsumer(found);
          } else {
            try {
              const fetched = await adminApiClient.getConsumer(activeId);
              setConsumer(fetched);
            } catch {
              // Ignore not found
            }
          }
        }
      } catch (err) {
        console.error('Failed to load consumers:', err);
      } finally {
        setLoadingConsumer(false);
      }
    }

    loadConsumerDetails();
  }, [consumerId]);

  const handleSelectConsumer = (id: string) => {
    setConsumerId(id);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem('unc_consumer_id', id);
    }
  };

  return (
    <div style={{ flex: 1, padding: '40px 0 60px' }}>
      <div className="container" style={{ maxWidth: '960px' }}>
        {/* Breadcrumb / Top Bar */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '28px',
          }}
        >
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
              <span
                style={{
                  fontSize: '12px',
                  fontWeight: 600,
                  textTransform: 'uppercase',
                  letterSpacing: '0.5px',
                  color: theme.accent,
                }}
              >
                Access Control
              </span>
              <span style={{ color: theme.border }}>/</span>
              <span style={{ fontSize: '12px', color: theme.muted }}>API Credentials</span>
            </div>
            <h1
              style={{
                fontSize: '28px',
                fontWeight: 800,
                color: theme.ink,
                letterSpacing: '-0.5px',
              }}
            >
              API Key Management
            </h1>
            <p style={{ color: theme.muted, fontSize: '15px', marginTop: '4px' }}>
              Issue, monitor, and revoke API keys used to authenticate against Unc Gateway.
            </p>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            {consumerId && (
              <Link
                href={`/endpoints?consumerId=${encodeURIComponent(consumerId)}`}
                className="btn btn-primary"
                style={{
                  backgroundColor: theme.accent,
                  fontSize: '13.5px',
                }}
              >
                View Live Endpoints →
              </Link>
            )}
          </div>
        </div>

        {/* Consumer Selector / Identity Pill */}
        <div
          className="card"
          style={{
            padding: '16px 20px',
            marginBottom: '28px',
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: '12px',
            borderRadius: theme.radiusCard,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '50%',
                background: theme.accentTint,
                color: theme.accent,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                fontSize: '14px',
              }}
            >
              {consumer?.name ? consumer.name.charAt(0).toUpperCase() : 'C'}
            </div>
            <div>
              <div style={{ fontSize: '14px', fontWeight: 700, color: theme.ink }}>
                {consumer?.name || consumer?.username || 'Consumer Account'}
                {consumer?.organization && (
                  <span style={{ fontWeight: 400, color: theme.muted, marginLeft: '6px' }}>
                    ({consumer.organization})
                  </span>
                )}
              </div>
              <div className="mono" style={{ fontSize: '12px', color: theme.muted }}>
                ID: {consumerId || 'No consumer selected'}
              </div>
            </div>
          </div>

          {allConsumers.length > 1 && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <label style={{ fontSize: '13px', color: theme.muted }}>Switch Account:</label>
              <select
                value={consumerId}
                onChange={(e) => handleSelectConsumer(e.target.value)}
                style={{
                  padding: '6px 12px',
                  fontSize: '13px',
                  borderRadius: theme.radiusControl,
                  border: `1px solid ${theme.border}`,
                  width: 'auto',
                }}
              >
                {allConsumers.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name || c.username} ({c.id.slice(0, 8)}...)
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>

        {/* ApiKeyManager Component */}
        <ApiKeyManager consumerId={consumerId} />
      </div>
    </div>
  );
}

export default function KeysPage() {
  return (
    <Suspense
      fallback={
        <div style={{ padding: '60px', textAlign: 'center', color: theme.muted }}>
          Loading API Key Management...
        </div>
      }
    >
      <KeysPageContent />
    </Suspense>
  );
}
