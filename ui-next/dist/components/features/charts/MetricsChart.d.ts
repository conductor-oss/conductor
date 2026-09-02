import { HistoricalData } from "types/MetricsTypes";
import { ChartType } from ".";
interface MetricsChartProps {
    type: ChartType;
    historicalData?: HistoricalData[];
    visiblePercentiles?: Record<string, boolean>;
}
export declare function MetricsChart({ type, historicalData, visiblePercentiles, }: MetricsChartProps): import("react").JSX.Element | null;
export {};
