import { TerminateTaskDef, Crumb } from "types";
import { NodeData } from "reaflow";
import { NodeTaskData } from "./types";
export declare const taskToTerminateNode: (task: TerminateTaskDef, crumbs?: Crumb[]) => NodeData<NodeTaskData<TerminateTaskDef>>;
