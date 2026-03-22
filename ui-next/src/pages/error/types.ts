export interface ParsedErrorMessage {
  code: string;
  description: string;
  title: string;
  message?: string;
  buttonText?: string;
  onClick?: () => void;
  errorLogo?: string;
  secondaryButton?: {
    buttonText?: string;
    onClick?: () => void;
  };
}
