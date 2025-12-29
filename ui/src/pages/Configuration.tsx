import styled from 'styled-components';
import React, { useState, useCallback, useEffect } from 'react';
import { Plus, Edit2, Trash2, Save, Database, Send, Box, Settings, Layers, Check, AlertCircle } from 'lucide-react';
import { PageHeader, PageTitle, PageSubtitle, PageActions } from '../components/Common';
import { Button, Tabs, Table, TableContainer, TableHead, TableBody, TableRow, TableHeader, TableCell, Badge, Input, Toggle } from '../components/UI';

const CONFIG_STORAGE_KEY = 'monitoring_tool_config';

const ContentArea = styled.div`
  margin-top: ${({ theme }) => theme.spacing.lg};
`;

const ConfigSection = styled.div`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-radius: ${({ theme }) => theme.radius.md};
  padding: ${({ theme }) => theme.spacing.lg};
  margin-bottom: ${({ theme }) => theme.spacing.lg};
`;

const SectionHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: ${({ theme }) => theme.spacing.lg};
`;

const SectionTitle = styled.h3`
  font-size: 16px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};
  margin: 0;
`;

const FormGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: ${({ theme }) => theme.spacing.lg};

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
  }
`;

const ToggleGroup = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${({ theme }) => theme.spacing.md};
  background: ${({ theme }) => theme.colors.bg.tertiary};
  border-radius: ${({ theme }) => theme.radius.sm};
`;

const ToggleLabel = styled.div``;

const ToggleTitle = styled.div`
  font-size: 14px;
  font-weight: 500;
  color: ${({ theme }) => theme.colors.text.primary};
`;

const ToggleDescription = styled.div`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.text.secondary};
  margin-top: ${({ theme }) => theme.spacing.xs};
`;

const ActionsCell = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.xs};
`;

const IconButton = styled.button`
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: ${({ theme }) => theme.radius.sm};
  color: ${({ theme }) => theme.colors.text.secondary};
  transition: all ${({ theme }) => theme.transition.fast};

  &:hover {
    background: ${({ theme }) => theme.colors.bg.tertiary};
    color: ${({ theme }) => theme.colors.text.primary};
  }
`;

const tabs = [
  { id: 'inputs', label: 'Data Inputs', icon: <Database size={16} /> },
  { id: 'forwarding', label: 'Forwarding', icon: <Send size={16} /> },
  { id: 'indexes', label: 'Indexes', icon: <Layers size={16} /> },
  { id: 'apps', label: 'Apps', icon: <Box size={16} /> },
  { id: 'system', label: 'System Settings', icon: <Settings size={16} /> },
];

const defaultDataInputs = [
  { id: '1', name: 'syslog-tcp', type: 'TCP', port: '514', status: 'active' },
  { id: '2', name: 'syslog-udp', type: 'UDP', port: '514', status: 'active' },
  { id: '3', name: 'http-events', type: 'HTTP', port: '8088', status: 'active' },
  { id: '4', name: 'file-monitor', type: 'File', port: '-', status: 'disabled' },
];

const defaultIndexes = [
  { id: '1', name: 'main', size: '125 GB', events: '45.2M', retention: '90 days' },
  { id: '2', name: 'security', size: '82 GB', events: '28.1M', retention: '365 days' },
  { id: '3', name: 'network', size: '56 GB', events: '19.8M', retention: '30 days' },
  { id: '4', name: 'application', size: '98 GB', events: '35.4M', retention: '60 days' },
];

