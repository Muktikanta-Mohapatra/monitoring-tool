/**
 * WebSocket utilities for managing connections and message handling
 */

export interface ExponentialBackoffConfig {
  initialDelayMs: number;
  maxDelayMs: number;
  multiplier: number;
  maxAttempts: number | null;
}

/**
 * Default exponential backoff configuration
 * Strategy: 1s → 2s → 4s → 8s → 16s → 30s (capped at 30s)
 */
export const DEFAULT_BACKOFF_CONFIG: ExponentialBackoffConfig = {
  initialDelayMs: 1000,
  maxDelayMs: 30000,
  multiplier: 2,
  maxAttempts: null,
};

/**
 * Calculates the next reconnection delay based on attempt number
 * @param attemptNumber - Zero-indexed attempt number
 * @param config - Backoff configuration
 * @returns Delay in milliseconds
 */
export function calculateBackoffDelay(
  attemptNumber: number,
  config: ExponentialBackoffConfig = DEFAULT_BACKOFF_CONFIG
): number {
  const exponentialDelay = config.initialDelayMs * Math.pow(config.multiplier, attemptNumber);
  const cappedDelay = Math.min(exponentialDelay, config.maxDelayMs);
  return cappedDelay;
}

/**
 * Determines if reconnection should be attempted
 * @param attemptNumber - Zero-indexed attempt number
 * @param config - Backoff configuration
 * @returns Boolean indicating if reconnection should be attempted
 */
export function shouldAttemptReconnect(
  attemptNumber: number,
  config: ExponentialBackoffConfig = DEFAULT_BACKOFF_CONFIG
): boolean {
  if (config.maxAttempts === null) {
    return true;
  }
  return attemptNumber < config.maxAttempts;
}

/**
 * Format delay in milliseconds to a human-readable string
 * @param delayMs - Delay in milliseconds
 * @returns Formatted string (e.g., "1s", "2.5s")
 */
export function formatDelay(delayMs: number): string {
  const seconds = delayMs / 1000;
  return `${seconds.toFixed(1)}s`;
}

/**
 * WebSocket connection states
 */
export const WebSocketState = {
  CONNECTING: 'connecting',
  CONNECTED: 'connected',
  DISCONNECTED: 'disconnected',
  ERROR: 'error',
  CLOSED: 'closed',
} as const;

export type WebSocketState = typeof WebSocketState[keyof typeof WebSocketState];

/**
 * Extended WebSocket with metadata
 */
export interface ManagedWebSocket {
  socket: WebSocket | null;
  state: WebSocketState;
  attemptNumber: number;
  lastError: Error | null;
  lastConnectTime: number | null;
  messageCount: number;
}

/**
 * Create initial managed WebSocket state
 */
export function createManagedWebSocket(): ManagedWebSocket {
  return {
    socket: null,
    state: 'disconnected',
    attemptNumber: 0,
    lastError: null,
    lastConnectTime: null,
    messageCount: 0,
  };
}

/**
 * Validate WebSocket message format
 * @param data - Message data to validate
 * @returns Boolean indicating if data is valid JSON
 */
export function isValidWebSocketMessage(data: string): boolean {
  try {
    JSON.parse(data);
    return true;
  } catch {
    return false;
  }
}

/**
 * Format WebSocket error message
 * @param error - Error object or event
 * @returns Formatted error message
 */
export function formatWebSocketError(error: Event | Error | null): string {
  if (!error) return 'Unknown WebSocket error';

  if (error instanceof CloseEvent) {
    return `WebSocket closed (code: ${error.code}, reason: ${error.reason || 'unknown'})`;
  }

  if (error instanceof Error) {
    return error.message;
  }

  if (error instanceof Event) {
    return `WebSocket error: ${error.type}`;
  }

  return 'Unknown error type';
}

/**
 * Get WebSocket close code description
 * @param code - Close code from CloseEvent
 * @returns Description of close code
 */
export function getCloseCodeDescription(code: number): string {
  const codeMap: Record<number, string> = {
    1000: 'Normal Closure',
    1001: 'Going Away',
    1002: 'Protocol Error',
    1003: 'Unsupported Data',
    1005: 'No Status Rcvd',
    1006: 'Abnormal Closure',
    1007: 'Invalid frame payload data',
    1008: 'Policy Violation',
    1009: 'Message too big',
    1010: 'Mandatory Ext.',
    1011: 'Internal Server Error',
    1012: 'Service Restart',
    1013: 'Try Again Later',
    1014: 'Bad Gateway',
    1015: 'TLS Handshake',
  };

  return codeMap[code] || `Unknown Close Code (${code})`;
}
