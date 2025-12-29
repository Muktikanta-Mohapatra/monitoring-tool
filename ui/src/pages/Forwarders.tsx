import styled from 'styled-components';
import React, { useState, useEffect, useCallback } from 'react';
import { Server, Plus, RefreshCw, Cpu, HardDrive, Activity, AlertCircle } from 'lucide-react';
import { PageHeader, PageTitle, PageSubtitle, PageActions, Grid } from '../components/Common';
import { Button, Badge, StatusDot, Spinner } from '../components/UI';
import { api } from '../utils/api';
import { useWebSocket } from '../hooks/useWebSocket';
import type { ForwarderDTO } from '../types';

const POLLING_INTERVAL = 30000;

const SummaryCards = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: ${({ theme }) => theme.spacing.lg};
  margin-bottom: ${({ theme }) => theme.spacing['2xl']};

  @media (max-width: 1024px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 600px) {
    grid-template-columns: 1fr;
  }
`;

const SummaryCard = styled.div<{ $color: string }>`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-left: 4px solid ${({ $color }) => $color};
  border-radius: ${({ theme }) => theme.radius.md};
  padding: ${({ theme }) => theme.spacing.lg};
  display: flex;
  align-items: center;
  justify-content: space-between;
`;

const SummaryContent = styled.div``;

const SummaryValue = styled.div`
  font-size: 32px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};
`;

const SummaryLabel = styled.div`
  font-size: 13px;
  color: ${({ theme }) => theme.colors.text.secondary};
  margin-top: ${({ theme }) => theme.spacing.xs};
`;

const SummaryIcon = styled.div<{ $color: string }>`
  width: 48px;
  height: 48px;
  border-radius: ${({ theme }) => theme.radius.md};
  background: ${({ $color }) => `${$color}20`};
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${({ $color }) => $color};
`;

const ForwardersGrid = styled(Grid)`
  grid-template-columns: repeat(3, 1fr);

  @media (max-width: 1200px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
`;

const ForwarderCard = styled.div`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-radius: ${({ theme }) => theme.radius.md};
  overflow: hidden;
  transition: all ${({ theme }) => theme.transition.normal};

  &:hover {
    transform: translateY(-2px);
    box-shadow: ${({ theme }) => theme.shadows.md};
  }
`;

const ForwarderHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${({ theme }) => theme.spacing.lg};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border.default};
`;

const ForwarderInfo = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.md};
`;

const ForwarderIcon = styled.div<{ $status: 'online' | 'offline' | 'warning' }>`
  width: 40px;
  height: 40px;
  border-radius: ${({ theme }) => theme.radius.md};
  background: ${({ $status }) => {
    switch ($status) {
      case 'online': return 'rgba(61, 204, 113, 0.15)';
      case 'warning': return 'rgba(240, 173, 78, 0.15)';
      default: return 'rgba(255, 84, 89, 0.15)';
    }
  }};
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${({ theme, $status }) => {
    switch ($status) {
      case 'online': return theme.colors.semantic.success;
      case 'warning': return theme.colors.semantic.warning;
      default: return theme.colors.semantic.error;
    }
  }};
`;

const ForwarderName = styled.div`
  font-size: 14px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};
`;

const ForwarderIP = styled.div`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.text.tertiary};
  font-family: ${({ theme }) => theme.typography.monoFamily};
`;

const ForwarderBody = styled.div`
  padding: ${({ theme }) => theme.spacing.lg};
`;

const MetricsGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: ${({ theme }) => theme.spacing.md};
`;

const MetricItem = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.xs};
`;

const MetricLabel = styled.span`
  font-size: 11px;
  color: ${({ theme }) => theme.colors.text.tertiary};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const MetricValue = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
  font-size: 14px;
  color: ${({ theme }) => theme.colors.text.primary};
`;

const MetricIcon = styled.span<{ $color?: string }>`
  color: ${({ $color, theme }) => $color || theme.colors.text.secondary};
  display: flex;
  align-items: center;
`;

const ProgressBar = styled.div<{ $value: number; $color: string }>`
  height: 4px;
  background: ${({ theme }) => theme.colors.bg.tertiary};
  border-radius: 2px;
  overflow: hidden;
  margin-top: ${({ theme }) => theme.spacing.xs};

  &::after {
    content: '';
    display: block;
    width: ${({ $value }) => $value}%;
    height: 100%;
    background: ${({ $color }) => $color};
    border-radius: 2px;
  }
