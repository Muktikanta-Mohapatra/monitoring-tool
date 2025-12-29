import styled from 'styled-components';
import React, { useState, useEffect, useCallback } from 'react';
import { Search, Download, AlertCircle } from 'lucide-react';
import { PageHeader, PageTitle, PageSubtitle } from '../components/Common';
import { Button, Select, Badge, Table, TableContainer, TableHead, TableBody, TableRow, TableHeader, TableCell, Pagination, Spinner } from '../components/UI';
import { api } from '../utils/api';
import type { EventDTO, PagedResult, SearchQuery } from '../types';

const SearchSection = styled.div`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-radius: ${({ theme }) => theme.radius.md};
  padding: ${({ theme }) => theme.spacing.lg};
  margin-bottom: ${({ theme }) => theme.spacing.lg};
`;

const SearchBar = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.md};
  margin-bottom: ${({ theme }) => theme.spacing.lg};

  @media (max-width: 768px) {
    flex-direction: column;
  }
`;

const SearchInputWrapper = styled.div`
  flex: 1;
  position: relative;
`;

const SearchInput = styled.input`
  width: 100%;
  height: 48px;
  padding: 0 16px 0 48px;
  background: ${({ theme }) => theme.colors.bg.tertiary};
  border: 1px solid ${({ theme }) => theme.colors.border.light};
  border-radius: ${({ theme }) => theme.radius.md};
  font-size: 15px;
  font-family: ${({ theme }) => theme.typography.monoFamily};
  color: ${({ theme }) => theme.colors.text.primary};
  transition: all ${({ theme }) => theme.transition.fast};

  &::placeholder {
    color: ${({ theme }) => theme.colors.text.tertiary};
  }

  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.accent.primary};
    box-shadow: 0 0 0 2px rgba(50, 184, 198, 0.1);
  }
`;

const SearchIcon = styled.div`
  position: absolute;
  left: 16px;
  top: 50%;
  transform: translateY(-50%);
  color: ${({ theme }) => theme.colors.text.tertiary};
`;

const FiltersRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.spacing.md};
  align-items: center;
`;

const FilterGroup = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
`;

const FilterLabel = styled.span`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.text.secondary};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;


const ResultsHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: ${({ theme }) => theme.spacing.lg};
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.spacing.md};
`;

const ResultsCount = styled.span`
  font-size: 14px;
  color: ${({ theme }) => theme.colors.text.secondary};
`;

const ResultsActions = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
`;

const LogMessage = styled.div`
  font-family: ${({ theme }) => theme.typography.monoFamily};
  font-size: 13px;
  color: ${({ theme }) => theme.colors.text.primary};
  max-width: 500px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
`;

const LogMeta = styled.span`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.text.tertiary};
  white-space: nowrap;
`;

const PaginationWrapper = styled.div`
  display: flex;
  justify-content: center;
  margin-top: ${({ theme }) => theme.spacing.xl};
`;

const ErrorContainer = styled.div`
  display: flex;
  align-items: flex-start;
  gap: ${({ theme }) => theme.spacing.md};
  padding: ${({ theme }) => theme.spacing.lg};
  background: rgba(255, 59, 48, 0.1);
  border: 1px solid rgba(255, 59, 48, 0.3);
  border-radius: ${({ theme }) => theme.radius.md};
  margin-bottom: ${({ theme }) => theme.spacing.lg};
  color: #ff3b30;
`;

const LoadingContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: ${({ theme }) => theme.spacing.xl};
  gap: ${({ theme }) => theme.spacing.lg};
  background: ${({ theme }) => theme.colors.bg.secondary};
  border-radius: ${({ theme }) => theme.radius.md};
  min-height: 300px;
`;

const EmptyState = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: ${({ theme }) => theme.spacing.xl};
  gap: ${({ theme }) => theme.spacing.md};
  background: ${({ theme }) => theme.colors.bg.secondary};
  border-radius: ${({ theme }) => theme.radius.md};
  min-height: 300px;
  color: ${({ theme }) => theme.colors.text.secondary};

  p {
    margin: 0;
    font-size: 15px;
  }
`;

const QuickFilters = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.sm};
  margin-bottom: ${({ theme }) => theme.spacing.md};

  button {
    font-size: 13px;
  }

  @media (max-width: 768px) {
    flex-wrap: wrap;
  }
