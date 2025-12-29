import type {
  ApiResponse,
  EventDTO,
  EventBatchDTO,
  ForwarderDTO,
  AlertDTO,
  UserDTO,
  LoginRequestDTO,
  LoginResponseDTO,
  DashboardSummary,
  SearchQuery,
  PagedResult,
} from '../types';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

class ApiClient {
  private baseUrl: string;
  private token: string | null = null;

  constructor(baseUrl: string) {
    this.baseUrl = baseUrl;
    this.token = localStorage.getItem('accessToken');
  }

  setToken(token: string | null) {
    this.token = token;
    if (token) {
      localStorage.setItem('accessToken', token);
    } else {
      localStorage.removeItem('accessToken');
    }
  }

  getToken(): string | null {
    this.token = localStorage.getItem('accessToken');
    return this.token;
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<ApiResponse<T>> {
    const url = `${this.baseUrl}${endpoint}`;
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
      ...options.headers,
    };

    const token = this.getToken();
    if (token) {
      (headers as Record<string, string>)['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(url, {
      ...options,
      headers,
    });

    if (!response.ok) {
      if (response.status === 401) {
        this.setToken(null);
        window.dispatchEvent(new CustomEvent('auth:logout'));
      }
      const error = await response.json().catch(() => ({
        success: false,
        message: `HTTP ${response.status}: ${response.statusText}`,
        code: 'HTTP_ERROR',
        data: null,
        timestamp: new Date().toISOString(),
      }));
      throw error;
    }

    return response.json();
  }

  async login(credentials: LoginRequestDTO): Promise<LoginResponseDTO> {
    const response = await this.request<LoginResponseDTO>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(credentials),
    });
    if (response.success && response.data.accessToken) {
      this.setToken(response.data.accessToken);
    }
    return response.data;
  }

  async logout(): Promise<void> {
    await this.request('/auth/logout', { method: 'POST' }).catch(() => {});
    this.setToken(null);
  }

  async refreshToken(): Promise<LoginResponseDTO> {
    const response = await this.request<LoginResponseDTO>('/auth/refresh', {
      method: 'POST',
    });
    if (response.success && response.data.accessToken) {
      this.setToken(response.data.accessToken);
    }
    return response.data;
  }

  async getCurrentUser(): Promise<string> {
    const response = await this.request<string>('/auth/me');
    return response.data;
  }

  async getDashboardSummary(): Promise<DashboardSummary> {
    const response = await this.request<DashboardSummary>('/dashboard/summary');
    return response.data;
  }

  async getRecentLogs(limit: number = 100): Promise<EventDTO[]> {
    const response = await this.request<EventDTO[]>(`/dashboard/logs/recent?limit=${limit}`);
    return response.data;
  }

  async getSystemHealth(): Promise<Record<string, unknown>> {
    const response = await this.request<Record<string, unknown>>('/dashboard/health');
    return response.data;
  }

  async searchEvents(query: SearchQuery): Promise<PagedResult<EventDTO>> {
    const params = new URLSearchParams();
    if (query.startTime) params.append('startTime', query.startTime);
    if (query.endTime) params.append('endTime', query.endTime);
    if (query.sourcetype) params.append('sourcetype', query.sourcetype);
    if (query.severity) params.append('severity', query.severity);
    if (query.page !== undefined) params.append('page', query.page.toString());
    if (query.pageSize !== undefined) params.append('pageSize', query.pageSize.toString());

    const response = await this.request<PagedResult<EventDTO>>(`/events/search?${params}`);
    return response.data;
  }

  async getEventById(eventId: number): Promise<EventDTO> {
    const response = await this.request<EventDTO>(`/events/${eventId}`);
    return response.data;
  }

  async getRecentEvents(limit: number = 100): Promise<EventDTO[]> {
    const response = await this.request<EventDTO[]>(`/events/recent?limit=${limit}`);
    return response.data;
  }

  async ingestEventBatch(batch: EventBatchDTO): Promise<EventBatchDTO> {
    const response = await this.request<EventBatchDTO>('/events/batch', {
      method: 'POST',
      body: JSON.stringify(batch),
    });
    return response.data;
  }

  async getForwarders(): Promise<ForwarderDTO[]> {
    const response = await this.request<ForwarderDTO[]>('/forwarders');
    return response.data;
  }

  async getForwarderById(id: string): Promise<ForwarderDTO> {
    const response = await this.request<ForwarderDTO>(`/forwarders/${id}`);
    return response.data;
  }

  async registerForwarder(forwarder: ForwarderDTO): Promise<ForwarderDTO> {
    const response = await this.request<ForwarderDTO>('/forwarders', {
      method: 'POST',
      body: JSON.stringify(forwarder),
    });
    return response.data;
  }

  async sendHeartbeat(forwarderId: string): Promise<void> {
    await this.request(`/forwarders/${forwarderId}/heartbeat`, { method: 'POST' });
  }

  async getForwarderMetrics(forwarderId: string): Promise<number> {
    const response = await this.request<number>(`/forwarders/${forwarderId}/metrics`);
    return response.data;
  }

  async getAlerts(status?: string, severity?: string, page: number = 0): Promise<AlertDTO[]> {
    const params = new URLSearchParams();
    if (status) params.append('status', status);
    if (severity) params.append('severity', severity);
    params.append('page', page.toString());

    const response = await this.request<PagedResult<AlertDTO>>(`/alerts?${params}`);
    return response.data?.content || [];
  }

  async createAlert(alert: AlertDTO): Promise<AlertDTO> {
    const response = await this.request<AlertDTO>('/alerts', {
      method: 'POST',
      body: JSON.stringify(alert),
    });
    return response.data;
  }

  async acknowledgeAlert(alertId: number, acknowledgedBy: string): Promise<AlertDTO> {
    const response = await this.request<AlertDTO>(
      `/alerts/${alertId}/acknowledge?acknowledgedBy=${encodeURIComponent(acknowledgedBy)}`,
      { method: 'POST' }
    );
    return response.data;
  }

  async resolveAlert(alertId: number, message: string): Promise<AlertDTO> {
    const response = await this.request<AlertDTO>(
      `/alerts/${alertId}/resolve?message=${encodeURIComponent(message)}`,
      { method: 'POST' }
    );
    return response.data;
  }

  async getUsers(): Promise<UserDTO[]> {
    const response = await this.request<UserDTO[]>('/users');
    return response.data;
  }

  async getUserById(id: number): Promise<UserDTO> {
    const response = await this.request<UserDTO>(`/users/${id}`);
    return response.data;
  }

  async createUser(user: UserDTO): Promise<UserDTO> {
    const response = await this.request<UserDTO>('/users', {
      method: 'POST',
      body: JSON.stringify(user),
    });
    return response.data;
  }

  async updateUser(id: number, user: UserDTO): Promise<UserDTO> {
    const response = await this.request<UserDTO>(`/users/${id}`, {
      method: 'PUT',
      body: JSON.stringify(user),
    });
    return response.data;
  }

  async deleteUser(id: number): Promise<void> {
    await this.request(`/users/${id}`, { method: 'DELETE' });
  }
}

export const api = new ApiClient(API_BASE_URL);
export default api;
