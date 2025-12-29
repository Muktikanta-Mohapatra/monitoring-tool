import styled from 'styled-components';
import React, { useState, useEffect, useCallback } from 'react';
import { User, Bell, Key, Shield, Palette, Save, Plus, Trash2, Copy, Check, AlertCircle } from 'lucide-react';
import { PageHeader, PageTitle, PageSubtitle, PageActions } from '../components/Common';
import { Button, Input, Toggle, Select } from '../components/UI';

const SETTINGS_STORAGE_KEY = 'monitoring_tool_settings';

interface ApiKey {
  id: string;
  name: string;
  key: string;
  created: string;
}

interface SettingsState {
  account: {
    fullName: string;
    email: string;
    jobTitle: string;
    department: string;
    timezone: string;
  };
  notifications: {
    emailNotifications: boolean;
    slackNotifications: boolean;
    criticalAlerts: boolean;
    weeklyReports: boolean;
  };
  security: {
    twoFactorAuth: boolean;
    sessionTimeout: boolean;
  };
  appearance: {
    theme: string;
    density: string;
  };
  apiKeys: ApiKey[];
}

const defaultSettings: SettingsState = {
  account: {
    fullName: 'John Smith',
    email: 'john.smith@example.com',
    jobTitle: 'System Administrator',
    department: 'IT Operations',
    timezone: 'UTC-5 (Eastern Time)',
  },
  notifications: {
    emailNotifications: true,
    slackNotifications: false,
    criticalAlerts: true,
    weeklyReports: true,
  },
  security: {
    twoFactorAuth: false,
    sessionTimeout: true,
  },
  appearance: {
    theme: 'dark',
    density: 'comfortable',
  },
  apiKeys: [
    { id: '1', name: 'Production API Key', key: 'sk_live_xxxx...xxxx1234', created: 'Dec 1, 2024' },
    { id: '2', name: 'Development API Key', key: 'sk_test_xxxx...xxxx5678', created: 'Nov 15, 2024' },
  ],
};

const loadSettingsFromStorage = (): SettingsState => {
  try {
    const stored = localStorage.getItem(SETTINGS_STORAGE_KEY);
    if (stored) {
      const parsed = JSON.parse(stored);
      return { ...defaultSettings, ...parsed };
    }
  } catch {
    console.error('Failed to load settings from storage');
  }
  return defaultSettings;
};

const saveSettingsToStorage = (settings: SettingsState): void => {
  try {
    localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(settings));
  } catch (err) {
    console.error('Failed to save settings to storage:', err);
  }
};

