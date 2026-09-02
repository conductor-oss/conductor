interface CronExpressionSectionProps {
    cronExpression: string;
    setCronExpression: (value: string, timezone: string) => void;
    futureMatches: string[];
    humanizedExpression: string;
    highlightedPart: number | null;
    getHighlightedPart: (value: string, selectionStart: number) => void;
    setHighlightedPart: (part: number | null) => void;
    selectedTemplate: string;
    setSelectedTemplate: (template: string) => void;
    timezone: string;
    setZoneId: (value: string) => void;
    cronError?: string;
    minWidthCronExpression: string;
}
export declare function CronExpressionSection({ cronExpression, setCronExpression, futureMatches, humanizedExpression, highlightedPart, getHighlightedPart, setHighlightedPart, selectedTemplate, setSelectedTemplate, timezone, setZoneId, cronError, minWidthCronExpression, }: CronExpressionSectionProps): import("react").JSX.Element;
export {};