`;

const getSeverityVariant = (severity: string) => {
  switch (severity?.toLowerCase()) {
    case 'error': return 'error';
    case 'critical': return 'error';
    case 'warning': return 'warning';
    case 'info': return 'info';
    case 'debug': return 'default';
    default: return 'default';
  }
};

const STORAGE_KEY = 'logSearchFilters';
const PAGE_SIZE = 50;

interface SearchFilters {
  query: string;
  timeRange: string;
  severity: string;
  sourcetype: string;
}

export const LogSearch: React.FC = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [severity, setSeverity] = useState('');
  const [sourcetype, setSourcetype] = useState('');
  const [timeRange, setTimeRange] = useState('24h');
  const [results, setResults] = useState<PagedResult<EventDTO> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [recentSearches, setRecentSearches] = useState<SearchFilters[]>([]);

  const loadFiltersFromStorage = useCallback(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        setRecentSearches(Array.isArray(parsed) ? parsed : []);
      }
    } catch {
      console.error('Failed to load filters from storage');
    }
  }, []);

  useEffect(() => {
    loadFiltersFromStorage();
  }, [loadFiltersFromStorage]);

  const saveFiltersToStorage = useCallback((filters: SearchFilters) => {
    try {
      const current = recentSearches.filter(
        f => !(f.query === filters.query && f.severity === filters.severity && f.sourcetype === filters.sourcetype)
      );
      const updated = [filters, ...current].slice(0, 5);
      localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
      setRecentSearches(updated);
    } catch {
      console.error('Failed to save filters to storage');
    }
  }, [recentSearches]);

  const getTimeRangeOffset = (range: string): { start: Date; end: Date } => {
    const end = new Date();
    const start = new Date();

    switch (range) {
      case '1h':
        start.setHours(start.getHours() - 1);
        break;
      case '24h':
        start.setDate(start.getDate() - 1);
        break;
      case '7d':
        start.setDate(start.getDate() - 7);
        break;
      case '30d':
        start.setDate(start.getDate() - 30);
        break;
      default:
        start.setDate(start.getDate() - 1);
    }

    return { start, end };
  };

  const performSearch = useCallback(async (page = 1) => {
    setLoading(true);
    setError(null);

    try {
      const { start, end } = getTimeRangeOffset(timeRange);

      const query: SearchQuery = {
        query: searchQuery || undefined,
        startTime: start.toISOString(),
        endTime: end.toISOString(),
        severity: severity || undefined,
        sourcetype: sourcetype || undefined,
        page: page - 1,
        pageSize: PAGE_SIZE,
      };

      const response = await api.searchEvents(query);
      setResults(response);
      setCurrentPage(page);

      saveFiltersToStorage({
        query: searchQuery,
        timeRange,
        severity,
        sourcetype,
      });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to search logs';
      setError(message);
      setResults(null);
    } finally {
      setLoading(false);
    }
  }, [searchQuery, timeRange, severity, sourcetype, saveFiltersToStorage]);

  const handleSearch = useCallback(() => {
    setCurrentPage(1);
    performSearch(1);
  }, [performSearch]);

  const handleApplyQuickFilter = useCallback((filters: SearchFilters) => {
    setSearchQuery(filters.query);
    setSeverity(filters.severity);
    setSourcetype(filters.sourcetype);
    setTimeRange(filters.timeRange);
    setCurrentPage(1);
  }, []);

  const clearFilters = useCallback(() => {
    setSearchQuery('');
    setSeverity('');
    setSourcetype('');
    setTimeRange('24h');
    setCurrentPage(1);
  }, []);

  const activeFilterCount = [searchQuery, severity, sourcetype].filter(Boolean).length;

  const handleKeyPress = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      handleSearch();
    }
  };

  return (
    <>
      <PageHeader>
        <div>
          <PageTitle>Log Search</PageTitle>
          <PageSubtitle>Search and analyze your log data with advanced filters</PageSubtitle>
        </div>
      </PageHeader>

      {error && (
        <ErrorContainer>
          <AlertCircle size={20} />
          <div>{error}</div>
        </ErrorContainer>
      )}

      <SearchSection>
        <SearchBar>
          <SearchInputWrapper>
            <SearchIcon>
              <Search size={20} />
            </SearchIcon>
            <SearchInput
              placeholder="Search logs by keyword, field=value..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              onKeyPress={handleKeyPress}
            />
          </SearchInputWrapper>
          <Button onClick={handleSearch} disabled={loading}>
            {loading ? <Spinner /> : <Search size={16} />}
            Search
          </Button>
        </SearchBar>

        <FiltersRow>
          <FilterGroup>
            <FilterLabel>Time Range:</FilterLabel>
            <Select
              options={[
                { value: '1h', label: 'Last 1 Hour' },
                { value: '24h', label: 'Last 24 Hours' },
                { value: '7d', label: 'Last 7 Days' },
                { value: '30d', label: 'Last 30 Days' },
              ]}
              value={timeRange}
              onChange={setTimeRange}
              placeholder="Select time range"
              fullWidth={false}
            />
          </FilterGroup>

          <FilterGroup>
            <FilterLabel>Severity:</FilterLabel>
            <Select
              options={[
                { value: '', label: 'All Severities' },
                { value: 'CRITICAL', label: 'Critical' },
                { value: 'ERROR', label: 'Error' },
                { value: 'WARNING', label: 'Warning' },
                { value: 'INFO', label: 'Info' },
                { value: 'DEBUG', label: 'Debug' },
              ]}
              value={severity}
              onChange={setSeverity}
              placeholder="Filter by severity"
              fullWidth={false}
            />
          </FilterGroup>

          <FilterGroup>
            <FilterLabel>Source Type:</FilterLabel>
            <Select
              options={[
                { value: '', label: 'All Sources' },
                { value: 'app-server', label: 'App Server' },
                { value: 'forwarder', label: 'Forwarder' },
                { value: 'auth-service', label: 'Auth Service' },
                { value: 'cache-server', label: 'Cache Server' },
              ]}
              value={sourcetype}
              onChange={setSourcetype}
              placeholder="Filter by source"
              fullWidth={false}
            />
          </FilterGroup>

          {activeFilterCount > 0 && (
            <Button variant="outline" size="sm" onClick={clearFilters}>
              Clear Filters
            </Button>
          )}
        </FiltersRow>

        {recentSearches.length > 0 && (
          <div>
            <FilterLabel style={{ display: 'block', marginBottom: '8px' }}>Recent Searches:</FilterLabel>
            <QuickFilters>
              {recentSearches.map((filter, idx) => (
                <Button
                  key={idx}
                  variant="secondary"
                  size="sm"
                  onClick={() => handleApplyQuickFilter(filter)}
                >
                  {filter.query || '(empty)'} • {filter.severity || 'all'} • {filter.timeRange}
                </Button>
              ))}
            </QuickFilters>
          </div>
        )}
      </SearchSection>

      {results && !loading && (
        <>
          <ResultsHeader>
            <ResultsCount>
              {results.totalElements === 0
                ? 'No results found'
                : `Showing ${(results.number * PAGE_SIZE) + 1}-${Math.min((results.number + 1) * PAGE_SIZE, results.totalElements)} of ${results.totalElements} results`}
            </ResultsCount>
            <ResultsActions>
              <Button variant="secondary" size="sm" icon={<Download size={14} />} disabled>
                Export
              </Button>
            </ResultsActions>
          </ResultsHeader>

          {results.content.length === 0 ? (
            <EmptyState>
              <Search size={32} />
              <p>No logs found matching your filters</p>
            </EmptyState>
          ) : (
            <>
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableHeader>Timestamp</TableHeader>
                      <TableHeader>Severity</TableHeader>
                      <TableHeader>Source</TableHeader>
                      <TableHeader>Message</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {results.content.map((log) => (
                      <TableRow key={log.id} $clickable>
                        <TableCell>
                          <LogMeta>{new Date(log.timestamp).toLocaleString()}</LogMeta>
                        </TableCell>
                        <TableCell>
                          <Badge $variant={getSeverityVariant(log.severity)}>
                            {(log.severity || 'INFO').toUpperCase()}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <LogMeta>{log.sourceName || log.sourcetype || 'unknown'}</LogMeta>
                        </TableCell>
                        <TableCell>
                          <LogMessage>{log.rawMessage || log.rawData || 'No message'}</LogMessage>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>

              {results.totalPages > 1 && (
                <PaginationWrapper>
                  <Pagination
                    currentPage={currentPage}
                    totalPages={results.totalPages}
                    onPageChange={(page) => performSearch(page)}
                  />
                </PaginationWrapper>
              )}
            </>
          )}
        </>
      )}

      {loading && (
        <LoadingContainer>
          <Spinner />
          <p>Searching logs...</p>
        </LoadingContainer>
      )}

      {!loading && !results && !error && (
        <EmptyState>
          <Search size={32} />
          <p>Enter search criteria and click Search to get started</p>
        </EmptyState>
      )}
    </>
  );
};
