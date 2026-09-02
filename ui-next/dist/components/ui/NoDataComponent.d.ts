import React from "react";
type NoDataComponentProps = {
    id?: string;
    title?: string;
    titleBg?: string;
    description: string;
    buttonText?: string;
    buttonHandler?: () => void;
    disableButton?: boolean;
    videoUrl?: string;
};
declare const NoDataComponent: ({ id, title, titleBg, buttonText, buttonHandler, description, disableButton, videoUrl, }: NoDataComponentProps) => React.JSX.Element;
export type { NoDataComponentProps };
export default NoDataComponent;
