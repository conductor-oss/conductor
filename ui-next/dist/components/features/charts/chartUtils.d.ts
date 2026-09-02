import { FormattedHistoricalData, HistoricalData } from "types/MetricsTypes";
export declare enum ChartType {
    REQUESTS = "requests",
    LATENCY = "latency",
    ERRORS = "errors",
    CACHE = "cache"
}
export declare enum ThemeMode {
    DARK = "dark",
    LIGHT = "light"
}
export interface BaseChartProps {
    historicalData?: HistoricalData[];
}
export interface LatencyChartProps extends BaseChartProps {
    visiblePercentiles?: Record<string, boolean>;
}
export declare function formatHistoricalData(data?: HistoricalData[]): {
    time: Date | null;
    requests: number;
    errors: number;
    p50: number;
    p95: number;
    p99: number;
    errorsByStatusCode: Record<string, number>;
    p75: number;
    p90: number;
    errorCount: number;
    requestCount: number;
    cacheHits: number;
    cacheMisses: number;
}[];
export declare const formatXAxis: (tickItem: Date | string | number, index: number) => string;
export declare const getTimeTicks: (data: FormattedHistoricalData[]) => number[];
export declare const useChartColors: () => {
    primary: string;
    secondary: string;
    tertiary: string;
    error: string;
    success: string;
    grid: string;
    text: string;
    isDark: boolean;
};
