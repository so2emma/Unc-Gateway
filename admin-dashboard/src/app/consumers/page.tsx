import React from 'react';
import type { Metadata } from 'next';
import { ConsumersPluginConfigsGrid } from '@/components/ConsumersPluginConfigsGrid';

export const metadata: Metadata = {
  title: 'Consumers & Plugins — Unc Operator Control Room',
  description: 'Manage API consumers and middleware plugin configuration pipelines',
};

export default function ConsumersPage() {
  return <ConsumersPluginConfigsGrid />;
}
