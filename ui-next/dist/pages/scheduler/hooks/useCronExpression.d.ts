export interface UseCronExpressionReturn {
    cronExpression: string;
    setCronExpression: (value: string, timezone: string) => void;
    futureMatches: string[];
    humanizedExpression: string;
    cronError: string | undefined;
    highlightedPart: number | null;
    setHighlightedPart: (part: number | null) => void;
}
export declare function useCronExpression(initialCronExpression?: string, timezone?: string, onError?: (error: string | undefined) => void): UseCronExpressionReturn;
