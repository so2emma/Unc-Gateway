import React from 'react';
import type { Metadata } from 'next';
import { SignupForm } from '@/components/SignupForm';

export const metadata: Metadata = {
  title: 'Sign Up — Unc Gateway Developer Portal',
  description: 'Self-serve developer onboarding to create consumer accounts and access gateway APIs.',
};

export default function SignupPage() {
  return (
    <div
      style={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '60px 24px',
        position: 'relative',
        overflow: 'hidden',
        minHeight: 'calc(100vh - 120px)',
      }}
    >
      {/* Soft orange radial ambient accent glow behind card */}
      <div
        aria-hidden="true"
        style={{
          position: 'absolute',
          top: '-150px',
          right: '-150px',
          width: '500px',
          height: '500px',
          borderRadius: '50%',
          background: 'radial-gradient(circle, var(--accent-tint, #FFF1EA) 0%, transparent 70%)',
          pointerEvents: 'none',
        }}
      />

      <SignupForm />
    </div>
  );
}
