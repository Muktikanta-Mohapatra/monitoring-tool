import styled from 'styled-components';
import React, { useState, useEffect, useCallback, useRef } from 'react';
import { FileText, Server, AlertTriangle, Activity, Loader } from 'lucide-react';
import { PageHeader, PageTitle, PageSubtitle, Grid } from '../components/Common';
import { StatCard, SystemHealthChart, LogsOverTimeChart, RecentLogs } from '../components/Dashboard';
import { api } from '../utils/api';
import { useWebSocket } from '../hooks/useWebSocket';
import type { AlertDTO, DashboardSummary } from '../types';

const POLLING_INTERVAL = 30000;

const StatsGrid = styled(Grid)`
  margin-bottom: ${({ theme }) => theme.spacing['2xl']};
`;

const ChartsGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: ${({ theme }) => theme.spacing.lg};
  margin-bottom: ${({ theme }) => theme.spacing['2xl']};

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
`;

const AlertsCard = styled.div`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-radius: ${({ theme }) => theme.radius.md};
  padding: ${({ theme }) => theme.spacing.lg};
`;

const AlertsHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: ${({ theme }) => theme.spacing.lg};
`;

const AlertsTitle = styled.h3`
  font-size: 16px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};
  margin: 0;
`;

const AlertsList = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.sm};
`;

const AlertItem = styled.div<{ $severity: 'error' | 'warning' | 'info' }>`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.md};
  padding: ${({ theme }) => theme.spacing.md};
  background: ${({ $severity }) => {
    switch ($severity) {
      case 'error': return 'rgba(255, 84, 89, 0.1)';
      case 'warning': return 'rgba(240, 173, 78, 0.1)';
      default: return 'rgba(90, 177, 209, 0.1)';
    }
  }};
  border-left: 3px solid ${({ theme, $severity }) => {
    switch ($severity) {
      case 'error': return theme.colors.semantic.error;
      case 'warning': return theme.colors.semantic.warning;
      default: return theme.colors.semantic.info;
    }
  }};
  border-radius: ${({ theme }) => theme.radius.sm};
`;

const AlertIcon = styled.div<{ $severity: 'error' | 'warning' | 'info' }>`
  color: ${({ theme, $severity }) => {
    switch ($severity) {
      case 'error': return theme.colors.semantic.error;
      case 'warning': return theme.colors.semantic.warning;
      default: return theme.colors.semantic.info;
    }
  }};
`;

const AlertContent = styled.div`
  flex: 1;
`;

const AlertMessage = styled.p`
  font-size: 13px;
  color: ${({ theme }) => theme.colors.text.primary};
  margin: 0;
`;

const AlertTime = styled.span`
  font-size: 11px;
  color: ${({ theme }) => theme.colors.text.tertiary};
`;

const ContentGrid = styled.div`
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: ${({ theme }) => theme.spacing.lg};

  @media (max-width: 1024px) {
    grid-template-columns: 1fr;
  }
`;

const formatRelativeTime = (date: string): string => {
  const now = new Date();
  const then = new Date(date);
  const seconds = Math.floor((now.getTime() - then.getTime()) / 1000);

  if (seconds < 60) return `${Math.floor(seconds)}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
};

const getSeverityType = (severity: string): 'error' | 'warning' | 'info' => {
  switch (severity?.toUpperCase()) {
    case 'CRITICAL':
    case 'ERROR':
      return 'error';
    case 'WARNING':
      return 'warning';
    default:
      return 'info';
  }
};

