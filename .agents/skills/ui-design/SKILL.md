---
name: ui-design
description: >-
  Enforces professional, clutter-free UI/UX standards across web applications.
  Use whenever creating or refactoring frontend interfaces, components, pages,
  icons, and forms. Strictly prohibits raw emojis in production UI in favor of
  semantic, accessible SVG icons and minimal, focused layouts.
---

# UI Design Guidelines

Standards for crafting production-grade user interfaces that feel cohesive, secure, and distraction-free.

## 1. Zero Emojis in Production UI
- **Strict Prohibition**: Never use raw Unicode emoji characters (`⚡`, `📋`, `🔒`, `💡`, `⚠`, `✓`, `❌`, `🚀`, etc.) in production UI components, buttons, badges, toasts, or headers.
- **Proper Icons**: Use lightweight, semantic inline SVG icons (e.g. Feather or Lucide icon conventions):
  - Always specify `width`, `height`, `viewBox="0 0 24 24"`, `fill="none"`, `stroke="currentColor"`, `strokeWidth="2"`, `strokeLinecap="round"`, and `strokeLinejoin="round"`.
  - Include `aria-hidden="true"` on decorative icons.
  - Position icons neatly with `display: flex; align-items: center; gap: 6px;`.

## 2. Uncluttered, Purposeful Layouts
- **Remove Redundant Controls**: If a control (e.g. dropdown, inactive input) cannot perform an actionable function or confuses user expectations, eliminate it.
- **Concise Copy**: Prefer brief, self-describing placeholder text and control labels over lengthy explanations.
- **Consistent Visual Hierarchy**: Follow design tokens (colors, typography, radii, spacing) without ad-hoc inline visual noise.

## 3. Frontend Secret Handling
- **No Secrets in Web Storage**: Never persist cleartext secrets (API keys, credentials, tokens) in `localStorage` or `sessionStorage`.
- **In-Memory Testing**: Keep user-entered credentials in React state (RAM) during interactive sessions.
