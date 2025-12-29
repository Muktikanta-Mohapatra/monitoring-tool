import styled from 'styled-components';

export const TableContainer = styled.div`
  width: 100%;
  overflow-x: auto;
  border-radius: ${({ theme }) => theme.radius.md};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
`;

export const Table = styled.table`
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
`;

export const TableHead = styled.thead`
  background: ${({ theme }) => theme.colors.bg.tertiary};
`;

export const TableBody = styled.tbody`
  background: ${({ theme }) => theme.colors.bg.secondary};
`;

export const TableRow = styled.tr<{ $clickable?: boolean }>`
  border-bottom: 1px solid ${({ theme }) => theme.colors.border.default};
  transition: background ${({ theme }) => theme.transition.fast};

  ${({ $clickable, theme }) => $clickable && `
    cursor: pointer;
    
    &:hover {
      background: ${theme.colors.bg.tertiary};
    }
  `}

  &:last-child {
    border-bottom: none;
  }
`;

export const TableHeader = styled.th<{ $sortable?: boolean }>`
  text-align: left;
  padding: ${({ theme }) => theme.spacing.md} ${({ theme }) => theme.spacing.lg};
  font-weight: 600;
  color: ${({ theme }) => theme.colors.text.secondary};
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  white-space: nowrap;

  ${({ $sortable }) => $sortable && `
    cursor: pointer;
    user-select: none;
    
    &:hover {
      color: #FFFFFF;
    }
  `}
`;

export const TableCell = styled.td`
  padding: ${({ theme }) => theme.spacing.md} ${({ theme }) => theme.spacing.lg};
  color: ${({ theme }) => theme.colors.text.primary};
  vertical-align: middle;
`;

export const TableEmpty = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: ${({ theme }) => theme.spacing['3xl']};
  color: ${({ theme }) => theme.colors.text.secondary};
  text-align: center;
  gap: ${({ theme }) => theme.spacing.md};
`;
