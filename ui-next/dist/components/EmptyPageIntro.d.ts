import React from "react";
export interface EmptyPageIntroProps {
    id?: string;
    image?: string;
    videoUrl?: string;
    title: React.ReactNode;
    message: string;
    variant?: "featureDisabled" | "default";
    primaryAction?: {
        text: string;
        onClick: () => void;
        disabled?: boolean;
        startIcon?: React.ReactNode;
    };
    secondaryAction?: {
        text: string;
        onClick: () => void;
        disabled?: boolean;
        startIcon?: React.ReactNode;
    };
    footer?: string;
}
declare const EmptyPageIntro: ({ id, image, videoUrl, title, message, primaryAction, secondaryAction, footer, variant, }: EmptyPageIntroProps) => React.JSX.Element;
export default EmptyPageIntro;
