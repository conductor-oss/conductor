import { BuildQueryOutput } from "./ApiSearchModalIntegration";
interface SchedulerApiSearchModalProps {
    buildQueryOutput: BuildQueryOutput;
    onClose: () => void;
}
declare const SchedulerApiSearchModal: ({ onClose, buildQueryOutput, }: SchedulerApiSearchModalProps) => import("react").JSX.Element;
export { SchedulerApiSearchModal };
