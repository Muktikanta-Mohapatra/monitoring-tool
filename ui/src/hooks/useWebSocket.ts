import { useWebSocketContext } from '../context';
import type { WebSocketMessage } from '../types/websocket';

interface UseWebSocketReturn {
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

export function useWebSocket(): UseWebSocketReturn {
  return useWebSocketContext();
}
