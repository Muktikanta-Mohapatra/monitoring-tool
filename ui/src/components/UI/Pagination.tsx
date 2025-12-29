import styled from 'styled-components';
import React from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}

const PaginationContainer = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  gap: ${({ theme }) => theme.spacing.xs};
`;

const PageButton = styled.button<{ $active?: boolean }>`
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 32px;
  padding: 0 ${({ theme }) => theme.spacing.sm};
  border-radius: ${({ theme }) => theme.radius.sm};
  font-size: 13px;
  font-weight: 500;
  transition: all ${({ theme }) => theme.transition.fast};

  ${({ theme, $active }) => $active
    ? `
      background: ${theme.colors.accent.primary};
      color: ${theme.colors.text.primary};
    `
    : `
      background: transparent;
      color: ${theme.colors.text.secondary};
      
      &:hover:not(:disabled) {
        background: ${theme.colors.bg.tertiary};
        color: ${theme.colors.text.primary};
      }
    `}

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`;

const NavButton = styled(PageButton)`
  padding: 0 ${({ theme }) => theme.spacing.xs};
`;

const Ellipsis = styled.span`
  color: ${({ theme }) => theme.colors.text.tertiary};
  padding: 0 ${({ theme }) => theme.spacing.xs};
`;

export const Pagination: React.FC<PaginationProps> = ({
  currentPage,
  totalPages,
  onPageChange,
}) => {
  const getPageNumbers = () => {
    const pages: (number | string)[] = [];
    const showEllipsisStart = currentPage > 3;
    const showEllipsisEnd = currentPage < totalPages - 2;

    if (totalPages <= 7) {
      for (let i = 1; i <= totalPages; i++) {
        pages.push(i);
      }
    } else {
      pages.push(1);
      
      if (showEllipsisStart) {
        pages.push('...');
      }

      const start = Math.max(2, currentPage - 1);
      const end = Math.min(totalPages - 1, currentPage + 1);

      for (let i = start; i <= end; i++) {
        if (!pages.includes(i)) {
          pages.push(i);
        }
      }

      if (showEllipsisEnd) {
        pages.push('...');
      }

      if (!pages.includes(totalPages)) {
        pages.push(totalPages);
      }
    }

    return pages;
  };

  if (totalPages <= 1) return null;

  return (
    <PaginationContainer>
      <NavButton
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 1}
      >
        <ChevronLeft size={16} />
      </NavButton>

      {getPageNumbers().map((page, index) =>
        typeof page === 'number' ? (
          <PageButton
            key={index}
            $active={page === currentPage}
            onClick={() => onPageChange(page)}
          >
            {page}
          </PageButton>
        ) : (
          <Ellipsis key={index}>{page}</Ellipsis>
        )
      )}

      <NavButton
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage === totalPages}
      >
        <ChevronRight size={16} />
      </NavButton>
    </PaginationContainer>
  );
};
