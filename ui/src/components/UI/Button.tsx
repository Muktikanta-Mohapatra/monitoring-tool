import styled, { css } from 'styled-components';
import React from 'react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  fullWidth?: boolean;
  loading?: boolean;
  icon?: React.ReactNode;
  iconPosition?: 'left' | 'right';
}

const buttonVariants = {
  primary: css`
    background-color: ${({ theme }) => theme.colors.accent.primary};
    color: ${({ theme }) => theme.colors.text.primary};
    border: none;

    &:hover:not(:disabled) {
      background-color: ${({ theme }) => theme.colors.accent.primaryHover};
    }

    &:active:not(:disabled) {
      background-color: ${({ theme }) => theme.colors.accent.primaryActive};
    }
  `,
  secondary: css`
    background-color: ${({ theme }) => theme.colors.bg.tertiary};
    color: ${({ theme }) => theme.colors.text.primary};
    border: 1px solid ${({ theme }) => theme.colors.border.light};

    &:hover:not(:disabled) {
      background-color: ${({ theme }) => theme.colors.bg.secondary};
    }
  `,
  outline: css`
    background-color: transparent;
    color: ${({ theme }) => theme.colors.accent.primary};
    border: 1px solid ${({ theme }) => theme.colors.accent.primary};

    &:hover:not(:disabled) {
      background-color: rgba(50, 184, 198, 0.1);
    }
  `,
  danger: css`
    background-color: ${({ theme }) => theme.colors.semantic.error};
    color: ${({ theme }) => theme.colors.text.primary};
    border: none;

    &:hover:not(:disabled) {
      background-color: #E63E45;
    }
  `,
};

const buttonSizes = {
  sm: css`
    height: 28px;
    padding: 4px 12px;
    font-size: 12px;
  `,
  md: css`
    height: 36px;
    padding: 8px 16px;
    font-size: 14px;
  `,
  lg: css`
    height: 44px;
    padding: 12px 24px;
    font-size: 15px;
  `,
};

const StyledButton = styled.button<{
  $variant?: 'primary' | 'secondary' | 'outline' | 'danger';
  $size?: 'sm' | 'md' | 'lg';
  $fullWidth?: boolean;
}>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: ${({ theme }) => theme.spacing.sm};
  border-radius: ${({ theme }) => theme.radius.sm};
  font-weight: 500;
  transition: all ${({ theme }) => theme.transition.fast};
  cursor: pointer;
  white-space: nowrap;

  ${({ $variant = 'primary' }) => buttonVariants[$variant]};
  ${({ $size = 'md' }) => buttonSizes[$size]};
  ${({ $fullWidth }) => $fullWidth && 'width: 100%;'}
  
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.colors.accent.primary};
    outline-offset: 2px;
  }
`;

const Spinner = styled.div`
  width: 16px;
  height: 16px;
  border: 2px solid transparent;
  border-top-color: currentColor;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;

  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
`;

export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  size = 'md',
  fullWidth = false,
  loading = false,
  icon,
  iconPosition = 'left',
  children,
  disabled,
  ...props
}) => {
  return (
    <StyledButton
      $variant={variant}
      $size={size}
      $fullWidth={fullWidth}
      disabled={disabled || loading}
      {...props}
    >
      {loading ? (
        <Spinner />
      ) : (
        <>
          {icon && iconPosition === 'left' && icon}
          {children}
          {icon && iconPosition === 'right' && icon}
        </>
      )}
    </StyledButton>
  );
};
