import { describe, it, expect } from 'vitest';
import { theme } from '../theme';

describe('Ignite theme tokens', () => {
  it('verifies the exported Ignite theme tokens match exact specifications', () => {
    expect(theme.background).toBe('#F7F8FA');
    expect(theme.surface).toBe('#FFFFFF');
    expect(theme.ink).toBe('#0B0D12');
    expect(theme.accent).toBe('#FF6B35');
    expect(theme.fontFamily).toBe('Inter');
  });

  it('contains full Ignite color and styling tokens', () => {
    expect(theme.muted).toBe('#5B6472');
    expect(theme.border).toBe('#E7E9EC');
    expect(theme.accentHover).toBe('#E85A2A');
    expect(theme.accentTint).toBe('#FFF1EA');
    expect(theme.success).toBe('#1CAE68');
    expect(theme.danger).toBe('#E5484D');
    expect(theme.radiusCard).toBe('12px');
    expect(theme.radiusControl).toBe('8px');
    expect(theme.fontFamilyMono).toBe('JetBrains Mono');
  });
});