`;

const ForwarderFooter = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${({ theme }) => theme.spacing.md} ${({ theme }) => theme.spacing.lg};
  background: ${({ theme }) => theme.colors.bg.tertiary};
  border-top: 1px solid ${({ theme }) => theme.colors.border.default};
`;

const FooterStat = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
  font-size: 12px;
  color: ${({ theme }) => theme.colors.text.secondary};
`;

const LoadingContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: ${({ theme }) => theme.spacing['2xl']};
  gap: ${({ theme }) => theme.spacing.lg};
  background: ${({ theme }) => theme.colors.bg.secondary};
  border-radius: ${({ theme }) => theme.radius.md};
  min-height: 300px;
`;

const EmptyContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: ${({ theme }) => theme.spacing['2xl']};
  gap: ${({ theme }) => theme.spacing.md};
  background: ${({ theme }) => theme.colors.bg.secondary};
  border-radius: ${({ theme }) => theme.radius.md};
  min-height: 300px;
  color: ${({ theme }) => theme.colors.text.secondary};

  p {
    margin: 0;
    font-size: 15px;
  }
`;

const ErrorContainer = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${({ theme }) => theme.spacing.md};
  padding: ${({ theme }) => theme.spacing.lg};
  background: rgba(255, 84, 89, 0.1);
  border: 1px solid rgba(255, 84, 89, 0.3);
  border-radius: ${({ theme }) => theme.radius.md};
  margin-bottom: ${({ theme }) => theme.spacing.lg};
  color: #ff5459;
`;

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

const mapApiToForwarder = (dto: ForwarderDTO): Forwarder => {
  const statusMap: Record<string, 'online' | 'offline' | 'warning'> = {
    ACTIVE: 'online',
    INACTIVE: 'offline',
    WARNING: 'warning',
    OFFLINE: 'offline',
  };

  const calculateUptime = (lastHeartbeat?: string): string => {
    if (!lastHeartbeat) return '-';
    try {
      const lastHeartbeatDate = new Date(lastHeartbeat);
      const now = new Date();
      const seconds = Math.floor((now.getTime() - lastHeartbeatDate.getTime()) / 1000);

      if (seconds < 60) return `${seconds}s ago`;
      if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
      if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
      return `${Math.floor(seconds / 86400)}d ago`;
    } catch {
      return '-';
    }
  };

  return {
    id: dto.id,
    name: dto.name,
    ip: dto.ipAddress || 'N/A',
    status: statusMap[dto.status] || 'offline',
    cpu: dto.cpuUsagePercent || 0,
    memory: dto.memoryUsagePercent || 0,
    logsPerSec: Math.round((dto.totalEventsProcessed || 0) / 3600),
    uptime: calculateUptime(dto.lastHeartbeat),
  };
};

const getStatusVariant = (status: string): 'success' | 'warning' | 'error' => {
  switch (status) {
    case 'online': return 'success';
    case 'warning': return 'warning';
    default: return 'error';
  }
};

