import { z } from 'zod';
import type {
  GenericMessage,
  SubscriptionMessage,
  AlertMessage,
  MetricsMessage,
  ErrorMessage,
  AckMessage,
  QueryMessage,
  WebSocketMessage,
} from '../types/websocket';

/**
 * Zod schemas for WebSocket message validation
 */

const baseMessageSchema = z.object({
  type: z.string(),
  version: z.string(),
  requestId: z.string().max(255),
  timestamp: z.string().datetime().or(z.string()),
});

const genericMessageSchema = baseMessageSchema.strict();

const subscriptionMessageSchema = baseMessageSchema.extend({
  subscriptionId: z.string().optional(),
  action: z.enum(['SUBSCRIBE', 'UNSUBSCRIBE', 'UPDATE', 'LIST']),
  query: z.string().max(10000).optional(),
  fields: z.array(z.string()).max(100).optional(),
  batchSize: z.number().int().min(1).max(10000).optional(),
  indexes: z.array(z.string()).optional(),
  filters: z.record(z.unknown()).optional(),
});

const alertMessageSchema = baseMessageSchema.extend({
  alertId: z.string(),
  alertName: z.string(),
  severity: z.enum(['CRITICAL', 'ERROR', 'WARNING', 'INFO']),
  status: z.enum(['TRIGGERED', 'RESOLVED', 'ACKNOWLEDGED', 'ESCALATED']),
  triggeredAt: z.string(),
  message: z.string(),
  eventCount: z.number().optional(),
  details: z.record(z.unknown()).optional(),
});

const metricsMessageSchema = baseMessageSchema.extend({
  metricType: z.string(),
  collectedAt: z.string(),
  value: z.number().finite().optional(),
  tags: z.record(z.unknown()).optional(),
  aggregatedMetrics: z.record(z.number()).optional(),
});

const errorMessageSchema = baseMessageSchema.extend({
  errorCode: z.string(),
  errorMessage: z.string().max(1000),
  severity: z.enum(['CRITICAL', 'ERROR', 'WARNING', 'INFO']),
  details: z.record(z.unknown()).optional(),
  originalRequestId: z.string().optional(),
  context: z.string().optional(),
});

const ackMessageSchema = baseMessageSchema.extend({
  originalRequestId: z.string().optional(),
  status: z.enum(['SUCCESS', 'FAILED', 'PARTIAL']),
  processedCount: z.number().optional(),
  message: z.string().optional(),
});

const queryMessageSchema = baseMessageSchema.extend({
  queryId: z.string(),
  queryText: z.string(),
  queryType: z.enum(['SEARCH', 'AGGREGATE', 'HISTOGRAM', 'STATS', 'FACET']),
  limit: z.number().optional(),
  offset: z.number().optional(),
  executionTimeMs: z.number().optional(),
  totalResults: z.number().optional(),
  results: z.array(z.record(z.unknown())).optional(),
});

/**
 * Type-safe message dispatcher function
 */
export function validateWebSocketMessage(data: unknown): {
  valid: boolean;
  message?: WebSocketMessage;
  error?: string;
  type?: string;
} {
  try {
    if (typeof data !== 'object' || data === null) {
      return {
        valid: false,
        error: 'Message must be an object',
      };
    }

    const obj = data as Record<string, unknown>;
    const messageType = obj.type;

    switch (messageType) {
      case 'subscription': {
        const result = subscriptionMessageSchema.safeParse(data);
        if (result.success) {
          return {
            valid: true,
            message: result.data as SubscriptionMessage,
            type: 'subscription',
          };
        }
        return {
          valid: false,
          error: result.error.message,
          type: 'subscription',
        };
      }

      case 'alert': {
        const result = alertMessageSchema.safeParse(data);
        if (result.success) {
          return {
            valid: true,
            message: result.data as AlertMessage,
            type: 'alert',
          };
        }
        return {
          valid: false,
          error: result.error.message,
          type: 'alert',
        };
      }

      case 'metrics': {
        const result = metricsMessageSchema.safeParse(data);
        if (result.success) {
          return {
            valid: true,
            message: result.data as MetricsMessage,
            type: 'metrics',
          };
        }
        return {
          valid: false,
          error: result.error.message,
          type: 'metrics',
        };
      }

      case 'error': {
        const result = errorMessageSchema.safeParse(data);
        if (result.success) {
          return {
            valid: true,
            message: result.data as ErrorMessage,
            type: 'error',
          };
        }
        return {
          valid: false,
          error: result.error.message,
          type: 'error',
        };
      }

      case 'ack': {
        const result = ackMessageSchema.safeParse(data);
        if (result.success) {
          return {
            valid: true,
            message: result.data as AckMessage,
            type: 'ack',
          };
        }
        return {
          valid: false,
          error: result.error.message,
          type: 'ack',
        };
      }

      case 'query': {
        const result = queryMessageSchema.safeParse(data);
        if (result.success) {
          return {
            valid: true,
            message: result.data as QueryMessage,
            type: 'query',
          };
        }
        return {
          valid: false,
          error: result.error.message,
          type: 'query',
        };
      }

      case 'ping':
      case 'pong': {
        const result = genericMessageSchema.safeParse(data);
        if (result.success) {
          return {
            valid: true,
            message: result.data as GenericMessage,
            type: messageType,
          };
        }
        return {
          valid: false,
          error: result.error.message,
          type: messageType,
        };
      }

      default:
        return {
          valid: false,
          error: `Unknown message type: ${messageType}`,
          type: messageType as string,
        };
    }
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Unknown validation error';
    return {
      valid: false,
      error: message,
    };
  }
}

/**
 * Generate a unique request ID
 */
export function generateRequestId(): string {
  return Math.random().toString(36).substring(2, 10).toUpperCase();
}

/**
 * Type guard functions for narrowing message types
 */
export function isErrorMessage(msg: WebSocketMessage): msg is ErrorMessage {
  return msg.type === 'error';
}

export function isAckMessage(msg: WebSocketMessage): msg is AckMessage {
  return msg.type === 'ack';
}

export function isSubscriptionMessage(msg: WebSocketMessage): msg is SubscriptionMessage {
  return msg.type === 'subscription';
}

export function isAlertMessage(msg: WebSocketMessage): msg is AlertMessage {
  return msg.type === 'alert';
}

export function isMetricsMessage(msg: WebSocketMessage): msg is MetricsMessage {
  return msg.type === 'metrics';
}

export function isQueryMessage(msg: WebSocketMessage): msg is QueryMessage {
  return msg.type === 'query';
}
