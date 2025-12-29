import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { Client, type IFrame, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { api } from '../utils/api';
import {
  generateRequestId,
  isErrorMessage,
  isAckMessage,
} from '../utils/websocket-validator';
import type { WebSocketMessage, SubscriptionMessage, AlertMessage, MetricsMessage } from '../types/websocket';
import { VERSION } from '../types/websocket';

const WS_BASE_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws';
const ENABLE_WS_DEBUG = import.meta.env.VITE_WS_DEBUG === 'true';

interface WebSocketContextType {
  connected: boolean;
  connectionState: 'connecting' | 'connected' | 'disconnected' | 'error';
  lastError: string | null;
  subscribe: (query: string, indexes?: string[]) => void;
  unsubscribe: (subscriptionId: string) => void;
  subscribeToAlerts: () => void;
  subscribeToMetrics: (forwarderId: string) => void;
  subscribeToTopic: (topic: string, callback: (message: WebSocketMessage) => void) => () => void;
  lastMessage: WebSocketMessage | null;
  reconnectAttempt: number;
  lastErrorMessage: string | null;
}

const WebSocketContext = createContext<WebSocketContextType | undefined>(undefined);

function log(message: string, data?: unknown): void {
  if (ENABLE_WS_DEBUG) {
    console.log(`[WebSocket] ${message}`, data);
  }
}

function logError(message: string, error?: unknown): void {
  console.error(`[WebSocket] ${message}`, error);
}

export const WebSocketProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [connected, setConnected] = useState(false);
  const [connectionState, setConnectionState] = useState<'connecting' | 'connected' | 'disconnected' | 'error'>('disconnected');
  const [lastMessage, setLastMessage] = useState<WebSocketMessage | null>(null);
  const [lastError, setLastError] = useState<string | null>(null);
  const [lastErrorMessage, setLastErrorMessage] = useState<string | null>(null);
  const [reconnectAttempt, setReconnectAttempt] = useState(0);

  const clientRef = useRef<Client | null>(null);
  const reconnectTimeoutRef = useRef<number | null>(null);
  const reconnectAttemptRef = useRef(0);
  const isMountedRef = useRef(true);
  const subscriptionsRef = useRef<Map<string, () => void>>(new Map());
  const topicCallbacksRef = useRef<Map<string, Set<(message: WebSocketMessage) => void>>>(new Map());

  const handleMessage = useCallback((message: IMessage) => {
    if (!isMountedRef.current) return;

    try {
      const data = JSON.parse(message.body);
      setLastMessage(data);

      if (isErrorMessage(data)) {
        const errorMsg = `Error: ${data.errorMessage} (${data.errorCode})`;
        setLastErrorMessage(errorMsg);
        setLastError(errorMsg);
        log('Error message received', {
          errorCode: data.errorCode,
          severity: data.severity,
        });
      } else if (isAckMessage(data)) {
        if (data.status === 'SUCCESS') {
          setLastErrorMessage(null);
        } else if (data.status === 'FAILED') {
          const msg = data.message || 'Operation failed';
          setLastErrorMessage(msg);
          setLastError(msg);
        }
        log('Ack message received', {
          status: data.status,
          processedCount: data.processedCount,
        });
      }

      log('Message received', { type: data.type });
    } catch (err) {
      logError('Failed to parse WebSocket message', err);
      setLastError('Failed to parse server message');
    }
  }, []);

  const connect = useCallback(() => {
    const token = api.getToken();
    if (!token) {
      log('No authentication token available, skipping WebSocket connection');
      setConnectionState('disconnected');
      return;
    }

    if (clientRef.current?.connected) {
      log('STOMP client already connected');
      return;
    }

    setConnectionState('connecting');
    log(`Attempting STOMP connection (attempt ${reconnectAttemptRef.current + 1})`);

    try {
      const client = new Client({
        webSocketFactory: () => new SockJS(`${WS_BASE_URL}?token=${encodeURIComponent(token)}`),
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        debug: (str: string) => {
          if (ENABLE_WS_DEBUG) {
            console.log('[STOMP Debug]', str);
          }
        },
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        onConnect: (frame: IFrame) => {
          if (!isMountedRef.current) return;

          reconnectAttemptRef.current = 0;
          setReconnectAttempt(0);

          setConnected(true);
          setConnectionState('connected');
          setLastError(null);
          log('STOMP connected successfully', frame);

          client.subscribe('/user/queue/subscriptions', handleMessage);
          client.subscribe('/user/queue/errors', handleMessage);
          client.subscribe('/user/queue/alerts', handleMessage);
          client.subscribe('/user/queue/metrics', handleMessage);
          client.subscribe('/user/queue/queries', handleMessage);
          client.subscribe('/topic/events', handleMessage);
          client.subscribe('/topic/alerts', handleMessage);
          client.subscribe('/topic/dashboard', handleMessage);

          log('Subscribed to default topics');
        },
        onStompError: (frame: IFrame) => {
          if (!isMountedRef.current) return;

          const errorMsg = `STOMP error: ${frame.headers['message'] || 'Unknown error'}`;
          logError('STOMP error', frame.body);
          setConnectionState('error');
          setLastError(errorMsg);
        },
        onWebSocketClose: () => {
          if (!isMountedRef.current) return;

          setConnected(false);
          setConnectionState('disconnected');
          
          reconnectAttemptRef.current++;
          setReconnectAttempt(reconnectAttemptRef.current);
          
          const delay = Math.min(1000 * Math.pow(2, reconnectAttemptRef.current), 30000);
          setLastError(`Disconnected. Reconnecting in ${(delay / 1000).toFixed(1)}s`);
          
          log(`WebSocket closed. Scheduling reconnect in ${delay}ms`);
        },
        onWebSocketError: (event: Event) => {
          if (!isMountedRef.current) return;

          const errorMsg = 'WebSocket connection error';
          logError(errorMsg, event);
          setConnectionState('error');
          setLastError(errorMsg);
        },
      });

      clientRef.current = client;
      client.activate();
    } catch (err) {
      logError('Failed to create STOMP client', err);
      setConnectionState('error');
      setLastError(err instanceof Error ? err.message : 'Failed to initialize WebSocket');
    }
  }, [handleMessage]);

  useEffect(() => {
    isMountedRef.current = true;
    const reconnectTimeout = reconnectTimeoutRef.current;
    const subscriptions = subscriptionsRef.current;
    const topicCallbacks = topicCallbacksRef.current;
    const client = clientRef.current;

    const token = api.getToken();
    if (token && !client?.connected) {
      connect();
    }

    return () => {
      isMountedRef.current = false;
      if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
      }
      
      subscriptions.forEach((unsubscribe) => unsubscribe());
      subscriptions.clear();
      topicCallbacks.clear();

      if (clientRef.current) {
        clientRef.current.deactivate();
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const sendMessage = useCallback((destination: string, message: Partial<WebSocketMessage>) => {
    if (!clientRef.current?.connected) {
      logError('STOMP client not connected');
      setLastError('WebSocket connection not available');
      return;
    }

    try {
      const requestId = generateRequestId();
      const payload = {
        ...message,
        version: VERSION,
        requestId: message.requestId || requestId,
        timestamp: message.timestamp || new Date().toISOString(),
      };

      clientRef.current.publish({
        destination,
        body: JSON.stringify(payload),
      });

      log('Message sent', { destination, type: message.type, requestId });
    } catch (err) {
      logError('Failed to send message', err);
      setLastError('Failed to send message to server');
    }
  }, []);

  const subscribe = useCallback(
    (query: string, indexes?: string[]) => {
      const message: Partial<SubscriptionMessage> = {
        type: 'subscription',
        action: 'SUBSCRIBE',
        query,
        indexes: indexes || ['logs'],
      };
      sendMessage('/app/subscribe', message);
    },
    [sendMessage]
  );

  const unsubscribe = useCallback(
    (subscriptionId: string) => {
      const message: Partial<SubscriptionMessage> = {
        type: 'subscription',
        action: 'UNSUBSCRIBE',
        subscriptionId,
      };
      sendMessage('/app/unsubscribe', message);
    },
    [sendMessage]
  );

  const subscribeToAlerts = useCallback(() => {
    sendMessage('/app/alert-subscribe', {
      type: 'alert',
    } as Partial<AlertMessage>);
  }, [sendMessage]);

  const subscribeToMetrics = useCallback((forwarderId: string) => {
    const message: Partial<MetricsMessage> = {
      type: 'metrics',
      metricType: 'forwarder',
      collectedAt: new Date().toISOString(),
      tags: { forwarderId },
    };
    sendMessage('/app/metrics-subscribe', message);
  }, [sendMessage]);

  const subscribeToTopic = useCallback((topic: string, callback: (message: WebSocketMessage) => void) => {
    if (!clientRef.current?.connected) {
      logError('Cannot subscribe: STOMP client not connected');
      return () => {};
    }

    if (!topicCallbacksRef.current.has(topic)) {
      topicCallbacksRef.current.set(topic, new Set());
      
      const subscription = clientRef.current.subscribe(topic, (message: IMessage) => {
        try {
          const data = JSON.parse(message.body);
          const callbacks = topicCallbacksRef.current.get(topic);
          callbacks?.forEach(cb => cb(data));
        } catch (err) {
          logError('Failed to parse message from topic', err);
        }
      });

      subscriptionsRef.current.set(topic, () => {
        subscription.unsubscribe();
        topicCallbacksRef.current.delete(topic);
      });
    }

    topicCallbacksRef.current.get(topic)?.add(callback);
    log('Added callback to topic', topic);

    return () => {
      const callbacks = topicCallbacksRef.current.get(topic);
      callbacks?.delete(callback);
      
      if (callbacks?.size === 0) {
        const unsubscribe = subscriptionsRef.current.get(topic);
        unsubscribe?.();
        subscriptionsRef.current.delete(topic);
        topicCallbacksRef.current.delete(topic);
      }
    };
  }, []);

  return (
    <WebSocketContext.Provider
      value={{
        connected,
        connectionState,
        lastError,
        subscribe,
        unsubscribe,
        subscribeToAlerts,
        subscribeToMetrics,
        subscribeToTopic,
        lastMessage,
        reconnectAttempt,
        lastErrorMessage,
      }}
    >
      {children}
    </WebSocketContext.Provider>
  );
};

export const useWebSocketContext = (): WebSocketContextType => {
  const context = useContext(WebSocketContext);
  if (context === undefined) {
    throw new Error('useWebSocketContext must be used within a WebSocketProvider');
  }
  return context;
};
