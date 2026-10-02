export const theme = {
  background: '#F7F8FA',
  surface: '#FFFFFF',
  ink: '#0B0D12',
  muted: '#5B6472',
  border: '#E7E9EC',
  accent: '#FF6B35',
  accentHover: '#E85A2A',
  accentTint: '#FFF1EA',
  success: '#1CAE68',
  danger: '#E5484D',
  radiusCard: '12px',
  radiusControl: '8px',
  fontFamily: 'Inter',
  fontFamilyMono: 'JetBrains Mono',
} as const;

export const {
  background,
  surface,
  ink,
  muted,
  border,
  accent,
  accentHover,
  accentTint,
  success,
  danger,
  radiusCard,
  radiusControl,
  fontFamily,
  fontFamilyMono,
} = theme;

export type IgniteTheme = typeof theme;
export default theme;
