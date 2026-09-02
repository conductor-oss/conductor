import { FunctionComponent } from "react";
import { PrometheusRateData } from "./state";
export interface TaskRateChartProps {
    color: string;
    data: PrometheusRateData;
    label: string;
}
export declare const TaskRateChart: FunctionComponent<TaskRateChartProps>;
