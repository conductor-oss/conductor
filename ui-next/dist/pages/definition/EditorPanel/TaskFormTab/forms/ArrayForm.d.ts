declare const ArrayForm: ({ title, addItemLabel, valueColumnLabel, value, onChange, }: {
    title: string;
    addItemLabel: string;
    valueColumnLabel: string;
    value?: Array<string>;
    onChange?: (newValues: Array<string>) => void;
}) => import("react").JSX.Element;
export default ArrayForm;
