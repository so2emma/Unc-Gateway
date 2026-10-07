export const theme = {
  background: '#15171C',
  surface: '#1C1F26',
  border: '#2A2E37',
  textPrimary: '#F5F6F8',
  textMuted: '#8B93A1',
  accent: '#FF6B35',
  accentHover: '#FF8554',
  success: '#1CAE68',
  warning: '#F5A623',
  danger: '#E5484D',
  radiusPanel: '12px',
  radiusControl: '8px',
  fontFamily: 'Inter',
  fontFamilyMono: 'JetBrains Mono',

  // Semantic aliases compatible with Ignite system
  ink: '#F5F6F8',
  muted: '#8B93A1',
  radiusCard: '12px',
  accentTint: 'rgba(255, 107, 53, 0.15)',
  accentGlow: 'rgba(255, 107, 53, 0.25)',
} as const;

export const {
  background,
  surface,
  border,
  textPrimary,
  textMuted,
  accent,
  accentHover,
  success,
  warning,
  danger,
  radiusPanel,
  radiusControl,
  fontFamily,
  fontFamilyMono,
  ink,
  muted,
  radiusCard,
  accentTint,
  accentGlow,
} = theme;

export type IgniteDarkTheme = typeof theme;
export default theme;
