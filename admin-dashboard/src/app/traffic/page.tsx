import React from 'react';
import type { Metadata } from 'next';
import { TrafficDashboardView } from '@/components/TrafficDashboardView';

export const metadata: Metadata = {
  title: 'Traffic Pulse — Unc Operator Control Room',
  description: 'Real-time request volume and tail-latency health across the gateway.',
};

export default function TrafficPage() {
  return <TrafficDashboardView />;
}
