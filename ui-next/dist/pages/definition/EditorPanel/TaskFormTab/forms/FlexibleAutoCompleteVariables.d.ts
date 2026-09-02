declare const FlexibleAutoCompleteVariables: ({ options, value, onChange, label, }: {
    options: Array<string>;
    value?: string;
    onChange?: (newValues: string) => void;
    label?: string;
}) => import("react").JSX.Element;
export default FlexibleAutoCompleteVariables;