const generateApiKey = (): string => {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let result = 'sk_';
  for (let i = 0; i < 32; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
};

const SettingsLayout = styled.div`
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: ${({ theme }) => theme.spacing.xl};

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
`;

const SettingsSidebar = styled.nav`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.xs};
`;

const SidebarItem = styled.button<{ $active: boolean }>`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.md};
  padding: ${({ theme }) => theme.spacing.md} ${({ theme }) => theme.spacing.lg};
  font-size: 14px;
  color: ${({ theme, $active }) => $active ? theme.colors.text.primary : theme.colors.text.secondary};
  background: ${({ theme, $active }) => $active ? theme.colors.bg.tertiary : 'transparent'};
  border-radius: ${({ theme }) => theme.radius.sm};
  text-align: left;
  transition: all ${({ theme }) => theme.transition.fast};

  &:hover {
    background: ${({ theme }) => theme.colors.bg.tertiary};
    color: ${({ theme }) => theme.colors.text.primary};
  }
`;

const SettingsContent = styled.div``;

const SettingsSection = styled.div`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-radius: ${({ theme }) => theme.radius.md};
  padding: ${({ theme }) => theme.spacing.xl};
  margin-bottom: ${({ theme }) => theme.spacing.lg};
`;

const SectionHeader = styled.div`
  margin-bottom: ${({ theme }) => theme.spacing.xl};
`;

const SectionTitle = styled.h2`
  font-size: 18px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};
  margin: 0 0 ${({ theme }) => theme.spacing.xs} 0;
`;

const SectionDescription = styled.p`
  font-size: 13px;
  color: ${({ theme }) => theme.colors.text.secondary};
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

const ToggleRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${({ theme }) => theme.spacing.md} 0;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border.default};

  &:last-child {
    border-bottom: none;
  }
`;

const ToggleInfo = styled.div``;

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

const ApiKeyCard = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: ${({ theme }) => theme.spacing.md};
  background: ${({ theme }) => theme.colors.bg.tertiary};
  border-radius: ${({ theme }) => theme.radius.sm};
  margin-bottom: ${({ theme }) => theme.spacing.sm};
`;

const ApiKeyInfo = styled.div`
  flex: 1;
`;

const ApiKeyName = styled.div`
  font-size: 14px;
  font-weight: 500;
  color: ${({ theme }) => theme.colors.text.primary};
`;

const ApiKeyValue = styled.div`
  font-size: 12px;
  font-family: ${({ theme }) => theme.typography.monoFamily};
  color: ${({ theme }) => theme.colors.text.secondary};
  margin-top: ${({ theme }) => theme.spacing.xs};
`;

const ApiKeyActions = styled.div`
  display: flex;
  gap: ${({ theme }) => theme.spacing.xs};
`;

const IconButton = styled.button`
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: ${({ theme }) => theme.radius.sm};
  color: ${({ theme }) => theme.colors.text.secondary};
  transition: all ${({ theme }) => theme.transition.fast};

  &:hover {
    background: ${({ theme }) => theme.colors.bg.secondary};
    color: ${({ theme }) => theme.colors.text.primary};
  }
`;

const settingsItems = [
  { id: 'account', label: 'Account', icon: <User size={18} /> },
  { id: 'notifications', label: 'Notifications', icon: <Bell size={18} /> },
  { id: 'api-keys', label: 'API Keys', icon: <Key size={18} /> },
  { id: 'appearance', label: 'Appearance', icon: <Palette size={18} /> },
  { id: 'security', label: 'Security', icon: <Shield size={18} /> },
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

export const Settings: React.FC = () => {
  const [activeSection, setActiveSection] = useState('account');
  const [settings, setSettings] = useState<SettingsState>(defaultSettings);
  const [saving, setSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [copiedKeyId, setCopiedKeyId] = useState<string | null>(null);

  useEffect(() => {
    setSettings(loadSettingsFromStorage());
  }, []);

  const handleSaveSettings = useCallback(() => {
    setSaving(true);
    setSaveError(null);
    try {
      saveSettingsToStorage(settings);
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
    } catch {
      setSaveError('Failed to save settings');
    } finally {
      setSaving(false);
    }
  }, [settings]);

  const updateAccount = useCallback((field: keyof SettingsState['account'], value: string) => {
    setSettings(prev => ({
      ...prev,
      account: { ...prev.account, [field]: value },
    }));
  }, []);

  const updateNotification = useCallback((field: keyof SettingsState['notifications'], value: boolean) => {
    setSettings(prev => ({
      ...prev,
      notifications: { ...prev.notifications, [field]: value },
    }));
  }, []);

  const updateSecurity = useCallback((field: keyof SettingsState['security'], value: boolean) => {
    setSettings(prev => ({
      ...prev,
      security: { ...prev.security, [field]: value },
    }));
  }, []);

  const updateAppearance = useCallback((field: keyof SettingsState['appearance'], value: string) => {
    setSettings(prev => ({
      ...prev,
      appearance: { ...prev.appearance, [field]: value },
    }));
  }, []);

  const handleGenerateApiKey = useCallback(() => {
    const newKey: ApiKey = {
      id: Date.now().toString(),
      name: `API Key ${settings.apiKeys.length + 1}`,
      key: generateApiKey(),
      created: new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }),
    };
    setSettings(prev => ({
      ...prev,
      apiKeys: [...prev.apiKeys, newKey],
    }));
  }, [settings.apiKeys.length]);

  const handleDeleteApiKey = useCallback((id: string) => {
    setSettings(prev => ({
      ...prev,
      apiKeys: prev.apiKeys.filter(key => key.id !== id),
    }));
  }, []);

  const handleCopyApiKey = useCallback((id: string, key: string) => {
    navigator.clipboard.writeText(key).then(() => {
      setCopiedKeyId(id);
      setTimeout(() => setCopiedKeyId(null), 2000);
    });
  }, []);

  return (
    <>
      <PageHeader>
        <div>
          <PageTitle>Settings</PageTitle>
          <PageSubtitle>Manage your account and preferences</PageSubtitle>
        </div>
        <PageActions>
          <Button icon={<Save size={16} />} onClick={handleSaveSettings} loading={saving}>
            Save Changes
          </Button>
        </PageActions>
      </PageHeader>

      {saveSuccess && (
        <SuccessMessage>
          <Check size={18} />
          <span>Settings saved successfully</span>
        </SuccessMessage>
      )}

      {saveError && (
        <ErrorMessage>
          <AlertCircle size={18} />
          <span>{saveError}</span>
        </ErrorMessage>
      )}

      <SettingsLayout>
        <SettingsSidebar>
          {settingsItems.map((item) => (
            <SidebarItem
              key={item.id}
              $active={activeSection === item.id}
              onClick={() => setActiveSection(item.id)}
            >
              {item.icon}
              {item.label}
            </SidebarItem>
          ))}
        </SettingsSidebar>

        <SettingsContent>
          {activeSection === 'account' && (
            <SettingsSection>
              <SectionHeader>
                <SectionTitle>Account Settings</SectionTitle>
                <SectionDescription>Update your personal information and preferences</SectionDescription>
              </SectionHeader>

              <FormGrid>
                <Input 
                  label="Full Name" 
                  value={settings.account.fullName}
                  onChange={(e) => updateAccount('fullName', e.target.value)}
                />
                <Input 
                  label="Email Address" 
                  type="email" 
                  value={settings.account.email}
                  onChange={(e) => updateAccount('email', e.target.value)}
                />
                <Input 
                  label="Job Title" 
                  value={settings.account.jobTitle}
                  onChange={(e) => updateAccount('jobTitle', e.target.value)}
                />
                <Input 
                  label="Department" 
                  value={settings.account.department}
                  onChange={(e) => updateAccount('department', e.target.value)}
                />
              </FormGrid>

              <div style={{ marginTop: '24px' }}>
                <Input 
                  label="Timezone" 
                  value={settings.account.timezone}
                  onChange={(e) => updateAccount('timezone', e.target.value)}
                />
              </div>
            </SettingsSection>
          )}

          {activeSection === 'notifications' && (
            <SettingsSection>
              <SectionHeader>
                <SectionTitle>Notification Preferences</SectionTitle>
                <SectionDescription>Configure how you want to receive notifications</SectionDescription>
              </SectionHeader>

              <ToggleRow>
                <ToggleInfo>
                  <ToggleTitle>Email Notifications</ToggleTitle>
                  <ToggleDescription>Receive alerts and updates via email</ToggleDescription>
                </ToggleInfo>
                <Toggle checked={settings.notifications.emailNotifications} onChange={(val) => updateNotification('emailNotifications', val)} />
              </ToggleRow>

              <ToggleRow>
                <ToggleInfo>
                  <ToggleTitle>Slack Notifications</ToggleTitle>
                  <ToggleDescription>Send alerts to your Slack channel</ToggleDescription>
                </ToggleInfo>
                <Toggle checked={settings.notifications.slackNotifications} onChange={(val) => updateNotification('slackNotifications', val)} />
              </ToggleRow>

              <ToggleRow>
                <ToggleInfo>
                  <ToggleTitle>Critical Alerts</ToggleTitle>
                  <ToggleDescription>Always notify for critical system events</ToggleDescription>
                </ToggleInfo>
                <Toggle checked={settings.notifications.criticalAlerts} onChange={(val) => updateNotification('criticalAlerts', val)} />
              </ToggleRow>

              <ToggleRow>
                <ToggleInfo>
                  <ToggleTitle>Weekly Reports</ToggleTitle>
                  <ToggleDescription>Receive weekly summary reports</ToggleDescription>
                </ToggleInfo>
                <Toggle checked={settings.notifications.weeklyReports} onChange={(val) => updateNotification('weeklyReports', val)} />
              </ToggleRow>
            </SettingsSection>
          )}

          {activeSection === 'api-keys' && (
            <SettingsSection>
              <SectionHeader>
                <SectionTitle>API Keys</SectionTitle>
                <SectionDescription>Manage your API keys for programmatic access</SectionDescription>
              </SectionHeader>

              <div style={{ marginBottom: '16px' }}>
                <Button size="sm" icon={<Plus size={14} />} onClick={handleGenerateApiKey}>Generate New Key</Button>
              </div>

              {settings.apiKeys.map((apiKey) => (
                <ApiKeyCard key={apiKey.id}>
                  <ApiKeyInfo>
                    <ApiKeyName>{apiKey.name}</ApiKeyName>
                    <ApiKeyValue>{apiKey.key} • Created {apiKey.created}</ApiKeyValue>
                  </ApiKeyInfo>
                  <ApiKeyActions>
                    <IconButton onClick={() => handleCopyApiKey(apiKey.id, apiKey.key)} title="Copy">
                      {copiedKeyId === apiKey.id ? <Check size={16} /> : <Copy size={16} />}
                    </IconButton>
                    <IconButton onClick={() => handleDeleteApiKey(apiKey.id)} title="Delete">
                      <Trash2 size={16} />
                    </IconButton>
                  </ApiKeyActions>
                </ApiKeyCard>
              ))}
            </SettingsSection>
          )}

          {activeSection === 'appearance' && (
            <SettingsSection>
              <SectionHeader>
                <SectionTitle>Appearance</SectionTitle>
                <SectionDescription>Customize the look and feel of the application</SectionDescription>
              </SectionHeader>

              <FormGrid>
                <Select
                  label="Theme"
                  options={[
                    { value: 'dark', label: 'Dark Mode' },
                    { value: 'light', label: 'Light Mode' },
                    { value: 'system', label: 'System Default' },
                  ]}
                  value={settings.appearance.theme}
                  onChange={(val) => updateAppearance('theme', val)}
                />
                <Select
                  label="Density"
                  options={[
                    { value: 'comfortable', label: 'Comfortable' },
                    { value: 'compact', label: 'Compact' },
                  ]}
                  value={settings.appearance.density}
                  onChange={(val) => updateAppearance('density', val)}
                />
              </FormGrid>
            </SettingsSection>
          )}

          {activeSection === 'security' && (
            <SettingsSection>
              <SectionHeader>
                <SectionTitle>Security Settings</SectionTitle>
                <SectionDescription>Configure security options for your account</SectionDescription>
              </SectionHeader>

              <ToggleRow>
                <ToggleInfo>
                  <ToggleTitle>Two-Factor Authentication</ToggleTitle>
                  <ToggleDescription>Add an extra layer of security to your account</ToggleDescription>
                </ToggleInfo>
                <Toggle checked={settings.security.twoFactorAuth} onChange={(val) => updateSecurity('twoFactorAuth', val)} />
              </ToggleRow>

              <ToggleRow>
                <ToggleInfo>
                  <ToggleTitle>Session Timeout</ToggleTitle>
                  <ToggleDescription>Automatically log out after 30 minutes of inactivity</ToggleDescription>
                </ToggleInfo>
                <Toggle checked={settings.security.sessionTimeout} onChange={(val) => updateSecurity('sessionTimeout', val)} />
              </ToggleRow>

              <div style={{ marginTop: '24px' }}>
                <Button variant="outline">Change Password</Button>
              </div>
            </SettingsSection>
          )}
        </SettingsContent>
      </SettingsLayout>
    </>
  );
};
