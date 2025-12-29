import styled from 'styled-components';
import React from 'react';

interface StatCardProps {
  title: string;
  value: string | number;
  change?: string;
  changeType?: 'positive' | 'negative' | 'neutral';
  icon: React.ReactNode;
  color?: string;
}

const CardContainer = styled.div`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-radius: ${({ theme }) => theme.radius.md};
  padding: ${({ theme }) => theme.spacing.lg};
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  transition: all ${({ theme }) => theme.transition.normal};

  &:hover {
    transform: translateY(-2px);
    box-shadow: ${({ theme }) => theme.shadows.md};
  }
`;

const ContentSection = styled.div`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.xs};
`;

const Title = styled.span`
  font-size: 13px;
  color: ${({ theme }) => theme.colors.text.secondary};
  text-transform: uppercase;
  letter-spacing: 0.5px;
`;

const Value = styled.span`
  font-size: 28px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};
  transition: all 0.3s ease-in-out;
  display: inline-block;
`;

const Change = styled.span<{ $type?: 'positive' | 'negative' | 'neutral' }>`
  font-size: 12px;
  color: ${({ theme, $type }) => {
    switch ($type) {
      case 'positive': return theme.colors.semantic.success;
      case 'negative': return theme.colors.semantic.error;
      default: return theme.colors.text.secondary;
    }
  }};
  transition: color 0.3s ease-in-out;
`;

const IconWrapper = styled.div<{ $color?: string }>`
  width: 48px;
  height: 48px;
  border-radius: ${({ theme }) => theme.radius.md};
  background: ${({ $color }) => $color ? `${$color}20` : 'rgba(50, 184, 198, 0.1)'};
  display: flex;
  align-items: center;
  justify-content: center;
  color: ${({ $color, theme }) => $color || theme.colors.accent.primary};
`;

export const StatCard: React.FC<StatCardProps> = ({
  title,
  value,
  change,
  changeType = 'neutral',
  icon,
  color,
}) => {
  return (
    <CardContainer>
      <ContentSection>
        <Title>{title}</Title>
        <Value>{value}</Value>
        {change && <Change $type={changeType}>{change}</Change>}
      </ContentSection>
      <IconWrapper $color={color}>{icon}</IconWrapper>
    </CardContainer>
  );
};
