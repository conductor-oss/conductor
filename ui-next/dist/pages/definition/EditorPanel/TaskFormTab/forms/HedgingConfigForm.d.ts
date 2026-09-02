interface HedgingConfigFormProp {
    hedgingConfig?: {
        maxAttempts?: number;
    };
    onChange: (value: any) => void;
}
declare function HedgingConfigForm({ hedgingConfig, onChange, }: HedgingConfigFormProp): import("react").JSX.Element;
export default HedgingConfigForm;
