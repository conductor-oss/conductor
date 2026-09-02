export interface ErrorProps {
    title: string;
    description: string;
    buttonText?: string;
    onClick?: () => void;
    errorLogo?: string;
    error?: string;
    secondaryButton?: {
        buttonText?: string;
        onClick?: () => void;
    };
}
export default function Error({ title, description, buttonText, onClick, error, }: ErrorProps): import("react").JSX.Element;
