import { IdempotencyStrategyEnum } from "./types";
export type BuildQueryOutput = {
    input?: Record<string, unknown>;
    taskToDomain?: object;
    name: string;
    version: string | null;
    correlationId: string;
    idempotencyKey?: string;
    idempotencyStrategy?: IdempotencyStrategyEnum;
};
interface RunWorkflowApiSearchModalProps {
    buildQueryOutput: BuildQueryOutput;
    onClose: () => void;
}
declare const RunWorkflowApiSearchModal: ({ onClose, buildQueryOutput, }: RunWorkflowApiSearchModalProps) => import("react").JSX.Element;
export { RunWorkflowApiSearchModal };
