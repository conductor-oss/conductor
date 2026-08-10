export interface HistoricalData {
  p50: number;
  p75: number;
  p90: number;
  p95: number;
  p99: number;
  errorCount: number;
  requestCount: number;
  cacheHits: number;
  cacheMisses: number;
  time: number;
  errorsByStatusCode?: Record<string, number>;
}

export interface FormattedHistoricalData extends Omit<HistoricalData, "time"> {
  time: Date | null;
  requests: number;
  errors: number;
  errorsByStatusCode: Record<string, number>;
}
