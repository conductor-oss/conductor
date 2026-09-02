type RateLimitConfigValue = {
    rateLimitKey: string;
    concurrentExecLimit: number;
};
interface RateLimitConfigFormProps {
    onChange: (value: RateLimitConfigValue) => void;
    value: RateLimitConfigValue;
}
export default function RateLimitConfigForm({ onChange, value, }: RateLimitConfigFormProps): import("react").JSX.Element;
export {};
