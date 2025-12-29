import styled from 'styled-components';
import React, { useEffect, useState, useCallback } from 'react';
import { Badge, Spinner } from '../UI';
import { api } from '../../utils/api';
import { useWebSocket } from '../../hooks/useWebSocket';
import type { EventDTO } from '../../types';
import { useNavigate } from 'react-router-dom';

const POLLING_INTERVAL = 30000;

const LogsCard = styled.div`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-radius: ${({ theme }) => theme.radius.md};
  overflow: hidden;
`;

const LogsHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${({ theme }) => theme.spacing.lg};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border.default};
`;

const LogsTitle = styled.h3`
  font-size: 16px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};
  margin: 0;
`;

const ViewAllLink = styled.a`
  font-size: 13px;
  color: ${({ theme }) => theme.colors.accent.primary};
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
`;

const LogsList = styled.div`
  max-height: 400px;
  overflow-y: auto;
`;

const LogItem = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${({ theme }) => theme.spacing.md};
  padding: ${({ theme }) => theme.spacing.md} ${({ theme }) => theme.spacing.lg};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border.default};
  transition: background ${({ theme }) => theme.transition.fast};

  &:hover {
    background: ${({ theme }) => theme.colors.bg.tertiary};
  }

  &:last-child {
    border-bottom: none;
  }
`;

const LogTime = styled.span`
  font-size: 12px;
  font-family: ${({ theme }) => theme.typography.monoFamily};
  color: ${({ theme }) => theme.colors.text.tertiary};
  white-space: nowrap;
`;

const LogContent = styled.div`
  flex: 1;
  min-width: 0;
`;

const LogMessage = styled.p`
  font-size: 13px;
  font-family: ${({ theme }) => theme.typography.monoFamily};
  color: ${({ theme }) => theme.colors.text.primary};
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const LogMeta = styled.span`
  font-size: 11px;
  color: ${({ theme }) => theme.colors.text.tertiary};
`;

const EmptyState = styled.div`
  text-align: center;
  padding: 40px 20px;
  color: ${({ theme }) => theme.colors.text.tertiary};
  font-size: 14px;
`;

const LoadingState = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 20px;
`;

const getLevelVariant = (level: string): 'error' | 'warning' | 'info' | 'default' => {
  const normalized = level?.toUpperCase();
  switch (normalized) {
    case 'ERROR':
    case 'CRITICAL':
      return 'error';
    case 'WARNING':
    case 'WARN':
      return 'warning';
    case 'INFO':
      return 'info';
    default:
      return 'default';
  }
};

const formatTime = (timestamp: string): string => {
  try {
    const date = new Date(timestamp);
    return date.toLocaleTimeString('en-US', { hour12: false });
  } catch {
    return timestamp;
  }
};

export const RecentLogs: React.FC = () => {
  const [logs, setLogs] = useState<EventDTO[]>([]);
  const [initialLoading, setInitialLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const { connected, subscribeToTopic } = useWebSocket();
  const isInitialLoadRef = React.useRef(true);

  const loadRecentLogs = useCallback(async () => {
    try {
      if (isInitialLoadRef.current) {
        setInitialLoading(true);
      }
      setError(null);
      const response = await api.getRecentLogs(10);
      setLogs(response || []);
    } catch (err) {
      console.error('Failed to load recent logs:', err);
      setError(err instanceof Error ? err.message : 'Failed to load logs');
      setLogs([]);
    } finally {
      setInitialLoading(false);
      isInitialLoadRef.current = false;
    }
  }, []);

  useEffect(() => {
    loadRecentLogs();
  }, [loadRecentLogs]);

  useEffect(() => {
    const interval = setInterval(loadRecentLogs, POLLING_INTERVAL);
    return () => clearInterval(interval);
  }, [loadRecentLogs]);

  useEffect(() => {
    if (!connected) return;

    const unsubscribeEvents = subscribeToTopic('/topic/events', (message) => {
      if (message.type === 'event' && 'data' in message && message.data) {
        setLogs(prevLogs => {
          const newLog = message.data as EventDTO;
          const updatedLogs = [newLog, ...prevLogs].slice(0, 10);
          return updatedLogs;
        });
      }
    });

    return () => {
      unsubscribeEvents();
    };
  }, [connected, subscribeToTopic]);

  return (
    <LogsCard>
      <LogsHeader>
        <LogsTitle>Recent Logs</LogsTitle>
        <ViewAllLink onClick={() => navigate('/search')}>View All</ViewAllLink>
      </LogsHeader>
      <LogsList>
        {initialLoading && (
          <LoadingState>
            <Spinner />
          </LoadingState>
        )}
        {!initialLoading && error && (
          <EmptyState>Failed to load logs: {error}</EmptyState>
        )}
        {!initialLoading && !error && logs.length === 0 && (
          <EmptyState>No recent logs found</EmptyState>
        )}
        {!initialLoading && !error && logs.length > 0 && logs.map((log) => (
          <LogItem key={log.id}>
            <LogTime>{formatTime(log.timestamp)}</LogTime>
            <Badge $variant={getLevelVariant(log.severity)} $size="sm">
              {(log.severity || 'INFO').toUpperCase()}
            </Badge>
            <LogContent>
              <LogMessage>{log.rawMessage || log.rawData || 'No message'}</LogMessage>
              <LogMeta>{log.sourceName || log.sourcetype || 'unknown'}</LogMeta>
            </LogContent>
          </LogItem>
        ))}
      </LogsList>
    </LogsCard>
  );
};
