import { type BuildQueryOutput } from "./agentSearchCode";
interface ApiSearchModalIntegrationProps {
    buildQueryOutput: BuildQueryOutput;
    onClose: () => void;
}
declare const ApiSearchModalIntegration: ({ onClose, buildQueryOutput, }: ApiSearchModalIntegrationProps) => import("react").JSX.Element;
export { ApiSearchModalIntegration };
export type { BuildQueryOutput };
