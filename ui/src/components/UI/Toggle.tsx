import styled from 'styled-components';
import React from 'react';

interface ToggleProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label?: string;
  disabled?: boolean;
}

const ToggleWrapper = styled.label<{ $disabled?: boolean }>`
  display: flex;
  align-items: center;
  gap: ${({ theme }) => theme.spacing.md};
  cursor: ${({ $disabled }) => ($disabled ? 'not-allowed' : 'pointer')};
  opacity: ${({ $disabled }) => ($disabled ? 0.5 : 1)};
`;

const ToggleTrack = styled.div<{ $checked: boolean }>`
  width: 44px;
  height: 24px;
  background: ${({ theme, $checked }) => 
    $checked ? theme.colors.accent.primary : theme.colors.border.light};
  border-radius: 12px;
  position: relative;
  transition: background ${({ theme }) => theme.transition.normal};
`;

const ToggleKnob = styled.div<{ $checked: boolean }>`
  width: 20px;
  height: 20px;
  background: ${({ theme }) => theme.colors.text.primary};
  border-radius: 50%;
  position: absolute;
  top: 2px;
  left: ${({ $checked }) => ($checked ? '22px' : '2px')};
  transition: left ${({ theme }) => theme.transition.normal};
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
`;

const ToggleLabel = styled.span`
  font-size: 13px;
  color: ${({ theme }) => theme.colors.text.primary};
`;

const HiddenInput = styled.input`
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
`;

export const Toggle: React.FC<ToggleProps> = ({
  checked,
  onChange,
  label,
  disabled = false,
}) => {
  return (
    <ToggleWrapper $disabled={disabled}>
      <HiddenInput
        type="checkbox"
        checked={checked}
        onChange={(e) => !disabled && onChange(e.target.checked)}
        disabled={disabled}
      />
      <ToggleTrack $checked={checked}>
        <ToggleKnob $checked={checked} />
      </ToggleTrack>
      {label && <ToggleLabel>{label}</ToggleLabel>}
    </ToggleWrapper>
  );
};
