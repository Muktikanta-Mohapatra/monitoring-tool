export interface ApiResponse<T = unknown> {
  success: boolean;
  message: string;
  code: string;
  data: T;
  timestamp: string;
}

export interface EventDTO {
  id?: number;
  timestamp: string;
  sourceId?: number;
  sourceName?: string;
  sourcetype: string;
  rawData?: string;
  rawMessage?: string;
  severity: string;
  hostId?: number;
  indexId?: number;
  batchId?: number;
  parsedFields?: Record<string, unknown>;
  enrichedFields?: Record<string, unknown>;
  indexedFields?: Record<string, unknown>;
  parseDurationUs?: number;
  detectedFormat?: string;
  elasticsearchId?: string;
  createdAt?: string;
  updatedAt?: string;
  isIndexed?: boolean;
  isEnriched?: boolean;
  forwarderId?: string;
}

export interface EventBatchDTO {
  forwarderId: string;
  apiKey?: string;
  events: EventDTO[];
  batchId?: number;
  batchSize?: number;
  nextCheckpointOffset?: number;
  nextCheckpointLineCount?: number;
}

export interface ForwarderDTO {
  id: string;
  name: string;
  hostname?: string;
  ipAddress?: string;
  version?: string;
  status: 'ACTIVE' | 'INACTIVE' | 'WARNING' | 'OFFLINE';
  lastHeartbeat?: string;
  createdAt?: string;
  updatedAt?: string;
  totalEventsProcessed?: number;
  cpuUsagePercent?: number;
  memoryUsagePercent?: number;
  queueDepth?: number;
  processId?: number;
  apiKey?: string;
  enabled?: boolean;
  description?: string;
  configPath?: string;
  maxBatchSize?: number;
  batchTimeoutMs?: number;
}

export interface ForwarderMetricsDTO {
  id?: number;
  forwarderId: string;
  timestamp: string;
  eventsProcessed?: number;
  eventsDropped?: number;
  cpuUsagePercent?: number;
  memoryUsageBytes?: number;
  totalMemoryBytes?: number;
  queueDepth?: number;
  uptime?: number;
  throughputEventsPerSecond?: number;
  averageLatencyMs?: number;
  errorCount?: number;
  activeConnections?: number;
  diskSpaceUsedBytes?: number;
  diskSpaceAvailableBytes?: number;
  cpuCores?: number;
}

export interface AlertDTO {
  id?: number;
  alertRuleId?: number;
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
  triggeredAt: string;
  resolvedAt?: string;
  acknowledgedAt?: string;
  acknowledgedBy?: string;
  triggerMessage?: string;
  resolutionMessage?: string;
  triggerCondition?: Record<string, unknown>;
  notificationCount?: number;
  notificationDelivered?: boolean;
  notificationChannel?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UserDTO {
  id?: number;
  username: string;
  email: string;
  role: 'ADMIN' | 'OPERATOR' | 'VIEWER';
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
  lastLogin?: string;
}

export interface LoginRequestDTO {
  username: string;
  password: string;
}

export interface LoginResponseDTO {
  accessToken: string;
  refreshToken?: string;
  tokenType: string;
  expiresIn: number;
  user?: UserDTO;
}

export interface DashboardSummary {
  total_events_last_24h: number;
  total_forwarders: number;
  active_forwarders: number;
  unacknowledged_alerts: number;
  system_metrics: SystemMetrics;
  app_metrics: AppMetrics;
}

export interface SystemMetrics {
  cpuUsage?: number;
  memoryUsage?: number;
  diskUsage?: number;
  uptime?: number;
}

export interface AppMetrics {
  eventsPerSecond?: number;
  averageLatency?: number;
  errorRate?: number;
  activeConnections?: number;
}

export interface SearchQuery {
  query?: string;
  startTime?: string;
  endTime?: string;
  sourcetype?: string;
  severity?: string;
  page?: number;
  pageSize?: number;
}

export interface PagedResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}
