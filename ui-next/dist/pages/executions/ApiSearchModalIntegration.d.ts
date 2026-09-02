export type BuildQueryOutput = {
    query: string;
    freeText: string;
    start: number;
    size: number;
    sort: string;
};
interface ApiSearchModalIntegrationProps {
    buildQueryOutput: BuildQueryOutput;
    onClose: () => void;
}
declare const ApiSearchModalIntegration: ({ onClose, buildQueryOutput, }: ApiSearchModalIntegrationProps) => import("react").JSX.Element;
export { ApiSearchModalIntegration };
