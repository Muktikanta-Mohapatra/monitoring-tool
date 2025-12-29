# UI - Complete Documentation

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Key Components](#key-components)
4. [Pages](#pages)
5. [Styling System](#styling-system)
6. [Routing](#routing)
7. [State Management](#state-management)
8. [API Integration](#api-integration)
9. [Testing](#testing)
10. [Running the Project](#running-the-project)

---

## Overview

The UI is a modern React 19 TypeScript application providing a Splunk-like interface for log monitoring, search, and forwarder management. It uses styled-components for theming and Recharts for data visualization.

### Technology Stack
- **Framework**: React 19.2
- **Language**: TypeScript 5.9
- **Build Tool**: Vite 7.2
- **Routing**: React Router DOM 7.10
- **Styling**: styled-components 6.1
- **Icons**: Lucide React 0.561
- **Charts**: Recharts 3.5
- **Package Manager**: npm

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              UI Application                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                            App.tsx                                        │ │
│  │  ┌─────────────────┐  ┌────────────────┐  ┌──────────────────────────┐  │ │
│  │  │  ThemeProvider  │  │ GlobalStyles   │  │    BrowserRouter         │  │ │
│  │  └─────────────────┘  └────────────────┘  └──────────────────────────┘  │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                    │                                          │
│  ┌─────────────────────────────────┴───────────────────────────────────────┐ │
│  │                             Layout                                        │ │
│  │  ┌──────────────────┐  ┌────────────────────────────────────────────┐   │ │
│  │  │     Header       │  │                  Content                    │   │ │
│  │  └──────────────────┘  │   ┌──────────────────────────────────────┐ │   │ │
│  │  ┌──────────────────┐  │   │              Routes                   │ │   │ │
│  │  │     Sidebar      │  │   │  ┌──────────┐ ┌────────┐ ┌─────────┐ │ │   │ │
│  │  │  - Dashboard     │  │   │  │Dashboard │ │Search  │ │Forward  │ │ │   │ │
│  │  │  - Search        │  │   │  └──────────┘ └────────┘ └─────────┘ │ │   │ │
│  │  │  - Forwarders    │  │   │  ┌──────────┐ ┌────────┐ ┌─────────┐ │ │   │ │
│  │  │  - Config        │  │   │  │Config    │ │Users   │ │Settings │ │ │   │ │
│  │  │  - Settings      │  │   │  └──────────┘ └────────┘ └─────────┘ │ │   │ │
│  │  └──────────────────┘  │   └──────────────────────────────────────┘ │   │ │
│  │                        └────────────────────────────────────────────┘   │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
│                                                                               │
│  ┌─────────────────────────────────────────────────────────────────────────┐ │
│  │                          UI Components                                    │ │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────────┐  │ │
│  │  │ Button │ │ Input  │ │ Table  │ │ Modal  │ │ Badge  │ │ Pagination │  │ │
│  │  └────────┘ └────────┘ └────────┘ └────────┘ └────────┘ └────────────┘  │ │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────────┐  │ │
│  │  │ Card   │ │ Select │ │ Tabs   │ │ Toggle │ │Spinner │ │  Charts    │  │ │
│  │  └────────┘ └────────┘ └────────┘ └────────┘ └────────┘ └────────────┘  │ │
│  └─────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Key Components

### 1. Application Entry (`src/main.tsx`)

```typescript
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)
```

### 2. App Root (`src/App.tsx`)

```typescript
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider } from 'styled-components';
import { theme } from './styles/theme';
import { GlobalStyles } from './styles/globalStyles';
import { Layout } from './components/Common';
import { Dashboard, LogSearch, Forwarders, Configuration, Users, Settings } from './pages';

function App() {
  return (
    <ThemeProvider theme={theme}>
      <GlobalStyles />
      <Router>
        <Layout>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/search" element={<LogSearch />} />
            <Route path="/forwarders" element={<Forwarders />} />
            <Route path="/config" element={<Configuration />} />
            <Route path="/admin/users" element={<Users />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </Layout>
      </Router>
    </ThemeProvider>
  );
}
```

### 3. Layout Components (`src/components/Common/`)

#### Layout.tsx
```typescript
interface LayoutProps {
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <LayoutContainer>
      <Header onMenuToggle={() => setSidebarOpen(!sidebarOpen)} />
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <MainContent>
        <ContentWrapper>{children}</ContentWrapper>
      </MainContent>
    </LayoutContainer>
  );
};
```

#### Sidebar.tsx
Navigation structure:
- **Home** - `/`
- **Dashboards** (collapsible)
  - Overview - `/dashboard`
  - Performance - `/dashboard/performance`
  - Errors & Alerts - `/dashboard/alerts`
- **Searches** (collapsible)
  - Log Search - `/search`
  - Saved Searches - `/search/saved`
- **Forwarders** (collapsible)
  - Status Monitor - `/forwarders`
  - Configuration - `/forwarders/config`
- **Administration** (collapsible)
  - Configuration - `/config`
  - User Management - `/admin/users`
  - Data Inputs - `/config/inputs`
  - API Keys - `/settings/api-keys`
- **Documentation** (collapsible)
  - Help - `/help`
  - API Docs - `/docs/api`
- **Settings** - `/settings`

#### Header.tsx
- Logo and branding
- Search bar
- User menu
- Notifications

### 4. UI Components (`src/components/UI/`)

| Component | File | Props |
|-----------|------|-------|
| `Button` | `Button.tsx` | `variant`, `size`, `icon`, `disabled`, `loading` |
| `Input` | `Input.tsx` | `type`, `placeholder`, `value`, `onChange`, `icon`, `error` |
| `Select` | `Select.tsx` | `options`, `value`, `onChange`, `placeholder`, `fullWidth` |
| `Table` | `Table.tsx` | `TableContainer`, `Table`, `TableHead`, `TableBody`, `TableRow`, `TableCell` |
| `Modal` | `Modal.tsx` | `isOpen`, `onClose`, `title`, `children`, `size` |
| `Badge` | `Badge.tsx` | `$variant` (success, error, warning, info, default) |
| `Card` | `Card.tsx` | `title`, `children`, `footer` |
| `Pagination` | `Pagination.tsx` | `currentPage`, `totalPages`, `onPageChange` |
| `Spinner` | `Spinner.tsx` | `size` |
| `Tabs` | `Tabs.tsx` | `tabs`, `activeTab`, `onChange` |
| `Toggle` | `Toggle.tsx` | `checked`, `onChange`, `label` |

#### Button Component
```typescript
interface ButtonProps {
  variant?: 'primary' | 'secondary' | 'outline' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  icon?: React.ReactNode;
  loading?: boolean;
  disabled?: boolean;
  children: React.ReactNode;
  onClick?: () => void;
}

<Button variant="primary" size="md" icon={<Plus size={16} />}>
  Add Item
</Button>
```

#### Badge Component
```typescript
interface BadgeProps {
  $variant: 'success' | 'error' | 'warning' | 'info' | 'default';
  children: React.ReactNode;
}

<Badge $variant="success">ACTIVE</Badge>
<Badge $variant="error">OFFLINE</Badge>
<Badge $variant="warning">WARNING</Badge>
```

### 5. Dashboard Components (`src/components/Dashboard/`)

| Component | Purpose |
|-----------|---------|
| `StatCard` | Display metric with icon, value, change indicator |
| `ChartContainer` | Wrapper for Recharts charts |
| `SystemHealthChart` | Pie/donut chart for system health |
| `LogsOverTimeChart` | Line/area chart for log volume |
| `RecentLogs` | Table of recent log entries |

#### StatCard Component
```typescript
interface StatCardProps {
  title: string;
  value: string;
  change?: string;
  changeType?: 'positive' | 'negative' | 'neutral';
  icon: React.ReactNode;
  color: string;
}

<StatCard
  title="Total Logs"
  value="2.4M"
  change="+12% from last hour"
  changeType="positive"
  icon={<FileText size={24} />}
  color="#32B8C6"
/>
```

---

## Pages

### 1. Dashboard (`src/pages/Dashboard.tsx`)

**Features:**
- Statistics grid (4 columns): Total Logs, Active Forwarders, Failed Connections, Data Processed
- Charts section: System Health (pie), Logs Over Time (line)
- Recent Logs table
- Active Alerts panel

**Sample Data Structure:**
```typescript
const alerts = [
  { id: '1', severity: 'error', message: 'Forwarder offline', time: '5 min ago' },
  { id: '2', severity: 'warning', message: 'High CPU usage', time: '12 min ago' },
];
```

### 2. Log Search (`src/pages/LogSearch.tsx`)

**Features:**
- Search bar with Splunk-like query syntax
- Time range filter
- Log level filter (All, Error, Warning, Info, Debug)
- Active filters display with removal
- Results table with columns: Timestamp, Level, Source, Message
- Pagination
- Export functionality

**Search Query Example:**
```
error OR warning source=app-server-* level>=ERROR
```

**State Management:**
```typescript
const [searchQuery, setSearchQuery] = useState('');
const [currentPage, setCurrentPage] = useState(1);
const [logLevel, setLogLevel] = useState('');
const [activeFilters, setActiveFilters] = useState<string[]>([]);
```

### 3. Forwarders (`src/pages/Forwarders.tsx`)

**Features:**
- Summary cards: Online, Warning, Offline, Total counts
- Forwarder grid (3 columns)
- Each forwarder card shows:
  - Name and IP
  - Status badge
  - CPU usage with progress bar
  - Memory usage with progress bar
  - Logs per second
  - Uptime

**Forwarder Data Structure:**
```typescript
interface Forwarder {
  id: string;
  name: string;
  ip: string;
  status: 'online' | 'offline' | 'warning';
  cpu: number;
  memory: number;
  logsPerSec: number;
  uptime: string;
}
```

### 4. Configuration (`src/pages/Configuration.tsx`)

**Features:**
- Input configuration
- Output configuration
- Parser settings
- System settings
- Tabs navigation

### 5. Users (`src/pages/Users.tsx`)

**Features:**
- User list table
- Add/Edit user modal
- Role assignment
- Status toggle
- Last login display

### 6. Settings (`src/pages/Settings.tsx`)

**Features:**
- General settings
- Security settings
- Notification settings
- API key management
- Theme customization

---

## Styling System

### Theme (`src/styles/theme.ts`)

```typescript
export const theme = {
  colors: {
    bg: {
      primary: '#0F1211',      // Main background
      secondary: '#1F2121',    // Card background
      tertiary: '#26282A',     // Input background
      sidebar: '#131D1B',      // Sidebar background
      header: '#1F2121',       // Header background
    },
    accent: {
      primary: '#32B8C6',      // Teal accent
      primaryHover: '#2186A0',
      secondary: '#E67F48',    // Orange accent
    },
    semantic: {
      success: '#3DCC71',      // Green
      error: '#FF5459',        // Red
      warning: '#F0AD4E',      // Yellow
      info: '#5AB1D1',         // Light blue
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
```

### Global Styles (`src/styles/globalStyles.ts`)

```typescript
import { createGlobalStyle } from 'styled-components';

export const GlobalStyles = createGlobalStyle`
  * {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
  }

  html, body {
    height: 100%;
    font-family: ${({ theme }) => theme.typography.fontFamily};
    background: ${({ theme }) => theme.colors.bg.primary};
    color: ${({ theme }) => theme.colors.text.primary};
    line-height: 1.5;
    -webkit-font-smoothing: antialiased;
  }

  button {
    cursor: pointer;
    border: none;
    background: none;
  }

  a {
    text-decoration: none;
    color: inherit;
  }

  ::-webkit-scrollbar {
    width: 8px;
    height: 8px;
  }

  ::-webkit-scrollbar-track {
    background: ${({ theme }) => theme.colors.bg.tertiary};
  }

  ::-webkit-scrollbar-thumb {
    background: ${({ theme }) => theme.colors.border.light};
    border-radius: 4px;
  }
`;
```

### Styled Component Example

```typescript
import styled from 'styled-components';

const Card = styled.div`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-radius: ${({ theme }) => theme.radius.md};
  padding: ${({ theme }) => theme.spacing.lg};
  transition: all ${({ theme }) => theme.transition.normal};

  &:hover {
    transform: translateY(-2px);
    box-shadow: ${({ theme }) => theme.shadows.md};
  }
`;

const Title = styled.h2`
  font-size: 18px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};
  margin-bottom: ${({ theme }) => theme.spacing.md};
`;
```

---

## Routing

### Route Configuration

| Path | Component | Description |
|------|-----------|-------------|
| `/` | `Dashboard` | Home/Dashboard |
| `/dashboard` | `Dashboard` | Dashboard view |
| `/dashboard/*` | `Dashboard` | Dashboard sub-routes |
| `/search` | `LogSearch` | Log search |
| `/search/*` | `LogSearch` | Search sub-routes |
| `/forwarders` | `Forwarders` | Forwarder management |
| `/forwarders/*` | `Forwarders` | Forwarder sub-routes |
| `/config` | `Configuration` | System configuration |
| `/config/*` | `Configuration` | Config sub-routes |
| `/admin/users` | `Users` | User management |
| `/settings` | `Settings` | Application settings |
| `/settings/*` | `Settings` | Settings sub-routes |
| `*` | Redirect to `/` | Catch-all redirect |

### Navigation Link Example

```typescript
import { NavLink } from 'react-router-dom';

<NavLink to="/dashboard" className={({ isActive }) => isActive ? 'active' : ''}>
  Dashboard
</NavLink>
```

---

## State Management

### Local State (useState)
Currently using React's built-in `useState` for component-level state:

```typescript
const [searchQuery, setSearchQuery] = useState('');
const [currentPage, setCurrentPage] = useState(1);
const [filters, setFilters] = useState<string[]>([]);
const [isModalOpen, setIsModalOpen] = useState(false);
```

### Future State Management Options

For production, consider adding:

**1. Redux Toolkit:**
```typescript
// store/slices/eventsSlice.ts
import { createSlice, PayloadAction } from '@reduxjs/toolkit';

const eventsSlice = createSlice({
  name: 'events',
  initialState: { items: [], loading: false },
  reducers: {
    setEvents: (state, action: PayloadAction<Event[]>) => {
      state.items = action.payload;
    },
    setLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload;
    },
  },
});
```

**2. React Context:**
```typescript
// context/AuthContext.tsx
export const AuthContext = createContext<AuthContextType | null>(null);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);

  return (
    <AuthContext.Provider value={{ user, token, setUser, setToken }}>
      {children}
    </AuthContext.Provider>
  );
};
```

---

## API Integration

### API Client Setup

Create an API client for middleware communication:

```typescript
// utils/api.ts
const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

export const api = {
  async get<T>(endpoint: string): Promise<T> {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
        'Content-Type': 'application/json',
      },
    });
    if (!response.ok) throw new Error(response.statusText);
    return response.json();
  },

  async post<T>(endpoint: string, data: any): Promise<T> {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(data),
    });
    if (!response.ok) throw new Error(response.statusText);
    return response.json();
  },
};
```

### API Hooks

```typescript
// hooks/useEvents.ts
export const useEvents = (params: SearchParams) => {
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchEvents = async () => {
      setLoading(true);
      try {
        const data = await api.get<EventsResponse>(`/v1/events/search?${new URLSearchParams(params)}`);
        setEvents(data.events);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };
    fetchEvents();
  }, [params]);

  return { events, loading, error };
};
```

### WebSocket Integration

```typescript
// hooks/useWebSocket.ts
import { useEffect, useRef, useState } from 'react';

export const useWebSocket = (url: string) => {
  const [messages, setMessages] = useState<any[]>([]);
  const [connected, setConnected] = useState(false);
  const ws = useRef<WebSocket | null>(null);

  useEffect(() => {
    ws.current = new WebSocket(url);

    ws.current.onopen = () => setConnected(true);
    ws.current.onclose = () => setConnected(false);
    ws.current.onmessage = (event) => {
      setMessages((prev) => [...prev, JSON.parse(event.data)]);
    };

    return () => ws.current?.close();
  }, [url]);

  const send = (data: any) => {
    ws.current?.send(JSON.stringify(data));
  };

  return { messages, connected, send };
};
```

---

## Testing

### Run Tests
```bash
cd ui
npm test
```

### Test with Coverage
```bash
npm run test:coverage
```

### Component Testing Example

```typescript
// __tests__/Button.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { ThemeProvider } from 'styled-components';
import { theme } from '../styles/theme';
import { Button } from '../components/UI';

describe('Button', () => {
  const renderWithTheme = (ui: React.ReactElement) => {
    return render(
      <ThemeProvider theme={theme}>{ui}</ThemeProvider>
    );
  };

  it('renders with text', () => {
    renderWithTheme(<Button>Click me</Button>);
    expect(screen.getByText('Click me')).toBeInTheDocument();
  });

  it('calls onClick when clicked', () => {
    const handleClick = jest.fn();
    renderWithTheme(<Button onClick={handleClick}>Click me</Button>);
    fireEvent.click(screen.getByText('Click me'));
    expect(handleClick).toHaveBeenCalledTimes(1);
  });

  it('is disabled when disabled prop is true', () => {
    renderWithTheme(<Button disabled>Click me</Button>);
    expect(screen.getByText('Click me')).toBeDisabled();
  });
});
```

### Manual Testing Checklist

**Dashboard:**
- [ ] Statistics cards display correctly
- [ ] Charts render with data
- [ ] Recent logs table loads
- [ ] Alerts panel shows items
- [ ] Refresh button works

**Log Search:**
- [ ] Search input accepts text
- [ ] Time range filter works
- [ ] Log level filter filters results
- [ ] Active filters can be removed
- [ ] Pagination navigates pages
- [ ] Export button triggers download

**Forwarders:**
- [ ] Summary cards show counts
- [ ] Forwarder cards display status
- [ ] Progress bars reflect CPU/memory
- [ ] Add Forwarder button works

**Settings:**
- [ ] All tabs navigate correctly
- [ ] Form inputs save values
- [ ] Toggle switches work

---

## Running the Project

### Prerequisites
- Node.js 18+
- npm 9+

### Install Dependencies
```bash
cd ui
npm install
```

### Development Server
```bash
npm run dev
```
Application runs at `http://localhost:5173`

### Build for Production
```bash
npm run build
```
Output in `dist/` directory

### Preview Production Build
```bash
npm run preview
```

### Lint Code
```bash
npm run lint
```

### Environment Variables

Create `.env` file:
```env
VITE_API_URL=http://localhost:8080/api
VITE_WS_URL=ws://localhost:8080/api/ws
```

### Docker Build
```bash
# Build image
docker build -t logforwarder-ui:latest .

# Run container
docker run -p 80:80 logforwarder-ui:latest
```

### Nginx Configuration (Production)
```nginx
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api {
        proxy_pass http://middleware:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host $host;
        proxy_cache_bypass $http_upgrade;
    }
}
```

---

## File Structure

```
ui/
├── package.json                    # Dependencies
├── tsconfig.json                   # TypeScript config
├── vite.config.ts                  # Vite config
├── eslint.config.js               # ESLint config
├── index.html                      # HTML entry
├── public/
│   └── vite.svg                   # Favicon
├── dist/                          # Production build
│   ├── index.html
│   └── assets/
└── src/
    ├── main.tsx                   # Entry point
    ├── App.tsx                    # Root component
    ├── index.css                  # Base CSS
    ├── assets/
    │   └── react.svg
    ├── components/
    │   ├── Common/                # Layout components
    │   │   ├── Header.tsx
    │   │   ├── Layout.tsx
    │   │   ├── Sidebar.tsx
    │   │   └── index.ts
    │   ├── Dashboard/             # Dashboard widgets
    │   │   ├── ChartContainer.tsx
    │   │   ├── RecentLogs.tsx
    │   │   ├── StatCard.tsx
    │   │   └── index.ts
    │   └── UI/                    # Reusable UI components
    │       ├── Badge.tsx
    │       ├── Button.tsx
    │       ├── Card.tsx
    │       ├── Input.tsx
    │       ├── Modal.tsx
    │       ├── Pagination.tsx
    │       ├── Select.tsx
    │       ├── Spinner.tsx
    │       ├── Table.tsx
    │       ├── Tabs.tsx
    │       ├── Toggle.tsx
    │       └── index.ts
    ├── pages/                     # Page components
    │   ├── Configuration.tsx
    │   ├── Dashboard.tsx
    │   ├── Forwarders.tsx
    │   ├── LogSearch.tsx
    │   ├── Settings.tsx
    │   ├── Users.tsx
    │   └── index.ts
    ├── styles/                    # Theming
    │   ├── globalStyles.ts
    │   ├── styled.d.ts
    │   └── theme.ts
    ├── context/                   # React contexts
    ├── hooks/                     # Custom hooks
    ├── types/                     # TypeScript types
    └── utils/                     # Utilities
```

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Blank page after build | Check base path in vite.config.ts |
| API CORS errors | Configure CORS on middleware or use proxy |
| Styles not applying | Verify ThemeProvider wraps app |
| Routes not working | Check BrowserRouter setup |
| Charts not rendering | Verify Recharts data format |
| WebSocket disconnect | Check WS URL and middleware status |