export const Dashboard: React.FC = () => {
  const [alerts, setAlerts] = useState<Array<AlertDTO & { timeAgo: string }>>([]);
  const [dashboardData, setDashboardData] = useState<DashboardSummary | null>(null);
  const [initialLoading, setInitialLoading] = useState(true);
  const [alertsInitialLoading, setAlertsInitialLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const isInitialLoadRef = useRef(true);
  const isAlertsInitialLoadRef = useRef(true);

  const { connected, subscribeToTopic } = useWebSocket();

  const loadDashboardData = useCallback(async () => {
    try {
      if (isInitialLoadRef.current) {
        setInitialLoading(true);
      }
      const summary = await api.getDashboardSummary();
      setDashboardData(summary);
    } catch (err) {
      console.error('Failed to load dashboard summary:', err);
    } finally {
      setInitialLoading(false);
      isInitialLoadRef.current = false;
    }
  }, []);

  const loadAlerts = useCallback(async () => {
    try {
      if (isAlertsInitialLoadRef.current) {
        setAlertsInitialLoading(true);
      }
      setError(null);
      const response = await api.getAlerts('OPEN');
      const formattedAlerts = (response || [])
        .slice(0, 4)
        .map(alert => ({
          ...alert,
          timeAgo: formatRelativeTime(alert.triggeredAt),
        }));
      setAlerts(formattedAlerts);
    } catch (err) {
      console.error('Failed to load alerts:', err);
      setError('Failed to load alerts');
    } finally {
      setAlertsInitialLoading(false);
      isAlertsInitialLoadRef.current = false;
    }
  }, []);

  useEffect(() => {
    loadDashboardData();
    loadAlerts();
  }, [loadDashboardData, loadAlerts]);

  useEffect(() => {
    const interval = setInterval(() => {
      loadDashboardData();
      loadAlerts();
    }, POLLING_INTERVAL);
    return () => clearInterval(interval);
  }, [loadDashboardData, loadAlerts]);

  useEffect(() => {
    if (!connected) return;

    const unsubscribeDashboard = subscribeToTopic('/topic/dashboard', (message) => {
      if (message.type === 'dashboard' && 'data' in message && message.data) {
        setDashboardData(message.data as DashboardSummary);
      }
    });

    const unsubscribeAlerts = subscribeToTopic('/topic/alerts', (message) => {
      if (message.type === 'alert') {
        loadAlerts();
      }
    });

    return () => {
      unsubscribeDashboard();
      unsubscribeAlerts();
    };
  }, [connected, subscribeToTopic, loadAlerts]);

  return (
    <>
      <PageHeader>
        <div>
          <PageTitle>Dashboard</PageTitle>
          <PageSubtitle>Overview of your log monitoring system</PageSubtitle>
        </div>
      </PageHeader>

      <StatsGrid $columns={4}>
        <StatCard
          title="Total Events (24h)"
          value={initialLoading ? '-' : (dashboardData?.total_events_last_24h?.toLocaleString() || '0')}
          change={initialLoading ? '' : `Live`}
          changeType="neutral"
          icon={<FileText size={24} />}
          color="#32B8C6"
        />
        <StatCard
          title="Active Forwarders"
          value={initialLoading ? '-' : (dashboardData?.active_forwarders?.toString() || '0')}
          change={initialLoading ? '' : `${dashboardData?.total_forwarders || 0} total`}
          changeType={(dashboardData?.active_forwarders || 0) > 0 ? "positive" : "neutral"}
          icon={<Server size={24} />}
          color="#3DCC71"
        />
        <StatCard
          title="Unacked Alerts"
          value={initialLoading ? '-' : (dashboardData?.unacknowledged_alerts?.toString() || '0')}
          change={initialLoading ? '' : `System alerts`}
          changeType={(dashboardData?.unacknowledged_alerts || 0) > 0 ? "negative" : "positive"}
          icon={<AlertTriangle size={24} />}
          color="#FF5459"
        />
        <StatCard
          title="Events/Second"
          value={initialLoading ? '-' : (dashboardData?.app_metrics?.eventsPerSecond?.toFixed(2) || '0')}
          change={initialLoading ? '' : `Current rate`}
          changeType="neutral"
          icon={<Activity size={24} />}
          color="#F0AD4E"
        />
      </StatsGrid>

      <ChartsGrid>
        <SystemHealthChart />
        <LogsOverTimeChart />
      </ChartsGrid>

      <ContentGrid>
        <RecentLogs />
        <AlertsCard>
          <AlertsHeader>
            <AlertsTitle>Active Alerts</AlertsTitle>
          </AlertsHeader>
          <AlertsList>
            {alertsInitialLoading ? (
              <div style={{ textAlign: 'center', padding: '20px', color: '#999' }}>
                <Loader size={24} style={{ animation: 'spin 1s linear infinite', margin: '0 auto' }} />
              </div>
            ) : error ? (
              <div style={{ textAlign: 'center', padding: '20px', color: '#ff3b30' }}>
                {error}
              </div>
            ) : alerts.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '20px', color: '#999' }}>
                No active alerts
              </div>
            ) : (
              alerts.map((alert) => (
                <AlertItem key={alert.id} $severity={getSeverityType(alert.severity)}>
                  <AlertIcon $severity={getSeverityType(alert.severity)}>
                    <AlertTriangle size={16} />
                  </AlertIcon>
                  <AlertContent>
                    <AlertMessage>{alert.triggerMessage || alert.status}</AlertMessage>
                    <AlertTime>{alert.timeAgo}</AlertTime>
                  </AlertContent>
                </AlertItem>
              ))
            )}
          </AlertsList>
        </AlertsCard>
      </ContentGrid>
    </>
  );
};
