import { HistoricalData } from "types/MetricsTypes";
import {
  CacheChart,
  ChartType,
  ErrorsChart,
  LatencyChart,
  RequestsChart,
} from ".";

interface MetricsChartProps {
  type: ChartType;
  historicalData?: HistoricalData[];
  visiblePercentiles?: Record<string, boolean>;
}

export function MetricsChart({
  type,
  historicalData = [],
  visiblePercentiles = { p50: true, p95: true, p99: true },
}: MetricsChartProps) {
  switch (type) {
    case ChartType.REQUESTS:
      return <RequestsChart historicalData={historicalData} />;

    case ChartType.LATENCY:
      return (
        <LatencyChart
          historicalData={historicalData}
          visiblePercentiles={visiblePercentiles}
        />
      );

    case ChartType.ERRORS:
      return <ErrorsChart historicalData={historicalData} />;

    case ChartType.CACHE:
      return <CacheChart historicalData={historicalData} />;

    default:
      return null;
  }
}
