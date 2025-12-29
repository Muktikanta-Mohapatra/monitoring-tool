import styled, { keyframes } from 'styled-components';

const spin = keyframes`
  to {
    transform: rotate(360deg);
  }
`;

interface SpinnerProps {
  size?: 'sm' | 'md' | 'lg';
}

export const Spinner = styled.div<SpinnerProps>`
  width: ${({ size }) => {
    switch (size) {
      case 'sm': return '16px';
      case 'lg': return '48px';
      default: return '24px';
    }
  }};
  height: ${({ size }) => {
    switch (size) {
      case 'sm': return '16px';
      case 'lg': return '48px';
      default: return '24px';
    }
  }};
  border: 2px solid ${({ theme }) => theme.colors.bg.tertiary};
  border-top-color: ${({ theme }) => theme.colors.accent.primary};
  border-radius: 50%;
  animation: ${spin} 0.8s linear infinite;
`;

export const LoadingOverlay = styled.div`
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(15, 18, 17, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
`;

export const FullPageLoader = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  width: 100%;
`;
