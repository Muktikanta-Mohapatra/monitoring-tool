import styled from 'styled-components';
import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  PieChart, Pie, Cell, ResponsiveContainer, AreaChart, Area, XAxis, YAxis, Tooltip, CartesianGrid,
  LineChart, Line, BarChart, Bar, ScatterChart, Scatter, ComposedChart,
  RadarChart, Radar, PolarGrid, PolarAngleAxis, PolarRadiusAxis,
  Treemap, FunnelChart, Funnel, LabelList
} from 'recharts';
import { Spinner } from '../UI';
import { api } from '../../utils/api';
import { useWebSocket } from '../../hooks/useWebSocket';

const POLLING_INTERVAL = 30000;

type TimeRange = '1d' | '1w' | '1m' | '1y';
type Granularity = '1s' | '1m' | '1h' | '1d' | '1M';
type ChartType = 'area' | 'line' | 'bar' | 'scatter' | 'composed' | 'step' | 'radar' | 'treemap' | 'funnel' | 'stacked';

const TIME_RANGE_OPTIONS: { value: TimeRange; label: string }[] = [
  { value: '1d', label: '1 Day' },
  { value: '1w', label: '1 Week' },
  { value: '1m', label: '1 Month' },
  { value: '1y', label: '1 Year' },
];

const GRANULARITY_OPTIONS: Record<TimeRange, { value: Granularity; label: string }[]> = {
  '1d': [
    { value: '1s', label: '1 Second' },
    { value: '1m', label: '1 Minute' },
    { value: '1h', label: '1 Hour' },
  ],
  '1w': [
    { value: '1s', label: '1 Second' },
    { value: '1m', label: '1 Minute' },
    { value: '1h', label: '1 Hour' },
    { value: '1d', label: '1 Day' },
  ],
  '1m': [
    { value: '1s', label: '1 Second' },
    { value: '1m', label: '1 Minute' },
    { value: '1h', label: '1 Hour' },
    { value: '1d', label: '1 Day' },
  ],
  '1y': [
    { value: '1s', label: '1 Second' },
    { value: '1m', label: '1 Minute' },
    { value: '1h', label: '1 Hour' },
    { value: '1d', label: '1 Day' },
    { value: '1M', label: '1 Month' },
  ],
};

const CHART_TYPE_OPTIONS: { value: ChartType; label: string }[] = [
  { value: 'area', label: 'Area Chart' },
  { value: 'line', label: 'Line Chart' },
  { value: 'bar', label: 'Bar Chart' },
  { value: 'scatter', label: 'Scatter Plot' },
  { value: 'composed', label: 'Composed Chart' },
  { value: 'step', label: 'Step Chart' },
  { value: 'radar', label: 'Radar Chart' },
  { value: 'treemap', label: 'Treemap' },
  { value: 'funnel', label: 'Funnel Chart' },
  { value: 'stacked', label: 'Stacked Bar' },
];

const ChartCard = styled.div`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-radius: ${({ theme }) => theme.radius.md};
  padding: ${({ theme }) => theme.spacing.lg};
`;

const ChartHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: ${({ theme }) => theme.spacing.lg};
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.spacing.sm};
`;

const ChartControls = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
  flex-wrap: wrap;
`;

const ChartSelect = styled.select`
  background: ${({ theme }) => theme.colors.bg.tertiary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-radius: ${({ theme }) => theme.radius.sm};
  color: ${({ theme }) => theme.colors.text.primary};
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
  outline: none;
  min-width: 100px;

  &:hover {
    border-color: ${({ theme }) => theme.colors.border.hover};
  }

  &:focus {
    border-color: ${({ theme }) => theme.colors.accent.primary};
  }

  option {
    background: ${({ theme }) => theme.colors.bg.secondary};
    color: ${({ theme }) => theme.colors.text.primary};
  }
`;

const ChartTitle = styled.h3`
  font-size: 16px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};
  margin: 0;
`;

const ChartLegend = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.lg};
  margin-top: ${({ theme }) => theme.spacing.md};
`;

const LegendItem = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
  font-size: 12px;
  color: ${({ theme }) => theme.colors.text.secondary};
`;

const LegendDot = styled.div<{ $color: string }>`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: ${({ $color }) => $color};
`;

const LoadingState = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
`;

const EmptyState = styled.div`
  display: flex;
  justify-content: center;
  align-items: center;
  height: 200px;
  color: ${({ theme }) => theme.colors.text.tertiary};
  font-size: 14px;
