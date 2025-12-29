import React from 'react';
import type { ReactNode, ReactElement } from 'react';
import styled from 'styled-components';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

const ErrorContainer = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: ${({ theme }) => theme.colors.bg.primary};
  padding: ${({ theme }) => theme.spacing['2xl']};
`;

const ErrorContent = styled.div`
  max-width: 500px;
  text-align: center;
`;

const ErrorTitle = styled.h1`
  font-size: 32px;
  font-weight: 600;
  color: ${({ theme }) => theme.colors.semantic.error};
  margin: 0 0 ${({ theme }) => theme.spacing.md} 0;
`;

const ErrorMessage = styled.p`
  font-size: 16px;
  color: ${({ theme }) => theme.colors.text.secondary};
  margin: 0 0 ${({ theme }) => theme.spacing.lg} 0;
  line-height: 1.5;
`;

const ErrorDetails = styled.pre`
  background: ${({ theme }) => theme.colors.bg.secondary};
  border: 1px solid ${({ theme }) => theme.colors.border.default};
  border-radius: ${({ theme }) => theme.radius.md};
  padding: ${({ theme }) => theme.spacing.lg};
  text-align: left;
  font-size: 12px;
  color: ${({ theme }) => theme.colors.text.tertiary};
  overflow-x: auto;
  margin: ${({ theme }) => theme.spacing.lg} 0;
  max-height: 200px;
  overflow-y: auto;
`;

const ResetButton = styled.button`
  padding: ${({ theme }) => `${theme.spacing.md} ${theme.spacing.lg}`};
  background: ${({ theme }) => theme.colors.accent.primary};
  color: white;
  border: none;
  border-radius: ${({ theme }) => theme.radius.md};
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all ${({ theme }) => theme.transition.fast};

  &:hover {
    background: ${({ theme }) => theme.colors.accent.secondary};
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(50, 184, 198, 0.3);
  }

  &:active {
    transform: translateY(0);
  }
`;

export class ErrorBoundary extends React.Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
    };
  }

  static getDerivedStateFromError(error: Error): State {
    return {
      hasError: true,
      error,
    };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error('ErrorBoundary caught an error:', error);
    console.error('Error Info:', errorInfo);
  }

  handleReset = () => {
    this.setState({
      hasError: false,
      error: null,
    });
    window.location.href = '/';
  };

  render(): ReactElement {
    if (this.state.hasError) {
      return (
        <ErrorContainer>
          <ErrorContent>
            <ErrorTitle>Oops! Something went wrong</ErrorTitle>
            <ErrorMessage>
              We encountered an unexpected error. Please try refreshing the page or contact support if the problem persists.
            </ErrorMessage>
            {this.state.error && (
              <ErrorDetails>
                {this.state.error.toString()}
              </ErrorDetails>
            )}
            <ResetButton onClick={this.handleReset}>
              Return to Dashboard
            </ResetButton>
          </ErrorContent>
        </ErrorContainer>
      );
    }

    return <>{this.props.children}</>;
  }
}
