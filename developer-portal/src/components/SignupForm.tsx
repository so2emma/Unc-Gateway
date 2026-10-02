'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { createConsumer, Consumer, CreateConsumerInput } from '@/lib/adminApiClient';
import { theme } from '@/styles/theme';

export interface SignupFormProps {
  onSuccess?: (consumer: Consumer) => void;
  onSubmitHandler?: (input: CreateConsumerInput) => Promise<Consumer>;
  defaultTenantId?: string;
}

export const SignupForm: React.FC<SignupFormProps> = ({
  onSuccess,
  onSubmitHandler,
  defaultTenantId,
}) => {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [organization, setOrganization] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{
    name?: string;
    email?: string;
    organization?: string;
  }>({});
  const [apiError, setApiError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [createdConsumer, setCreatedConsumer] = useState<Consumer | null>(null);

  const validate = (): boolean => {
    const errors: { name?: string; email?: string; organization?: string } = {};

    if (!name.trim()) {
      errors.name = 'Full name is required';
    }

    if (!email.trim()) {
      errors.email = 'Work email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      errors.email = 'Please enter a valid email address';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setApiError(null);

    if (!validate()) {
      return;
    }

    setIsSubmitting(true);

    try {
      const payload: CreateConsumerInput = {
        name: name.trim(),
        email: email.trim(),
        organization: organization.trim() || undefined,
        tenantId: defaultTenantId,
      };

      const handler = onSubmitHandler || createConsumer;
      const consumer = await handler(payload);

      setCreatedConsumer(consumer);
      if (onSuccess) {
        onSuccess(consumer);
      }
    } catch (err: any) {
      const message =
        err?.message || 'Failed to create consumer account. Please try again.';
      setApiError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  if (createdConsumer) {
    const displayName = createdConsumer.name || createdConsumer.username || name || 'Developer';
    return (
      <div
        className="card"
        data-testid="signup-success-view"
        style={{
          width: '100%',
          maxWidth: '440px',
          padding: '36px',
          margin: '0 auto',
          textAlign: 'center',
          animation: 'fadeUp 0.35s ease',
        }}
      >
        <div
          data-testid="success-icon"
          style={{
            width: '56px',
            height: '56px',
            borderRadius: '50%',
            backgroundColor: theme.accentTint,
            background: 'var(--success-tint, #E7F8EF)',
            color: theme.success,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: '26px',
            margin: '0 auto 20px',
          }}
        >
          <span className="icon" style={{ width: '28px', height: '28px' }}>
            <svg viewBox="0 0 24 24">
              <polyline points="20 6 9 17 4 12" />
            </svg>
          </span>
        </div>

        <h1
          className="headline"
          style={{
            fontSize: '26px',
            fontWeight: 800,
            letterSpacing: '-0.02em',
            marginBottom: '8px',
            color: theme.ink,
          }}
        >
          You&apos;re all set, {displayName}
        </h1>

        <p
          className="sub"
          style={{
            color: theme.muted,
            fontSize: '14.5px',
            marginBottom: '28px',
            lineHeight: 1.5,
          }}
        >
          Your consumer account was created. Head to <strong>API Keys</strong> to
          issue your first credential.
        </p>

        <Link
          href={`/keys?consumerId=${encodeURIComponent(createdConsumer.id)}`}
          className="btn btn-primary"
          style={{
            width: '100%',
            justifyContent: 'center',
            padding: '13px',
            fontSize: '15px',
            textDecoration: 'none',
            backgroundColor: theme.accent,
          }}
        >
          Go to API Keys →
        </Link>
      </div>
    );
  }

  return (
    <div
      className="card"
      data-testid="signup-card"
      style={{
        width: '100%',
        maxWidth: '440px',
        padding: '36px',
        margin: '0 auto',
        backgroundColor: theme.surface,
        borderRadius: theme.radiusCard,
        boxShadow: 'var(--shadow-soft)',
        border: `1px solid ${theme.border}`,
      }}
    >
      <div className="form-view">
        <span
          className="badge-top"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '6px',
            fontSize: '12px',
            fontWeight: 700,
            color: theme.accentHover,
            backgroundColor: theme.accentTint,
            padding: '5px 12px',
            borderRadius: '999px',
            marginBottom: '20px',
          }}
        >
          <span className="icon" style={{ width: '12px', height: '12px' }}>
            <svg viewBox="0 0 24 24">
              <path d="M12 2l1.5 6.5L20 10l-6.5 1.5L12 18l-1.5-6.5L4 10l6.5-1.5z" />
            </svg>
          </span>{' '}
          Self-serve onboarding
        </span>

        <h1
          className="headline"
          style={{
            fontSize: '26px',
            fontWeight: 800,
            letterSpacing: '-0.02em',
            marginBottom: '8px',
            color: theme.ink,
            fontFamily: theme.fontFamily,
          }}
        >
          Create your developer account
        </h1>

        <p
          className="sub"
          style={{
            color: theme.muted,
            fontSize: '14.5px',
            marginBottom: '28px',
            lineHeight: 1.5,
          }}
        >
          Spin up API credentials in seconds and start routing requests through
          Unc Gateway — no approval queue, no sales call.
        </p>

        {apiError && (
          <div
            role="alert"
            data-testid="signup-error-alert"
            style={{
              marginBottom: '20px',
              padding: '12px 16px',
              borderRadius: theme.radiusControl,
              backgroundColor: 'var(--danger-tint, #FDECEC)',
              border: `1px solid ${theme.danger}`,
              color: theme.danger,
              fontSize: '13.5px',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
            }}
          >
            <span className="icon" style={{ width: '16px', height: '16px' }}>
              <svg viewBox="0 0 24 24">
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
            </span>
            <span>{apiError}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          <div className="field">
            <label className="label" htmlFor="fullNameInput">
              Full name
            </label>
            <input
              id="fullNameInput"
              name="name"
              type="text"
              placeholder="Ada Lovelace"
              value={name}
              onChange={(e) => {
                setName(e.target.value);
                if (fieldErrors.name) {
                  setFieldErrors((prev) => ({ ...prev, name: undefined }));
                }
              }}
              className={fieldErrors.name ? 'input-error' : ''}
              disabled={isSubmitting}
            />
            {fieldErrors.name && (
              <span
                data-testid="name-error"
                style={{
                  display: 'block',
                  color: theme.danger,
                  fontSize: '12px',
                  marginTop: '5px',
                  fontWeight: 500,
                }}
              >
                {fieldErrors.name}
              </span>
            )}
          </div>

          <div className="field">
            <label className="label" htmlFor="emailInput">
              Work email
            </label>
            <input
              id="emailInput"
              name="email"
              type="email"
              placeholder="ada@acme.dev"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value);
                if (fieldErrors.email) {
                  setFieldErrors((prev) => ({ ...prev, email: undefined }));
                }
              }}
              className={fieldErrors.email ? 'input-error' : ''}
              disabled={isSubmitting}
            />
            {fieldErrors.email && (
              <span
                data-testid="email-error"
                style={{
                  display: 'block',
                  color: theme.danger,
                  fontSize: '12px',
                  marginTop: '5px',
                  fontWeight: 500,
                }}
              >
                {fieldErrors.email}
              </span>
            )}
          </div>

          <div className="field">
            <label className="label" htmlFor="orgInput">
              Organization
            </label>
            <input
              id="orgInput"
              name="organization"
              type="text"
              placeholder="Acme Corp"
              value={organization}
              onChange={(e) => setOrganization(e.target.value)}
              disabled={isSubmitting}
            />
          </div>

          <button
            type="submit"
            className="btn btn-primary submit-btn"
            disabled={isSubmitting}
            style={{
              width: '100%',
              justifyContent: 'center',
              padding: '13px',
              fontSize: '15px',
              marginTop: '8px',
              backgroundColor: theme.accent,
              color: '#FFFFFF',
              fontFamily: theme.fontFamily,
            }}
          >
            {isSubmitting ? 'Creating account...' : 'Create account →'}
          </button>
        </form>

        <p
          className="fine-print"
          style={{
            textAlign: 'center',
            fontSize: '12.5px',
            color: theme.muted,
            marginTop: '22px',
          }}
        >
          By continuing, you agree to Unc&apos;s Terms &amp; API Usage Policy.
        </p>
      </div>
    </div>
  );
};

export default SignupForm;
