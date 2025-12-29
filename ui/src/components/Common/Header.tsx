import styled from 'styled-components';
import React from 'react';
import { Search, Bell, Settings, Menu, User } from 'lucide-react';

interface HeaderProps {
  onMenuClick: () => void;
}

const HeaderContainer = styled.header`
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: ${({ theme }) => theme.sizes.headerHeight};
  background: ${({ theme }) => theme.colors.bg.header};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border.default};
  display: flex;
  align-items: center;
  padding: 0 ${({ theme }) => theme.spacing.lg};
  z-index: 1000;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);

  @media (max-width: 480px) {
    padding: 0 ${({ theme }) => theme.spacing.md};
  }
`;

const LogoSection = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.md};
  width: 250px;
  flex-shrink: 0;

  @media (max-width: 1024px) {
    width: auto;
  }
`;

const MenuButton = styled.button`
  display: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: ${({ theme }) => theme.radius.sm};
  color: ${({ theme }) => theme.colors.text.secondary};
  transition: all ${({ theme }) => theme.transition.fast};

  &:hover {
    background: ${({ theme }) => theme.colors.bg.tertiary};
    color: ${({ theme }) => theme.colors.text.primary};
  }

  @media (max-width: 1024px) {
    display: flex;
  }
`;

const Logo = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.sm};
`;

const LogoIcon = styled.div`
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #32B8C6, #2186A0);
  border-radius: ${({ theme }) => theme.radius.sm};
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 700;
  font-size: 14px;
`;

const LogoText = styled.span`
  font-size: 14px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};

  @media (max-width: 768px) {
    display: none;
  }

  @media (max-width: 480px) {
    display: none;
  }
`;

const SearchSection = styled.div`
  flex: 1;
  max-width: 400px;
  margin: 0 ${({ theme }) => theme.spacing.xl};

  @media (max-width: 768px) {
    display: none;
  }

  @media (max-width: 480px) {
    display: none;
  }
`;

const SearchContainer = styled.div`
  position: relative;
  display: flex;
  align-items: center;
`;

const SearchIcon = styled.div`
  position: absolute;
  left: 12px;
  color: ${({ theme }) => theme.colors.text.tertiary};
  display: flex;
  align-items: center;
  pointer-events: none;
`;

const SearchInput = styled.input`
  width: 100%;
  height: 40px;
  padding: 0 12px 0 40px;
  background: ${({ theme }) => theme.colors.bg.tertiary};
  border: 1px solid ${({ theme }) => theme.colors.border.light};
  border-radius: ${({ theme }) => theme.radius.md};
  font-size: 14px;
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

const RightSection = styled.div`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.lg};
  margin-left: auto;

  @media (max-width: 480px) {
    gap: ${({ theme }) => theme.spacing.sm};
  }
`;

const IconButton = styled.button`
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: ${({ theme }) => theme.radius.sm};
  color: ${({ theme }) => theme.colors.text.secondary};
  transition: all ${({ theme }) => theme.transition.fast};

  &:hover {
    background: ${({ theme }) => theme.colors.bg.tertiary};
    color: ${({ theme }) => theme.colors.text.primary};
  }
`;

const NotificationBadge = styled.span`
  position: absolute;
  top: 4px;
  right: 4px;
  width: 8px;
  height: 8px;
  background: ${({ theme }) => theme.colors.semantic.error};
  border-radius: 50%;
`;

const UserAvatar = styled.div`
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #32B8C6, #2186A0);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  cursor: pointer;
  transition: transform ${({ theme }) => theme.transition.fast};

  &:hover {
    transform: scale(1.05);
  }
`;

export const Header: React.FC<HeaderProps> = ({ onMenuClick }) => {
  return (
    <HeaderContainer>
      <LogoSection>
        <MenuButton onClick={onMenuClick}>
          <Menu size={20} />
        </MenuButton>
        <Logo>
          <LogoIcon>LF</LogoIcon>
          <LogoText>Log Forwarder</LogoText>
        </Logo>
      </LogoSection>

      <SearchSection>
        <SearchContainer>
          <SearchIcon>
            <Search size={20} />
          </SearchIcon>
          <SearchInput placeholder="Search logs, dashboards, hosts..." />
        </SearchContainer>
      </SearchSection>

      <RightSection>
        <IconButton>
          <Bell size={20} />
          <NotificationBadge />
        </IconButton>
        <IconButton>
          <Settings size={20} />
        </IconButton>
        <UserAvatar>
          <User size={18} />
        </UserAvatar>
      </RightSection>
    </HeaderContainer>
  );
};
