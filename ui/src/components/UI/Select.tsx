import styled from 'styled-components';
import React from 'react';
import { ChevronDown } from 'lucide-react';

interface Option {
  value: string;
  label: string;
}

interface SelectProps {
  options: Option[];
  value: string;
  onChange: (value: string) => void;
  label?: string;
  placeholder?: string;
  disabled?: boolean;
  error?: string;
  fullWidth?: boolean;
}

const SelectWrapper = styled.div<{ $fullWidth?: boolean }>`
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

const SelectContainer = styled.div`
  position: relative;
  display: flex;
  align-items: center;
`;

const StyledSelect = styled.select<{ $hasError?: boolean }>`
  width: 100%;
  height: 36px;
  padding: 8px 36px 8px 12px;
  background: ${({ theme }) => theme.colors.bg.tertiary};
  border: 1px solid ${({ theme, $hasError }) => 
    $hasError ? theme.colors.semantic.error : theme.colors.border.light};
  border-radius: ${({ theme }) => theme.radius.md};
  font-size: 14px;
  color: ${({ theme }) => theme.colors.text.primary};
  cursor: pointer;
  appearance: none;
  transition: all ${({ theme }) => theme.transition.fast};

  &:hover:not(:disabled) {
    border-color: ${({ theme }) => theme.colors.border.hover};
  }

  &:focus {
    outline: none;
    border-color: ${({ theme }) => theme.colors.accent.primary};
    box-shadow: 0 0 0 2px rgba(50, 184, 198, 0.1);
  }

  &:disabled {
    background: ${({ theme }) => theme.colors.bg.secondary};
    color: ${({ theme }) => theme.colors.text.tertiary};
    cursor: not-allowed;
    opacity: 0.6;
  }
`;

const IconWrapper = styled.div`
  position: absolute;
  right: 12px;
  pointer-events: none;
  color: ${({ theme }) => theme.colors.text.tertiary};
  display: flex;
  align-items: center;
`;

const ErrorText = styled.span`
  font-size: 12px;
  color: ${({ theme }) => theme.colors.semantic.error};
`;

export const Select: React.FC<SelectProps> = ({
  options,
  value,
  onChange,
  label,
  placeholder = 'Select...',
  disabled = false,
  error,
  fullWidth = true,
}) => {
  return (
    <SelectWrapper $fullWidth={fullWidth}>
      {label && <Label>{label}</Label>}
      <SelectContainer>
        <StyledSelect
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          $hasError={!!error}
        >
          <option value="" disabled>
            {placeholder}
          </option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </StyledSelect>
        <IconWrapper>
          <ChevronDown size={16} />
        </IconWrapper>
      </SelectContainer>
      {error && <ErrorText>{error}</ErrorText>}
    </SelectWrapper>
  );
};
