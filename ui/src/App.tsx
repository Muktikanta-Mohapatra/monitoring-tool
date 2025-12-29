import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ThemeProvider } from 'styled-components';
import { theme } from './styles/theme';
import { GlobalStyles } from './styles/globalStyles';
import { Layout } from './components/Common';
import { ErrorBoundary } from './components/ErrorBoundary';
import { Dashboard, LogSearch, Forwarders, Configuration, Users, Settings, Login } from './pages';
import { AuthProvider, useAuth, WebSocketProvider } from './context';

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return <div>Loading...</div>;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <WebSocketProvider>{children}</WebSocketProvider>;
};

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/*"
        element={
          <ProtectedRoute>
            <Layout>
              <Routes>
                <Route path="/" element={<Dashboard />} />
                <Route path="/dashboard" element={<Dashboard />} />
                <Route path="/dashboard/*" element={<Dashboard />} />
                <Route path="/search" element={<LogSearch />} />
                <Route path="/search/*" element={<LogSearch />} />
                <Route path="/forwarders" element={<Forwarders />} />
                <Route path="/forwarders/*" element={<Forwarders />} />
                <Route path="/config" element={<Configuration />} />
                <Route path="/config/*" element={<Configuration />} />
                <Route path="/admin/users" element={<Users />} />
                <Route path="/settings" element={<Settings />} />
                <Route path="/settings/*" element={<Settings />} />
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </Layout>
          </ProtectedRoute>
        }
      />
    </Routes>
  );
}

function App() {
  return (
    <ErrorBoundary>
      <ThemeProvider theme={theme}>
        <GlobalStyles />
        <AuthProvider>
          <Router>
            <AppRoutes />
          </Router>
        </AuthProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
}

export default App;
