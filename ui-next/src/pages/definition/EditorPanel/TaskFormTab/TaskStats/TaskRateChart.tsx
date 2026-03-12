import { FunctionComponent } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Label,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { formatUnixTimeToTimeString } from "utils/date";
import { PrometheusRateData } from "./state";

export interface TaskRateChartProps {
  color: string;
  data: PrometheusRateData;
  label: string;
}

const dataToTimeCount = (data: PrometheusRateData) =>
  data.map(([timeStamp, count]) => ({
    time: formatUnixTimeToTimeString(timeStamp),
    count,
  }));

export const TaskRateChart: FunctionComponent<TaskRateChartProps> = ({
  data,
  color = "#f34608",
  label,
}) => {
  return (
    <BarChart
      width={380}
      height={240}
      data={dataToTimeCount(data)}
      margin={{
        top: 5,
        right: 35,
        /* left: 20, */
        bottom: 5,
      }}
    >
      <CartesianGrid strokeDasharray="3 3" />
      <XAxis dataKey="time" tick={{ fontSize: 8 }}>
        <Label
          value={label}
          offset={1}
          position="insideBottom"
          fontSize={"10px"}
        />
      </XAxis>
      <YAxis tick={{ fontSize: 8 }} />
      <Tooltip />
      <Bar dataKey="count" fill={color} />
    </BarChart>
  );
};
