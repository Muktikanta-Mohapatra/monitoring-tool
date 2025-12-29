import styled from 'styled-components';
import React from 'react';

interface Tab {
  id: string;
  label: string;
  icon?: React.ReactNode;
}

interface TabsProps {
  tabs: Tab[];
  activeTab: string;
  onChange: (tabId: string) => void;
}

const TabsContainer = styled.div`
  display: flex;
  border-bottom: 1px solid ${({ theme }) => theme.colors.border.default};
  gap: ${({ theme }) => theme.spacing.xs};
`;

const TabButton = styled.button<{ $active: boolean }>`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
  padding: ${({ theme }) => theme.spacing.md} ${({ theme }) => theme.spacing.lg};
  font-size: 14px;
  font-weight: 500;
  color: ${({ theme, $active }) => 
    $active ? theme.colors.accent.primary : theme.colors.text.secondary};
  background: transparent;
  border: none;
  border-bottom: 2px solid ${({ theme, $active }) => 
    $active ? theme.colors.accent.primary : 'transparent'};
  margin-bottom: -1px;
  transition: all ${({ theme }) => theme.transition.fast};
  cursor: pointer;

  &:hover {
    color: ${({ theme }) => theme.colors.text.primary};
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.colors.accent.primary};
    outline-offset: -2px;
  }
`;

const TabContent = styled.div`
  padding: ${({ theme }) => theme.spacing.xl} 0;
`;

export const Tabs: React.FC<TabsProps> = ({ tabs, activeTab, onChange }) => {
  return (
    <TabsContainer>
      {tabs.map((tab) => (
        <TabButton
          key={tab.id}
          $active={activeTab === tab.id}
          onClick={() => onChange(tab.id)}
        >
          {tab.icon}
          {tab.label}
        </TabButton>
      ))}
    </TabsContainer>
  );
};

export { TabContent };
