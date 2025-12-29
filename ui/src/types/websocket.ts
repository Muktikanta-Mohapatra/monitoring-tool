/**
 * WebSocket message types matching Java backend definitions
 */

export const VERSION = '1.0';

/**
 * Base message type with common fields
 */
export interface GenericMessage {
  type: string;
  version: string;
  requestId: string;
  timestamp: string;
  action?: string;
  data?: unknown;
}

/**
 * Subscription message for subscribing/unsubscribing to log streams
 */
export interface SubscriptionMessage extends GenericMessage {
  subscriptionId?: string;
  action: 'SUBSCRIBE' | 'UNSUBSCRIBE' | 'UPDATE' | 'LIST';
  query?: string;
  fields?: string[];
  batchSize?: number;
  indexes?: string[];
  filters?: Record<string, unknown>;
}

/**
 * Alert notification message
 */
export interface AlertMessage extends GenericMessage {
  alertId: string;
  alertName: string;
  severity: 'CRITICAL' | 'ERROR' | 'WARNING' | 'INFO';
  status: 'TRIGGERED' | 'RESOLVED' | 'ACKNOWLEDGED' | 'ESCALATED';
  triggeredAt: string;
  message: string;
  eventCount?: number;
  details?: Record<string, unknown>;
}

/**
 * Metrics message for reporting metrics
 */
export interface MetricsMessage extends GenericMessage {
  metricType: string;
  collectedAt: string;
  value?: number;
  tags?: Record<string, unknown>;
  aggregatedMetrics?: Record<string, number>;
}

/**
 * Error message for error responses
 */
export interface ErrorMessage extends GenericMessage {
  errorCode: string;
  errorMessage: string;
  severity: 'CRITICAL' | 'ERROR' | 'WARNING' | 'INFO';
  details?: Record<string, unknown>;
  originalRequestId?: string;
  context?: string;
}

/**
 * Acknowledgement message for successful operations
 */
export interface AckMessage extends GenericMessage {
  originalRequestId?: string;
  status: 'SUCCESS' | 'FAILED' | 'PARTIAL';
  processedCount?: number;
  message?: string;
}

/**
 * Query message for querying logs
 */
export interface QueryMessage extends GenericMessage {
  queryId: string;
  queryText: string;
  queryType: 'SEARCH' | 'AGGREGATE' | 'HISTOGRAM' | 'STATS' | 'FACET';
  limit?: number;
  offset?: number;
  executionTimeMs?: number;
  totalResults?: number;
  results?: Array<Record<string, unknown>>;
}

/**
 * Union type for all WebSocket messages
 */
export type WebSocketMessage =
  | GenericMessage
  | SubscriptionMessage
  | AlertMessage
  | MetricsMessage
  | ErrorMessage
  | AckMessage
  | QueryMessage;

/**
 * Error code constants
 */
export const ErrorCode = {
  VALIDATION_FAILED: 'VALIDATION_FAILED',
  QUERY_INVALID: 'QUERY_INVALID',
  SUBSCRIPTION_LIMIT_EXCEEDED: 'SUBSCRIPTION_LIMIT_EXCEEDED',
  UNAUTHORIZED: 'UNAUTHORIZED',
  SUBSCRIPTION_NOT_FOUND: 'SUBSCRIPTION_NOT_FOUND',
  INTERNAL_ERROR: 'INTERNAL_ERROR',
  TIMEOUT: 'TIMEOUT',
  RESOURCE_NOT_FOUND: 'RESOURCE_NOT_FOUND',
  SQL_INJECTION_DETECTED: 'SQL_INJECTION_DETECTED',
  INVALID_MESSAGE_FORMAT: 'INVALID_MESSAGE_FORMAT',
} as const;

/**
 * Error severity constants
 */
export const ErrorSeverity = {
  CRITICAL: 'CRITICAL',
  ERROR: 'ERROR',
  WARNING: 'WARNING',
  INFO: 'INFO',
} as const;
