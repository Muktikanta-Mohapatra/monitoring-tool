import styled from 'styled-components';

interface CardProps {
  $variant?: 'default' | 'info' | 'success' | 'warning' | 'error';
  $clickable?: boolean;
}

export const Card = styled.div<CardProps>`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme, $variant }) => {
    switch ($variant) {
      case 'success': return theme.colors.semantic.success;
      case 'warning': return theme.colors.semantic.warning;
      case 'error': return theme.colors.semantic.error;
      case 'info': return theme.colors.semantic.info;
      default: return theme.colors.border.default;
    }
  }};
  border-radius: ${({ theme }) => theme.radius.md};
  padding: ${({ theme }) => theme.spacing.lg};
  box-shadow: ${({ theme }) => theme.shadows.sm};
  transition: all ${({ theme }) => theme.transition.normal};

  ${({ $clickable }) => $clickable && `
    cursor: pointer;
    
    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
    }
  `}
`;

export const CardHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: ${({ theme }) => theme.spacing.lg};
  padding-bottom: ${({ theme }) => theme.spacing.md};
  border-bottom: 1px solid ${({ theme }) => theme.colors.border.default};
`;

export const CardTitle = styled.h3`
  font-size: 16px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.primary};
  margin: 0;
`;

export const CardContent = styled.div`
  color: ${({ theme }) => theme.colors.text.secondary};
`;

export const CardFooter = styled.div`
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: ${({ theme }) => theme.spacing.sm};
  margin-top: ${({ theme }) => theme.spacing.lg};
  padding-top: ${({ theme }) => theme.spacing.md};
  border-top: 1px solid ${({ theme }) => theme.colors.border.default};
`;
