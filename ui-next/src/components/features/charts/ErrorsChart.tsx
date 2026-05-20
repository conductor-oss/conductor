import { Box, Paper, Stack, Typography } from "@mui/material";
import _mergeWith from "lodash/mergeWith";
import _sum from "lodash/sum";
import { getHttpStatusText } from "utils/httpStatus";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  BaseChartProps,
  formatHistoricalData,
  formatXAxis,
  getTimeTicks,
  useChartColors,
} from "./chartUtils";

export function ErrorsChart({ historicalData = [] }: BaseChartProps) {
  const colors = useChartColors();
  const data = formatHistoricalData(historicalData);
  const xTicks = getTimeTicks(data);

  const errorBreakdown: Record<string, number> = _mergeWith(
    {},
    ...data.map((point) => point.errorsByStatusCode || {}),
    (objValue: number, srcValue: number) => (objValue || 0) + (srcValue || 0),
  );

  const totalErrors = _sum(Object.values(errorBreakdown));

  return (
    <Stack spacing={2}>
      <ResponsiveContainer width="100%" height={350}>
        <AreaChart
          data={data}
          margin={{ left: 16, right: 16, top: 16, bottom: 36 }}
        >
          <CartesianGrid
            strokeDasharray="3 3"
            stroke={colors.grid}
            vertical={false}
          />
          <XAxis
            dataKey="time"
            stroke={colors.text}
            tickFormatter={formatXAxis}
            tick={{ fontSize: 11 }}
            interval={0}
            height={38}
            domain={[xTicks[0], xTicks[xTicks.length - 1]]}
            ticks={xTicks}
            minTickGap={50}
            type="number"
            scale="time"
          />
          <YAxis
            tickFormatter={(value) => `${(value * 100).toFixed(1)}%`}
            stroke={colors.text}
            width={60}
          />
          <Tooltip
            formatter={(value) => `${(Number(value) * 100).toFixed(2)}%`}
            contentStyle={{
              backgroundColor: colors.isDark ? "#1f2937" : "#fff",
              borderColor: colors.grid,
            }}
          />
          <Area
            type="monotone"
            dataKey="errors"
            stroke={colors.error}
            fill={colors.error}
            fillOpacity={0.15}
            strokeWidth={2}
            dot={false}
          />
        </AreaChart>
      </ResponsiveContainer>

      {totalErrors > 0 && (
        <Paper
          elevation={0}
          sx={{
            p: 2,
            backgroundColor: colors.isDark
              ? "rgba(255, 255, 255, 0.05)"
              : "rgba(0, 0, 0, 0.02)",
            borderRadius: 1,
          }}
        >
          <Typography variant="h6" gutterBottom>
            Error Breakdown
          </Typography>
          <Typography variant="body2" color="text.secondary" gutterBottom>
            Types of errors encountered
          </Typography>
          <Stack spacing={2} sx={{ mt: 2 }}>
            {Object.entries(errorBreakdown).map(([statusCode, count]) => {
              const percentage = ((count as number) / totalErrors) * 100;
              const getStatusColor = (code: string) => {
                if (code.startsWith("5")) return colors.error;
                if (code.startsWith("4")) return colors.tertiary;
                return colors.secondary;
              };

              return (
                <Box key={statusCode}>
                  <Box
                    sx={{
                      display: "flex",
                      justifyContent: "space-between",
                      mb: 0.5,
                    }}
                  >
                    <Typography variant="body2" fontWeight="medium">
                      {statusCode} {getHttpStatusText(statusCode)}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {count} occurrences ({percentage.toFixed(1)}%)
                    </Typography>
                  </Box>
                  <Box
                    sx={{
                      height: 8,
                      width: "100%",
                      bgcolor: colors.isDark
                        ? "rgba(255, 255, 255, 0.1)"
                        : "rgba(0, 0, 0, 0.1)",
                      borderRadius: 1,
                      overflow: "hidden",
                    }}
                  >
                    <Box
                      sx={{
                        height: "100%",
                        width: `${percentage}%`,
                        bgcolor: getStatusColor(statusCode),
                        borderRadius: 1,
                      }}
                    />
                  </Box>
                </Box>
              );
            })}
          </Stack>
        </Paper>
      )}
    </Stack>
  );
}
