export declare const filterOptionByEvaluatorType: (evaluatorType?: string) => ({
    value: string;
    label: string;
    disabled?: undefined;
} | {
    value: string;
    label: string;
    disabled: boolean;
})[];
