import { useState, useEffect, useCallback } from 'react';
import { api } from '../utils/api';
import type {
  EventDTO,
  ForwarderDTO,
  AlertDTO,
  DashboardSummary,
  SearchQuery,
  PagedResult,
} from '../types';

interface UseApiState<T> {
  data: T | null;
  loading: boolean;
  error: Error | null;
  refetch: () => Promise<void>;
}

function useApiCall<T>(
  fetchFn: () => Promise<T>,
  deps: unknown[] = []
): UseApiState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const fetch = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchFn();
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
    } finally {
      setLoading(false);
    }
  }, deps);

  useEffect(() => {
    fetch();
  }, [fetch]);

  return { data, loading, error, refetch: fetch };
}

export function useDashboardSummary(): UseApiState<DashboardSummary> {
  return useApiCall(() => api.getDashboardSummary(), []);
}

export function useRecentLogs(limit: number = 100): UseApiState<EventDTO[]> {
  return useApiCall(() => api.getRecentLogs(limit), [limit]);
}

export function useRecentEvents(limit: number = 100): UseApiState<EventDTO[]> {
  return useApiCall(() => api.getRecentEvents(limit), [limit]);
}

export function useSearchEvents(query: SearchQuery): UseApiState<PagedResult<EventDTO>> {
  return useApiCall(
    () => api.searchEvents(query),
    [query.startTime, query.endTime, query.sourcetype, query.severity, query.page, query.pageSize]
  );
}

export function useForwarders(): UseApiState<ForwarderDTO[]> {
  return useApiCall(() => api.getForwarders(), []);
}

export function useForwarder(id: string): UseApiState<ForwarderDTO> {
  return useApiCall(() => api.getForwarderById(id), [id]);
}

export function useAlerts(
  status?: string,
  severity?: string,
  page: number = 0
): UseApiState<AlertDTO[]> {
  return useApiCall(() => api.getAlerts(status, severity, page), [status, severity, page]);
}

export function useSystemHealth(): UseApiState<Record<string, unknown>> {
  return useApiCall(() => api.getSystemHealth(), []);
}

export { api };
