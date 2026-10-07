import React from 'react';
import type { Metadata } from 'next';
import { ServicesRoutesGrid } from '@/components/ServicesRoutesGrid';

export const metadata: Metadata = {
  title: 'Services & Routes — Unc Operator Control Room',
  description: 'Manage gateway upstream services and route proxy rules',
};

export default function ServicesPage() {
  return <ServicesRoutesGrid />;
}