const SuccessMessage = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.md};
  padding: ${({ theme }) => theme.spacing.md};
  background: rgba(61, 204, 113, 0.1);
  border: 1px solid rgba(61, 204, 113, 0.3);
  border-radius: ${({ theme }) => theme.radius.md};
  color: #3dcc71;
  margin-bottom: ${({ theme }) => theme.spacing.lg};
  animation: slideIn 0.3s ease-in-out;

  @keyframes slideIn {
    from {
      opacity: 0;
      transform: translateY(-10px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }
`;

const ErrorMessage = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.md};
  padding: ${({ theme }) => theme.spacing.md};
  background: rgba(255, 84, 89, 0.1);
  border: 1px solid rgba(255, 84, 89, 0.3);
  border-radius: ${({ theme }) => theme.radius.md};
  color: #ff5459;
  margin-bottom: ${({ theme }) => theme.spacing.lg};
`;

interface ConfigState {
  dataInputs: typeof defaultDataInputs;
  indexes: typeof defaultIndexes;
  forwardingConfig: {
    targetHost: string;
    targetPort: string;
    maxQueueSize: string;
    retryInterval: string;
  };
  systemConfig: {
    serverName: string;
    serverRole: string;
    dataDirectory: string;
    logLevel: string;
  };
  sslEnabled: boolean;
  compressionEnabled: boolean;
}

const loadConfigFromStorage = (): Partial<ConfigState> => {
  try {
    const stored = localStorage.getItem(CONFIG_STORAGE_KEY);
    return stored ? JSON.parse(stored) : {};
  } catch {
    return {};
  }
};

const saveConfigToStorage = (config: ConfigState): void => {
  try {
    localStorage.setItem(CONFIG_STORAGE_KEY, JSON.stringify(config));
  } catch (err) {
    console.error('Failed to save config to storage:', err);
  }
};

export const Configuration: React.FC = () => {
  const [activeTab, setActiveTab] = useState('inputs');
  const [saving, setSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [dataInputs, setDataInputs] = useState(defaultDataInputs);
  const [indexes, setIndexes] = useState(defaultIndexes);

  const [forwardingConfig, setForwardingConfig] = useState({
    targetHost: 'splunk-indexer.example.com',
    targetPort: '9997',
    maxQueueSize: '500KB',
    retryInterval: '30s',
  });

  const [systemConfig, setSystemConfig] = useState({
    serverName: 'splunk-forwarder-01',
    serverRole: 'Universal Forwarder',
    dataDirectory: '/opt/splunkforwarder/var/lib',
    logLevel: 'INFO',
  });

  const [sslEnabled, setSslEnabled] = useState(true);
  const [compressionEnabled, setCompressionEnabled] = useState(true);

  useEffect(() => {
    const stored = loadConfigFromStorage();
    if (stored.dataInputs) setDataInputs(stored.dataInputs);
    if (stored.indexes) setIndexes(stored.indexes);
    if (stored.forwardingConfig) setForwardingConfig(stored.forwardingConfig);
    if (stored.systemConfig) setSystemConfig(stored.systemConfig);
    if (stored.sslEnabled !== undefined) setSslEnabled(stored.sslEnabled);
    if (stored.compressionEnabled !== undefined) setCompressionEnabled(stored.compressionEnabled);
  }, []);

  const handleSaveChanges = useCallback(async () => {
    setSaving(true);
    setSaveError(null);
    setSaveSuccess(false);

    try {
      const config: ConfigState = {
        dataInputs,
        indexes,
        forwardingConfig,
        systemConfig,
        sslEnabled,
        compressionEnabled,
      };

      saveConfigToStorage(config);

      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
    } catch {
      setSaveError('Failed to save configuration. Please try again.');
    } finally {
      setSaving(false);
    }
  }, [dataInputs, indexes, forwardingConfig, systemConfig, sslEnabled, compressionEnabled]);

  const handleDeleteInput = useCallback((id: string) => {
    setDataInputs(prev => prev.filter(input => input.id !== id));
  }, []);

  const handleDeleteIndex = useCallback((id: string) => {
    setIndexes(prev => prev.filter(index => index.id !== id));
  }, []);

  return (
    <>
      <PageHeader>
        <div>
          <PageTitle>Configuration</PageTitle>
          <PageSubtitle>Manage system settings and data inputs</PageSubtitle>
        </div>
        <PageActions>
          <Button 
            icon={<Save size={16} />} 
            onClick={handleSaveChanges}
            loading={saving}
          >
            Save Changes
          </Button>
        </PageActions>
      </PageHeader>

      {saveSuccess && (
        <SuccessMessage>
          <Check size={18} />
          <span>Configuration saved successfully</span>
        </SuccessMessage>
      )}

      {saveError && (
        <ErrorMessage>
          <AlertCircle size={18} />
          <span>{saveError}</span>
        </ErrorMessage>
      )}

      <Tabs tabs={tabs} activeTab={activeTab} onChange={setActiveTab} />

      <ContentArea>
        {activeTab === 'inputs' && (
          <>
            <ConfigSection>
              <SectionHeader>
                <SectionTitle>Data Inputs</SectionTitle>
                <Button size="sm" icon={<Plus size={14} />}>Add Input</Button>
              </SectionHeader>

              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableHeader>Name</TableHeader>
                      <TableHeader>Type</TableHeader>
                      <TableHeader>Port</TableHeader>
                      <TableHeader>Status</TableHeader>
                      <TableHeader>Actions</TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {dataInputs.map((input) => (
                      <TableRow key={input.id}>
                        <TableCell>{input.name}</TableCell>
                        <TableCell>{input.type}</TableCell>
                        <TableCell>{input.port}</TableCell>
                        <TableCell>
                          <Badge $variant={input.status === 'active' ? 'success' : 'default'}>
                            {input.status.toUpperCase()}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <ActionsCell>
                            <IconButton title="Edit"><Edit2 size={14} /></IconButton>
                            <IconButton title="Delete" onClick={() => handleDeleteInput(input.id)}><Trash2 size={14} /></IconButton>
                          </ActionsCell>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </ConfigSection>
          </>
        )}

        {activeTab === 'forwarding' && (
          <ConfigSection>
            <SectionHeader>
              <SectionTitle>Forwarding Settings</SectionTitle>
            </SectionHeader>

            <FormGrid>
              <Input 
                label="Target Host" 
                placeholder="splunk-indexer.example.com" 
                value={forwardingConfig.targetHost}
                onChange={(e) => setForwardingConfig(prev => ({ ...prev, targetHost: e.target.value }))}
              />
              <Input 
                label="Target Port" 
                type="number" 
                placeholder="9997" 
                value={forwardingConfig.targetPort}
                onChange={(e) => setForwardingConfig(prev => ({ ...prev, targetPort: e.target.value }))}
              />
              <Input 
                label="Max Queue Size" 
                placeholder="500KB" 
                value={forwardingConfig.maxQueueSize}
                onChange={(e) => setForwardingConfig(prev => ({ ...prev, maxQueueSize: e.target.value }))}
              />
              <Input 
                label="Retry Interval" 
                placeholder="30s" 
                value={forwardingConfig.retryInterval}
                onChange={(e) => setForwardingConfig(prev => ({ ...prev, retryInterval: e.target.value }))}
              />
            </FormGrid>

            <div style={{ marginTop: '24px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
              <ToggleGroup>
                <ToggleLabel>
                  <ToggleTitle>SSL Encryption</ToggleTitle>
                  <ToggleDescription>Enable SSL/TLS for secure data transmission</ToggleDescription>
                </ToggleLabel>
                <Toggle checked={sslEnabled} onChange={setSslEnabled} />
              </ToggleGroup>

              <ToggleGroup>
                <ToggleLabel>
                  <ToggleTitle>Compression</ToggleTitle>
                  <ToggleDescription>Compress data before forwarding to reduce bandwidth</ToggleDescription>
                </ToggleLabel>
                <Toggle checked={compressionEnabled} onChange={setCompressionEnabled} />
              </ToggleGroup>
            </div>
          </ConfigSection>
        )}

        {activeTab === 'indexes' && (
          <ConfigSection>
            <SectionHeader>
              <SectionTitle>Indexes</SectionTitle>
              <Button size="sm" icon={<Plus size={14} />}>Create Index</Button>
            </SectionHeader>

            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableHeader>Index Name</TableHeader>
                    <TableHeader>Size</TableHeader>
                    <TableHeader>Events</TableHeader>
                    <TableHeader>Retention</TableHeader>
                    <TableHeader>Actions</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {indexes.map((index) => (
                    <TableRow key={index.id}>
                      <TableCell>{index.name}</TableCell>
                      <TableCell>{index.size}</TableCell>
                      <TableCell>{index.events}</TableCell>
                      <TableCell>{index.retention}</TableCell>
                      <TableCell>
                        <ActionsCell>
                          <IconButton title="Edit"><Edit2 size={14} /></IconButton>
                          <IconButton title="Delete" onClick={() => handleDeleteIndex(index.id)}><Trash2 size={14} /></IconButton>
                        </ActionsCell>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </ConfigSection>
        )}

        {activeTab === 'apps' && (
          <ConfigSection>
            <SectionHeader>
              <SectionTitle>Installed Apps</SectionTitle>
              <Button size="sm" icon={<Plus size={14} />}>Install App</Button>
            </SectionHeader>
            <p style={{ color: '#A1A9A8', fontSize: '14px' }}>
              No apps installed. Click "Install App" to add new functionality.
            </p>
          </ConfigSection>
        )}

        {activeTab === 'system' && (
          <ConfigSection>
            <SectionHeader>
              <SectionTitle>System Settings</SectionTitle>
            </SectionHeader>

            <FormGrid>
              <Input 
                label="Server Name" 
                value={systemConfig.serverName}
                onChange={(e) => setSystemConfig(prev => ({ ...prev, serverName: e.target.value }))}
              />
              <Input 
                label="Server Role" 
                value={systemConfig.serverRole} 
                disabled 
              />
              <Input 
                label="Data Directory" 
                value={systemConfig.dataDirectory}
                onChange={(e) => setSystemConfig(prev => ({ ...prev, dataDirectory: e.target.value }))}
              />
              <Input 
                label="Log Level" 
                value={systemConfig.logLevel}
                onChange={(e) => setSystemConfig(prev => ({ ...prev, logLevel: e.target.value }))}
              />
            </FormGrid>
          </ConfigSection>
        )}
      </ContentArea>
    </>
  );
};
