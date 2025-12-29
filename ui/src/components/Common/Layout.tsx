import styled from 'styled-components';
import React, { useState } from 'react';
import { Header } from './Header';
import { Sidebar } from './Sidebar';

interface LayoutProps {
  children: React.ReactNode;
}

const LayoutContainer = styled.div`
  min-height: 100vh;
  background: ${({ theme }) => theme.colors.bg.primary};
`;

const MainContent = styled.main`
  margin-left: ${({ theme }) => theme.sizes.sidebarWidth};
  margin-top: ${({ theme }) => theme.sizes.headerHeight};
  min-height: calc(100vh - ${({ theme }) => theme.sizes.headerHeight});
  padding: ${({ theme }) => theme.spacing.xl};

  @media (max-width: 1024px) {
    margin-left: 0;
  }

  @media (max-width: 768px) {
    padding: ${({ theme }) => theme.spacing.lg};
  }

  @media (max-width: 480px) {
    padding: ${({ theme }) => theme.spacing.md};
  }
`;

const PageContainer = styled.div`
  max-width: 1400px;
  margin: 0 auto;
`;

export const Layout: React.FC<LayoutProps> = ({ children }) => {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);

  const toggleSidebar = () => setIsSidebarOpen(!isSidebarOpen);
  const closeSidebar = () => setIsSidebarOpen(false);

  return (
    <LayoutContainer>
      <Header onMenuClick={toggleSidebar} />
      <Sidebar isOpen={isSidebarOpen} onClose={closeSidebar} />
      <MainContent>
        <PageContainer>{children}</PageContainer>
      </MainContent>
    </LayoutContainer>
  );
};

export const PageHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: ${({ theme }) => theme.spacing.xl};
  flex-wrap: wrap;
  gap: ${({ theme }) => theme.spacing.lg};

  @media (max-width: 768px) {
    flex-direction: column;
    align-items: flex-start;
  }

  @media (max-width: 480px) {
    margin-bottom: ${({ theme }) => theme.spacing.lg};
  }
`;

export const PageTitle = styled.h1`
  font-size: 32px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};
  margin: 0;

  @media (max-width: 768px) {
    font-size: 24px;
  }

  @media (max-width: 480px) {
    font-size: 20px;
  }
`;

export const PageSubtitle = styled.p`
  font-size: 14px;
  color: ${({ theme }) => theme.colors.text.secondary};
  margin: ${({ theme }) => theme.spacing.xs} 0 0 0;
`;

export const PageActions = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
`;

export const Grid = styled.div<{ $columns?: number; $gap?: string }>`
  display: grid;
  grid-template-columns: repeat(${({ $columns }) => $columns || 4}, 1fr);
  gap: ${({ $gap, theme }) => $gap || theme.spacing.lg};

  @media (max-width: 1200px) {
    grid-template-columns: repeat(3, 1fr);
  }

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, 1fr);
  }

  @media (max-width: 768px) {
    grid-template-columns: 1fr;
    gap: ${({ $gap, theme }) => $gap || theme.spacing.md};
  }

  @media (max-width: 480px) {
    gap: ${({ $gap, theme }) => $gap || theme.spacing.sm};
  }
`;

export const Section = styled.section`
  margin-bottom: ${({ theme }) => theme.spacing['2xl']};
`;

export const SectionTitle = styled.h2`
  font-size: 18px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};
  margin: 0 0 ${({ theme }) => theme.spacing.lg} 0;
`;
