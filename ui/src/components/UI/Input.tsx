import styled from 'styled-components';
import React from 'react';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  icon?: React.ReactNode;
  fullWidth?: boolean;
}

const InputWrapper = styled.div<{ $fullWidth?: boolean }>`
  display: flex;
  flex-direction: column;
  gap: ${({ theme }) => theme.spacing.sm};
  width: ${({ $fullWidth }) => ($fullWidth ? '100%' : 'auto')};
`;

const Label = styled.label`
  font-size: 13px;
  font-weight: 500;
  color: ${({ theme }) => theme.colors.text.primary};
`;

const Required = styled.span`
  color: ${({ theme }) => theme.colors.semantic.error};
  margin-left: 4px;
`;

const InputContainer = styled.div`
  position: relative;
  display: flex;
  align-items: center;
`;

const IconWrapper = styled.div`
  position: absolute;
  left: 12px;
  color: ${({ theme }) => theme.colors.text.tertiary};
  display: flex;
  align-items: center;
  pointer-events: none;
`;

const StyledInput = styled.input<{ $hasIcon?: boolean; $hasError?: boolean }>`
  width: 100%;
  height: 36px;
  padding: 8px 12px;
  padding-left: ${({ $hasIcon }) => ($hasIcon ? '36px' : '12px')};
  background: ${({ theme }) => theme.colors.bg.tertiary};
  border: 1px solid ${({ theme, $hasError }) => 
    $hasError ? theme.colors.semantic.error : theme.colors.border.light};
  border-radius: ${({ theme }) => theme.radius.md};
  font-size: 14px;
  color: ${({ theme }) => theme.colors.text.primary};
  transition: all ${({ theme }) => theme.transition.fast};

  &::placeholder {
    color: ${({ theme }) => theme.colors.text.tertiary};
  }

  &:hover:not(:disabled) {
    border-color: ${({ theme }) => theme.colors.border.hover};
  }

  &:focus {
    outline: none;
    border-color: ${({ theme, $hasError }) => 
      $hasError ? theme.colors.semantic.error : theme.colors.accent.primary};
    box-shadow: 0 0 0 2px ${({ $hasError }) => 
      $hasError ? 'rgba(255, 84, 89, 0.1)' : 'rgba(50, 184, 198, 0.1)'};
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.bg.secondary};
    color: ${({ theme }) => theme.colors.text.tertiary};
    cursor: not-allowed;
    opacity: 0.6;
  }
`;

const HelperText = styled.span<{ $isError?: boolean }>`
  font-size: 12px;
  color: ${({ theme, $isError }) => 
    $isError ? theme.colors.semantic.error : theme.colors.text.secondary};
`;

export const Input: React.FC<InputProps> = ({
  label,
  error,
  helperText,
  icon,
  fullWidth = true,
  required,
  ...props
}) => {
  return (
    <InputWrapper $fullWidth={fullWidth}>
      {label && (
        <Label>
          {label}
          {required && <Required>*</Required>}
        </Label>
      )}
      <InputContainer>
        {icon && <IconWrapper>{icon}</IconWrapper>}
        <StyledInput 
          $hasIcon={!!icon} 
          $hasError={!!error}
          required={required}
          {...props} 
        />
      </InputContainer>
      {(error || helperText) && (
        <HelperText $isError={!!error}>{error || helperText}</HelperText>
      )}
    </InputWrapper>
  );
};

export const Textarea = styled.textarea<{ $hasError?: boolean }>`
  width: 100%;
  min-height: 100px;
  padding: 12px;
  background: ${({ theme }) => theme.colors.bg.tertiary};
  border: 1px solid ${({ theme, $hasError }) => 
    $hasError ? theme.colors.semantic.error : theme.colors.border.light};
  border-radius: ${({ theme }) => theme.radius.md};
  font-size: 14px;
  font-family: inherit;
  color: ${({ theme }) => theme.colors.text.primary};
  resize: vertical;
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