`;

const COLORS = ['#3DCC71', '#FF5459', '#F0AD4E', '#5AB1D1'];

interface HealthData {
  name: string;
  value: number;
  [key: string]: string | number;
}

interface LogsData {
  time: string;
  logs: number;
}

export const SystemHealthChart: React.FC = () => {
  const [healthData, setHealthData] = useState<HealthData[]>([
    { name: 'Healthy', value: 0 },
    { name: 'Warning', value: 0 },
    { name: 'Critical', value: 0 },
  ]);
  const [initialLoading, setInitialLoading] = useState(true);
  const { connected, subscribeToTopic } = useWebSocket();
  const isInitialLoadRef = React.useRef(true);

  const loadHealthData = useCallback(async () => {
    try {
      if (isInitialLoadRef.current) {
        setInitialLoading(true);
      }
      const forwarders = await api.getForwarders();
      const active = forwarders.filter(f => f.status === 'ACTIVE').length;
      const warning = forwarders.filter(f => f.status === 'WARNING').length;
      const inactive = forwarders.filter(f => f.status === 'INACTIVE' || f.status === 'OFFLINE').length;

      const total = active + warning + inactive;
      if (total > 0) {
        setHealthData([
          { name: 'Healthy', value: Math.round((active / total) * 100) },
          { name: 'Warning', value: Math.round((warning / total) * 100) },
          { name: 'Critical', value: Math.round((inactive / total) * 100) },
        ]);
      } else {
        setHealthData([
          { name: 'Healthy', value: 100 },
          { name: 'Warning', value: 0 },
          { name: 'Critical', value: 0 },
        ]);
      }
    } catch (err) {
      console.error('Failed to load health data:', err);
      setHealthData([
        { name: 'Healthy', value: 0 },
        { name: 'Warning', value: 0 },
        { name: 'Critical', value: 100 },
      ]);
    } finally {
      setInitialLoading(false);
      isInitialLoadRef.current = false;
    }
  }, []);

  useEffect(() => {
    loadHealthData();
  }, [loadHealthData]);

  useEffect(() => {
    const interval = setInterval(loadHealthData, POLLING_INTERVAL);
    return () => clearInterval(interval);
  }, [loadHealthData]);

  useEffect(() => {
    if (!connected) return;

    const unsubscribeDashboard = subscribeToTopic('/topic/dashboard', (message) => {
      if (message.type === 'dashboard' || message.type === 'metrics') {
        loadHealthData();
      }
    });

    const unsubscribeEvents = subscribeToTopic('/topic/events', (message) => {
      if (message.type === 'event' && message.action === 'forwarder_update') {
        loadHealthData();
      }
    });

    return () => {
      unsubscribeDashboard();
      unsubscribeEvents();
    };
  }, [connected, subscribeToTopic, loadHealthData]);

  if (initialLoading) {
    return (
      <ChartCard>
        <ChartHeader>
          <ChartTitle>System Health</ChartTitle>
        </ChartHeader>
        <LoadingState>
          <Spinner />
        </LoadingState>
      </ChartCard>
    );
  }

  return (
    <ChartCard>
      <ChartHeader>
        <ChartTitle>System Health</ChartTitle>
      </ChartHeader>
      <ResponsiveContainer width="100%" height={200}>
        <PieChart>
          <Pie
            data={healthData as Array<{name: string; value: number; [key: string]: string | number}>}
            cx="50%"
            cy="50%"
            innerRadius={60}
            outerRadius={80}
            paddingAngle={2}
            dataKey="value"
          >
            {healthData.map((_, index) => (
              <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
            ))}
          </Pie>
        </PieChart>
      </ResponsiveContainer>
      <ChartLegend>
        {healthData.map((item, index) => (
          <LegendItem key={item.name}>
            <LegendDot $color={COLORS[index]} />
            {item.name}: {item.value}%
          </LegendItem>
        ))}
      </ChartLegend>
    </ChartCard>
  );
};

const getTimeRangeMs = (range: TimeRange): number => {
  switch (range) {
    case '1d': return 24 * 60 * 60 * 1000;
    case '1w': return 7 * 24 * 60 * 60 * 1000;
    case '1m': return 30 * 24 * 60 * 60 * 1000;
    case '1y': return 365 * 24 * 60 * 60 * 1000;
  }
};

const getTimeRangeLabel = (range: TimeRange): string => {
  switch (range) {
    case '1d': return '24h';
    case '1w': return '7d';
    case '1m': return '30d';
    case '1y': return '1yr';
  }
};

const formatTimeLabel = (date: Date, granularity: Granularity): string => {
  switch (granularity) {
    case '1s':
      return date.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
    case '1m':
      return date.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit' });
    case '1h':
      return date.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit' }) + ':00';
    case '1d':
      return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    case '1M':
      return date.toLocaleDateString('en-US', { month: 'short', year: '2-digit' });
  }
};

const getGranularityMs = (granularity: Granularity): number => {
  switch (granularity) {
    case '1s': return 1000;
    case '1m': return 60 * 1000;
    case '1h': return 60 * 60 * 1000;
    case '1d': return 24 * 60 * 60 * 1000;
    case '1M': return 30 * 24 * 60 * 60 * 1000;
  }
};

export const LogsOverTimeChart: React.FC = () => {
  const [logsData, setLogsData] = useState<LogsData[]>([]);
  const [initialLoading, setInitialLoading] = useState(true);
  const [timeRange, setTimeRange] = useState<TimeRange>('1d');
  const [granularity, setGranularity] = useState<Granularity>('1h');
  const [chartType, setChartType] = useState<ChartType>('area');
  const { connected, subscribeToTopic } = useWebSocket();
  const isInitialLoadRef = React.useRef(true);

  const availableGranularities = useMemo(() => GRANULARITY_OPTIONS[timeRange], [timeRange]);

  useEffect(() => {
    const validGranularities = GRANULARITY_OPTIONS[timeRange].map(g => g.value);
    if (!validGranularities.includes(granularity)) {
      setGranularity(validGranularities[validGranularities.length - 1]);
    }
  }, [timeRange, granularity]);

  const loadLogsData = useCallback(async () => {
    try {
      if (isInitialLoadRef.current) {
        setInitialLoading(true);
      }
      const endTime = new Date();
      const startTime = new Date(endTime.getTime() - getTimeRangeMs(timeRange));
      const timeRangeMs = getTimeRangeMs(timeRange);

      let events: Array<{ timestamp: string }> = [];
      
      try {
        const response = await api.searchEvents({
          startTime: startTime.toISOString(),
          endTime: endTime.toISOString(),
          page: 0,
          pageSize: 5000,
        });
        events = response?.content || [];
      } catch {
        const recentLogs = await api.getRecentLogs(1000);
        events = (recentLogs || []).filter(log => {
          const logTime = new Date(log.timestamp).getTime();
          return logTime >= startTime.getTime() && logTime <= endTime.getTime();
        });
      }

      const granularityMs = getGranularityMs(granularity);
      const totalPossibleBuckets = Math.ceil(timeRangeMs / granularityMs);
      const maxBuckets = 50;
      const effectiveGranularityMs = totalPossibleBuckets > maxBuckets 
        ? Math.ceil(timeRangeMs / maxBuckets)
        : granularityMs;
      
      const numBuckets = Math.ceil(timeRangeMs / effectiveGranularityMs);
      const buckets: number[] = new Array(numBuckets).fill(0);
      const bucketTimestamps: number[] = [];
      
      for (let i = 0; i < numBuckets; i++) {
        bucketTimestamps.push(startTime.getTime() + (i * effectiveGranularityMs));
      }

      events.forEach((event) => {
        if (!event.timestamp) return;
        const eventTime = new Date(event.timestamp).getTime();
        if (eventTime >= startTime.getTime() && eventTime <= endTime.getTime()) {
          const bucketIndex = Math.floor((eventTime - startTime.getTime()) / effectiveGranularityMs);
          if (bucketIndex >= 0 && bucketIndex < numBuckets) {
            buckets[bucketIndex]++;
          }
        }
      });

      const dataArray: LogsData[] = bucketTimestamps.map((timestamp, index) => ({
        time: formatTimeLabel(new Date(timestamp), granularity),
        logs: buckets[index],
      }));

      setLogsData(dataArray);
    } catch (err) {
      console.error('Failed to load logs data:', err);
      setLogsData([]);
    } finally {
      setInitialLoading(false);
      isInitialLoadRef.current = false;
    }
  }, [timeRange, granularity]);

  useEffect(() => {
    isInitialLoadRef.current = true;
    loadLogsData();
  }, [loadLogsData]);

  useEffect(() => {
    const interval = setInterval(loadLogsData, POLLING_INTERVAL);
    return () => clearInterval(interval);
  }, [loadLogsData]);

  useEffect(() => {
    if (!connected) return;

    const unsubscribeEvents = subscribeToTopic('/topic/events', (message) => {
      if (message.type === 'event' && 'data' in message && message.data) {
        loadLogsData();
      }
    });

    return () => {
      unsubscribeEvents();
    };
  }, [connected, subscribeToTopic, loadLogsData]);

  const handleTimeRangeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setTimeRange(e.target.value as TimeRange);
  };

  const handleGranularityChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setGranularity(e.target.value as Granularity);
  };

  const handleChartTypeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setChartType(e.target.value as ChartType);
  };

  const tooltipStyle = {
    background: '#1F2121',
    border: '1px solid #26282A',
    borderRadius: '6px',
    color: '#FFFFFF',
  };

  const renderChart = () => {
    switch (chartType) {
      case 'line':
        return (
          <LineChart data={logsData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#26282A" />
            <XAxis dataKey="time" stroke="#616363" fontSize={10} tickLine={false} interval="preserveStartEnd" />
            <YAxis stroke="#616363" fontSize={12} tickLine={false} axisLine={false} />
            <Tooltip contentStyle={tooltipStyle} />
            <Line type="monotone" dataKey="logs" stroke="#32B8C6" strokeWidth={2} dot={false} />
          </LineChart>
        );

      case 'bar':
        return (
          <BarChart data={logsData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#26282A" />
            <XAxis dataKey="time" stroke="#616363" fontSize={10} tickLine={false} interval="preserveStartEnd" />
            <YAxis stroke="#616363" fontSize={12} tickLine={false} axisLine={false} />
            <Tooltip contentStyle={tooltipStyle} />
            <Bar dataKey="logs" fill="#32B8C6" radius={[4, 4, 0, 0]} />
          </BarChart>
        );

      case 'scatter':
        return (
          <ScatterChart>
            <CartesianGrid strokeDasharray="3 3" stroke="#26282A" />
            <XAxis dataKey="time" stroke="#616363" fontSize={10} tickLine={false} name="Time" />
            <YAxis dataKey="logs" stroke="#616363" fontSize={12} tickLine={false} axisLine={false} name="Logs" />
            <Tooltip contentStyle={tooltipStyle} />
            <Scatter data={logsData} fill="#32B8C6" />
          </ScatterChart>
        );

      case 'composed':
        return (
          <ComposedChart data={logsData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#26282A" />
            <XAxis dataKey="time" stroke="#616363" fontSize={10} tickLine={false} interval="preserveStartEnd" />
            <YAxis stroke="#616363" fontSize={12} tickLine={false} axisLine={false} />
            <Tooltip contentStyle={tooltipStyle} />
            <Bar dataKey="logs" fill="#32B8C6" opacity={0.4} radius={[4, 4, 0, 0]} />
            <Line type="monotone" dataKey="logs" stroke="#FF5459" strokeWidth={2} dot={false} />
          </ComposedChart>
        );

      case 'step':
        return (
          <LineChart data={logsData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#26282A" />
            <XAxis dataKey="time" stroke="#616363" fontSize={10} tickLine={false} interval="preserveStartEnd" />
            <YAxis stroke="#616363" fontSize={12} tickLine={false} axisLine={false} />
            <Tooltip contentStyle={tooltipStyle} />
            <Line type="stepAfter" dataKey="logs" stroke="#32B8C6" strokeWidth={2} dot={false} />
          </LineChart>
        );

      case 'radar':
        return (
          <RadarChart data={logsData.slice(0, 12)} cx="50%" cy="50%" outerRadius="70%">
            <PolarGrid stroke="#26282A" />
            <PolarAngleAxis dataKey="time" stroke="#616363" fontSize={10} />
            <PolarRadiusAxis stroke="#616363" fontSize={10} />
            <Tooltip contentStyle={tooltipStyle} />
            <Radar dataKey="logs" stroke="#32B8C6" fill="#32B8C6" fillOpacity={0.3} />
          </RadarChart>
        );

      case 'treemap':
        const treemapData = logsData.filter(d => d.logs > 0).map(d => ({
          name: d.time,
          size: d.logs,
          fill: '#32B8C6',
        }));
        return (
          <Treemap
            data={treemapData}
            dataKey="size"
            aspectRatio={4 / 3}
            stroke="#1F2121"
            fill="#32B8C6"
          />
        );

      case 'funnel':
        const funnelData = [...logsData]
          .sort((a, b) => b.logs - a.logs)
          .slice(0, 10)
          .map(d => ({ name: d.time, value: d.logs, fill: '#32B8C6' }));
        return (
          <FunnelChart>
            <Tooltip contentStyle={tooltipStyle} />
            <Funnel dataKey="value" data={funnelData} isAnimationActive>
              <LabelList position="right" fill="#fff" stroke="none" dataKey="name" fontSize={10} />
            </Funnel>
          </FunnelChart>
        );

      case 'stacked':
        return (
          <BarChart data={logsData}>
            <CartesianGrid strokeDasharray="3 3" stroke="#26282A" />
            <XAxis dataKey="time" stroke="#616363" fontSize={10} tickLine={false} interval="preserveStartEnd" />
            <YAxis stroke="#616363" fontSize={12} tickLine={false} axisLine={false} />
            <Tooltip contentStyle={tooltipStyle} />
            <Bar dataKey="logs" stackId="a" fill="#32B8C6" radius={[0, 0, 0, 0]} />
            <Bar dataKey="logs" stackId="a" fill="#3DCC71" opacity={0.5} radius={[4, 4, 0, 0]} />
          </BarChart>
        );

      case 'area':
      default:
        return (
          <AreaChart data={logsData}>
            <defs>
              <linearGradient id="colorLogs" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#32B8C6" stopOpacity={0.3} />
                <stop offset="95%" stopColor="#32B8C6" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#26282A" />
            <XAxis dataKey="time" stroke="#616363" fontSize={10} tickLine={false} interval="preserveStartEnd" />
            <YAxis stroke="#616363" fontSize={12} tickLine={false} axisLine={false} />
            <Tooltip contentStyle={tooltipStyle} />
            <Area type="monotone" dataKey="logs" stroke="#32B8C6" fillOpacity={1} fill="url(#colorLogs)" />
          </AreaChart>
        );
    }
  };

  if (initialLoading) {
    return (
      <ChartCard>
        <ChartHeader>
          <ChartTitle>Logs Over Time ({getTimeRangeLabel(timeRange)})</ChartTitle>
          <ChartControls>
            <ChartSelect value={timeRange} onChange={handleTimeRangeChange}>
              {TIME_RANGE_OPTIONS.map(opt => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </ChartSelect>
            <ChartSelect value={granularity} onChange={handleGranularityChange}>
              {availableGranularities.map(opt => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </ChartSelect>
            <ChartSelect value={chartType} onChange={handleChartTypeChange}>
              {CHART_TYPE_OPTIONS.map(opt => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </ChartSelect>
          </ChartControls>
        </ChartHeader>
        <LoadingState>
          <Spinner />
        </LoadingState>
      </ChartCard>
    );
  }

  if (logsData.length === 0) {
    return (
      <ChartCard>
        <ChartHeader>
          <ChartTitle>Logs Over Time ({getTimeRangeLabel(timeRange)})</ChartTitle>
          <ChartControls>
            <ChartSelect value={timeRange} onChange={handleTimeRangeChange}>
              {TIME_RANGE_OPTIONS.map(opt => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </ChartSelect>
            <ChartSelect value={granularity} onChange={handleGranularityChange}>
              {availableGranularities.map(opt => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </ChartSelect>
            <ChartSelect value={chartType} onChange={handleChartTypeChange}>
              {CHART_TYPE_OPTIONS.map(opt => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </ChartSelect>
          </ChartControls>
        </ChartHeader>
        <EmptyState>No log data available</EmptyState>
      </ChartCard>
    );
  }

  return (
    <ChartCard>
      <ChartHeader>
        <ChartTitle>Logs Over Time ({getTimeRangeLabel(timeRange)})</ChartTitle>
        <ChartControls>
          <ChartSelect value={timeRange} onChange={handleTimeRangeChange}>
            {TIME_RANGE_OPTIONS.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </ChartSelect>
          <ChartSelect value={granularity} onChange={handleGranularityChange}>
            {availableGranularities.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </ChartSelect>
          <ChartSelect value={chartType} onChange={handleChartTypeChange}>
            {CHART_TYPE_OPTIONS.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </ChartSelect>
        </ChartControls>
      </ChartHeader>
      <ResponsiveContainer width="100%" height={200}>
        {renderChart()}
      </ResponsiveContainer>
    </ChartCard>
  );
};