export const Forwarders: React.FC = () => {
  const [forwarders, setForwarders] = useState<Forwarder[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { connected, subscribeToTopic } = useWebSocket();

  const loadForwarders = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await api.getForwarders();
      const mapped = response.map(mapApiToForwarder);
      setForwarders(mapped);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load forwarders';
      setError(message);
      setForwarders([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadForwarders();
  }, [loadForwarders]);

  useEffect(() => {
    const interval = setInterval(loadForwarders, POLLING_INTERVAL);
    return () => clearInterval(interval);
  }, [loadForwarders]);

  useEffect(() => {
    if (!connected) return;

    const unsubscribeDashboard = subscribeToTopic('/topic/dashboard', (message) => {
      if (message.type === 'dashboard' || message.type === 'forwarder') {
        loadForwarders();
      }
    });

    const unsubscribeEvents = subscribeToTopic('/topic/events', (message) => {
      if (message.type === 'event' && message.action === 'forwarder_update') {
        loadForwarders();
      }
    });

    return () => {
      unsubscribeDashboard();
      unsubscribeEvents();
    };
  }, [connected, subscribeToTopic, loadForwarders]);

  const online = forwarders.filter(f => f.status === 'online').length;
  const warning = forwarders.filter(f => f.status === 'warning').length;
  const offline = forwarders.filter(f => f.status === 'offline').length;

  return (
    <>
      <PageHeader>
        <div>
          <PageTitle>Forwarders</PageTitle>
          <PageSubtitle>Monitor and manage your log forwarders</PageSubtitle>
        </div>
        <PageActions>
          <Button 
            variant="secondary" 
            icon={<RefreshCw size={16} />}
            onClick={loadForwarders}
            loading={loading}
          >
            Refresh
          </Button>
          <Button icon={<Plus size={16} />}>
            Add Forwarder
          </Button>
        </PageActions>
      </PageHeader>

      {error && (
        <ErrorContainer>
          <AlertCircle size={20} />
          <div>{error}</div>
        </ErrorContainer>
      )}

      {!loading && (
        <SummaryCards>
          <SummaryCard $color="#3DCC71">
            <SummaryContent>
              <SummaryValue>{online}</SummaryValue>
              <SummaryLabel>Online</SummaryLabel>
            </SummaryContent>
            <SummaryIcon $color="#3DCC71">
              <Server size={24} />
            </SummaryIcon>
          </SummaryCard>

          <SummaryCard $color="#F0AD4E">
            <SummaryContent>
              <SummaryValue>{warning}</SummaryValue>
              <SummaryLabel>Warning</SummaryLabel>
            </SummaryContent>
            <SummaryIcon $color="#F0AD4E">
              <Server size={24} />
            </SummaryIcon>
          </SummaryCard>

          <SummaryCard $color="#FF5459">
            <SummaryContent>
              <SummaryValue>{offline}</SummaryValue>
              <SummaryLabel>Offline</SummaryLabel>
            </SummaryContent>
            <SummaryIcon $color="#FF5459">
              <Server size={24} />
            </SummaryIcon>
          </SummaryCard>

          <SummaryCard $color="#32B8C6">
            <SummaryContent>
              <SummaryValue>{forwarders.length}</SummaryValue>
              <SummaryLabel>Total</SummaryLabel>
            </SummaryContent>
            <SummaryIcon $color="#32B8C6">
              <Server size={24} />
            </SummaryIcon>
          </SummaryCard>
        </SummaryCards>
      )}

      {loading && (
        <LoadingContainer>
          <Spinner />
          <p>Loading forwarders...</p>
        </LoadingContainer>
      )}

      {!loading && forwarders.length === 0 && !error && (
        <EmptyContainer>
          <Server size={32} />
          <p>No forwarders found. Add a new forwarder to get started.</p>
        </EmptyContainer>
      )}

      {!loading && forwarders.length > 0 && (
        <ForwardersGrid>
        {forwarders.map((forwarder) => (
          <ForwarderCard key={forwarder.id}>
            <ForwarderHeader>
              <ForwarderInfo>
                <ForwarderIcon $status={forwarder.status}>
                  <Server size={20} />
                </ForwarderIcon>
                <div>
                  <ForwarderName>{forwarder.name}</ForwarderName>
                  <ForwarderIP>{forwarder.ip}</ForwarderIP>
                </div>
              </ForwarderInfo>
              <Badge $variant={getStatusVariant(forwarder.status)}>
                <StatusDot $status={forwarder.status} />
                {forwarder.status.toUpperCase()}
              </Badge>
            </ForwarderHeader>

            <ForwarderBody>
              <MetricsGrid>
                <MetricItem>
                  <MetricLabel>CPU Usage</MetricLabel>
                  <MetricValue>
                    <MetricIcon $color={forwarder.cpu > 80 ? '#FF5459' : '#32B8C6'}>
                      <Cpu size={14} />
                    </MetricIcon>
                    {forwarder.cpu}%
                  </MetricValue>
                  <ProgressBar 
                    $value={forwarder.cpu} 
                    $color={forwarder.cpu > 80 ? '#FF5459' : '#32B8C6'} 
                  />
                </MetricItem>

                <MetricItem>
                  <MetricLabel>Memory</MetricLabel>
                  <MetricValue>
                    <MetricIcon $color={forwarder.memory > 80 ? '#FF5459' : '#3DCC71'}>
                      <HardDrive size={14} />
                    </MetricIcon>
                    {forwarder.memory}%
                  </MetricValue>
                  <ProgressBar 
                    $value={forwarder.memory} 
                    $color={forwarder.memory > 80 ? '#FF5459' : '#3DCC71'} 
                  />
                </MetricItem>
              </MetricsGrid>
            </ForwarderBody>

            <ForwarderFooter>
              <FooterStat>
                <Activity size={14} />
                {forwarder.logsPerSec.toLocaleString()} logs/sec
              </FooterStat>
              <FooterStat>
                Uptime: {forwarder.uptime}
              </FooterStat>
            </ForwarderFooter>
          </ForwarderCard>
        ))}
        </ForwardersGrid>
      )}
    </>
  );
};
