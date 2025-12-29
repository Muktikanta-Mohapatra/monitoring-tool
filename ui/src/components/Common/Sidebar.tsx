import styled from 'styled-components';
import React, { useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  Home,
  LayoutDashboard,
  Search,
  Server,
  Settings,
  Users,
  FileText,
  ChevronDown,
  ChevronRight,
  Activity,
  AlertTriangle,
  Database,
  Key,
  HelpCircle,
  BookOpen,
} from 'lucide-react';

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
}

const SidebarOverlay = styled.div<{ $isOpen: boolean }>`
  display: none;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;

  @media (max-width: 1024px) {
    display: ${({ $isOpen }) => ($isOpen ? 'block' : 'none')};
  }
`;

const SidebarContainer = styled.aside<{ $isOpen: boolean }>`
  position: fixed;
  top: ${({ theme }) => theme.sizes.headerHeight};
  left: 0;
  bottom: 0;
  width: ${({ theme }) => theme.sizes.sidebarWidth};
  background: ${({ theme }) => theme.colors.bg.sidebar};
  border-right: 1px solid #1F2A28;
  overflow-y: auto;
  z-index: 999;
  transition: transform ${({ theme }) => theme.transition.slow};

  @media (max-width: 1024px) {
    transform: translateX(${({ $isOpen }) => ($isOpen ? '0' : '-100%')});
  }

  @media (max-width: 480px) {
    width: min(80vw, 280px);
  }
`;

const NavSection = styled.div`
  padding: ${({ theme }) => theme.spacing.md} 0;
`;

const NavGroupHeader = styled.button`
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: ${({ theme }) => theme.spacing.md} ${({ theme }) => theme.spacing.lg};
  font-size: 12px;
  font-weight: 600;
  color: #7A8C8A;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  transition: color ${({ theme }) => theme.transition.fast};

  &:hover {
    color: #E8EEED;
  }

  @media (max-width: 480px) {
    padding: ${({ theme }) => theme.spacing.sm} ${({ theme }) => theme.spacing.md};
    font-size: 11px;
  }
`;

const NavGroup = styled.div`
  margin-bottom: ${({ theme }) => theme.spacing.sm};
`;

const NavItems = styled.div<{ $isOpen: boolean }>`
  max-height: ${({ $isOpen }) => ($isOpen ? '500px' : '0')};
  overflow: hidden;
  transition: max-height ${({ theme }) => theme.transition.slow};
`;

const StyledNavLink = styled(NavLink)`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.md};
  padding: ${({ theme }) => theme.spacing.md} ${({ theme }) => theme.spacing.lg};
  font-size: 13px;
  color: #A1A9A8;
  text-decoration: none;
  transition: all ${({ theme }) => theme.transition.fast};
  border-radius: 0;
  margin: 0 ${({ theme }) => theme.spacing.sm};
  border-radius: ${({ theme }) => theme.radius.sm};

  &:hover {
    color: #E8EEED;
    background: #1F2A28;
  }

  &.active {
    color: #FFFFFF;
    background: #2A403D;
  }

  svg {
    flex-shrink: 0;
  }

  @media (max-width: 480px) {
    padding: ${({ theme }) => theme.spacing.sm} ${({ theme }) => theme.spacing.md};
    font-size: 12px;
    margin: 0 ${({ theme }) => theme.spacing.xs};
    gap: ${({ theme }) => theme.spacing.sm};
  }
`;

const SubNavLink = styled(StyledNavLink)`
  padding-left: ${({ theme }) => theme.spacing['2xl']};
  font-size: 13px;

  @media (max-width: 480px) {
    padding-left: ${({ theme }) => theme.spacing.xl};
    font-size: 12px;
  }
`;

interface NavGroupItemProps {
  title: string;
  icon: React.ReactNode;
  items: { label: string; path: string; icon?: React.ReactNode }[];
  defaultOpen?: boolean;
}

const NavGroupItem: React.FC<NavGroupItemProps> = ({ title, icon, items, defaultOpen = false }) => {
  const [isOpen, setIsOpen] = useState(defaultOpen);
  const location = useLocation();
  const isActive = items.some((item) => location.pathname === item.path);

  React.useEffect(() => {
    if (isActive) setIsOpen(true);
  }, [isActive]);

  return (
    <NavGroup>
      <NavGroupHeader onClick={() => setIsOpen(!isOpen)}>
        <span style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          {icon}
          {title}
        </span>
        {isOpen ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
      </NavGroupHeader>
      <NavItems $isOpen={isOpen}>
        {items.map((item) => (
          <SubNavLink key={item.path} to={item.path}>
            {item.icon}
            {item.label}
          </SubNavLink>
        ))}
      </NavItems>
    </NavGroup>
  );
};

export const Sidebar: React.FC<SidebarProps> = ({ isOpen, onClose }) => {
  return (
    <>
      <SidebarOverlay $isOpen={isOpen} onClick={onClose} />
      <SidebarContainer $isOpen={isOpen}>
        <NavSection>
          <StyledNavLink to="/" end>
            <Home size={18} />
            Home
          </StyledNavLink>

          <NavGroupItem
            title="Dashboards"
            icon={<LayoutDashboard size={18} />}
            defaultOpen
            items={[
              { label: 'Overview', path: '/dashboard', icon: <Activity size={16} /> },
              { label: 'Performance', path: '/dashboard/performance', icon: <Activity size={16} /> },
              { label: 'Errors & Alerts', path: '/dashboard/alerts', icon: <AlertTriangle size={16} /> },
            ]}
          />

          <NavGroupItem
            title="Searches"
            icon={<Search size={18} />}
            items={[
              { label: 'Log Search', path: '/search', icon: <Search size={16} /> },
              { label: 'Saved Searches', path: '/search/saved', icon: <FileText size={16} /> },
            ]}
          />

          <NavGroupItem
            title="Forwarders"
            icon={<Server size={18} />}
            items={[
              { label: 'Status Monitor', path: '/forwarders', icon: <Activity size={16} /> },
              { label: 'Configuration', path: '/forwarders/config', icon: <Settings size={16} /> },
            ]}
          />

          <NavGroupItem
            title="Administration"
            icon={<Settings size={18} />}
            items={[
              { label: 'Configuration', path: '/config', icon: <Settings size={16} /> },
              { label: 'User Management', path: '/admin/users', icon: <Users size={16} /> },
              { label: 'Data Inputs', path: '/config/inputs', icon: <Database size={16} /> },
              { label: 'API Keys', path: '/settings/api-keys', icon: <Key size={16} /> },
            ]}
          />

          <NavGroupItem
            title="Documentation"
            icon={<BookOpen size={18} />}
            items={[
              { label: 'Help', path: '/help', icon: <HelpCircle size={16} /> },
              { label: 'API Docs', path: '/docs/api', icon: <FileText size={16} /> },
            ]}
          />

          <StyledNavLink to="/settings">
            <Settings size={18} />
            Settings
          </StyledNavLink>
        </NavSection>
      </SidebarContainer>
    </>
  );
};
