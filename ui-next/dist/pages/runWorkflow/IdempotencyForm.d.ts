export interface IdempotencyFormProps {
    idempotencyValues: {
        idempotencyKey?: string;
        idempotencyStrategy?: IdempotencyStrategyEnum;
    };
    onChange: (data: {
        idempotencyKey: string;
        idempotencyStrategy?: IdempotencyStrategyEnum;
    }) => void;
    showStrategyInitially?: boolean;
}
declare enum IdempotencyStrategyEnum {
    FAIL = "FAIL",
    RETURN_EXISTING = "RETURN_EXISTING",
    FAIL_ON_RUNNING = "FAIL_ON_RUNNING"
}
export default function IdempotencyForm({ idempotencyValues, onChange, showStrategyInitially, }: IdempotencyFormProps): import("react").JSX.Element;
export {};
