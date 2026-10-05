import { describe, it, expect } from 'vitest';
import { theme } from '../theme';

describe('Ignite dark theme tokens', () => {
  it('verifies the exported Ignite dark theme tokens match exact specifications', () => {
    expect(theme.background).toBe('#15171C');
    expect(theme.accent).toBe('#FF6B35');
    expect(theme.surface).toBe('#1C1F26');
    expect(theme.border).toBe('#2A2E37');
    expect(theme.fontFamily).toBe('Inter');
  });

  it('contains full Ignite dark operator color and styling tokens', () => {
    expect(theme.textPrimary).toBe('#F5F6F8');
    expect(theme.textMuted).toBe('#8B93A1');
    expect(theme.accentHover).toBe('#FF8554');
    expect(theme.success).toBe('#1CAE68');
    expect(theme.warning).toBe('#F5A623');
    expect(theme.danger).toBe('#E5484D');
    expect(theme.radiusPanel).toBe('12px');
    expect(theme.radiusControl).toBe('8px');
    expect(theme.fontFamilyMono).toBe('JetBrains Mono');
  });
});
