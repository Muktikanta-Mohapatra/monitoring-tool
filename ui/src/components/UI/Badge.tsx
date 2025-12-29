import styled from 'styled-components';

interface BadgeProps {
  $variant?: 'default' | 'success' | 'warning' | 'error' | 'info';
  $size?: 'sm' | 'md';
}

export const Badge = styled.span<BadgeProps>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: ${({ $size }) => $size === 'sm' ? '2px 6px' : '4px 8px'};
  font-size: ${({ $size }) => $size === 'sm' ? '10px' : '12px'};
  font-weight: 500;
  border-radius: ${({ theme }) => theme.radius.sm};
  white-space: nowrap;

  ${({ theme, $variant }) => {
    switch ($variant) {
      case 'success':
        return `
          background: rgba(61, 204, 113, 0.15);
          color: ${theme.colors.semantic.success};
        `;
      case 'warning':
        return `
          background: rgba(240, 173, 78, 0.15);
          color: ${theme.colors.semantic.warning};
        `;
      case 'error':
        return `
          background: rgba(255, 84, 89, 0.15);
          color: ${theme.colors.semantic.error};
        `;
      case 'info':
        return `
          background: rgba(90, 177, 209, 0.15);
          color: ${theme.colors.semantic.info};
        `;
      default:
        return `
          background: ${theme.colors.bg.tertiary};
          color: ${theme.colors.text.secondary};
        `;
    }
  }}
`;

export const StatusDot = styled.span<{ $status: 'online' | 'offline' | 'warning' }>`
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  margin-right: 6px;
  
  ${({ theme, $status }) => {
    switch ($status) {
      case 'online':
        return `background: ${theme.colors.semantic.success};`;
      case 'warning':
        return `background: ${theme.colors.semantic.warning};`;
      case 'offline':
        return `background: ${theme.colors.semantic.error};`;
    }
  }}
`;
