export const theme = {
  colors: {
    bg: {
      primary: '#0F1211',
      secondary: '#1F2121',
      tertiary: '#26282A',
      sidebar: '#131D1B',
      header: '#1F2121',
      input: '#26282A',
    },
    accent: {
      primary: '#32B8C6',
      primaryHover: '#2186A0',
      primaryActive: '#1A6473',
      secondary: '#E67F48',
    },
    semantic: {
      success: '#3DCC71',
      error: '#FF5459',
      warning: '#F0AD4E',
      info: '#5AB1D1',
      neutral: '#747676',
    },
    text: {
      primary: '#FFFFFF',
      secondary: '#A1A9A8',
      tertiary: '#616363',
      link: '#32B8C6',
    },
    border: {
      default: '#26282A',
      light: '#424445',
      hover: '#5AB1D1',
    },
    sidebar: {
      active: '#2A403D',
      hover: '#1F2A28',
    },
  },
  spacing: {
    xs: '4px',
    sm: '8px',
    md: '12px',
    lg: '16px',
    xl: '24px',
    '2xl': '32px',
    '3xl': '48px',
  },
  radius: {
    sm: '4px',
    md: '6px',
    lg: '8px',
    full: '50%',
  },
  shadows: {
    sm: '0 1px 3px rgba(0, 0, 0, 0.3)',
    md: '0 4px 12px rgba(0, 0, 0, 0.3)',
    lg: '0 8px 24px rgba(0, 0, 0, 0.4)',
  },
  transition: {
    fast: '150ms ease',
    normal: '200ms ease',
    slow: '300ms ease',
  },
  sizes: {
    headerHeight: '64px',
    sidebarWidth: '250px',
    sidebarCollapsedWidth: '64px',
    modalSmall: '400px',
    modalMedium: '600px',
    modalLarge: '800px',
  },
  typography: {
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif",
    monoFamily: "'Menlo', 'Monaco', 'Courier New', monospace",
  },
};

export type Theme = typeof theme;
