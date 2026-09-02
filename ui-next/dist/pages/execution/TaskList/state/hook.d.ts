import { ActorRef } from "xstate";
import { SelectableStatus, TaskListMachineEvents } from "./types";
import { ExecutionTask } from "types";
export declare const useTaskListActor: (taskListActor: ActorRef<TaskListMachineEvents>) => ({
    taskListPage: any;
    statusFilter: any;
    totalHits: any;
    isFetching: any;
    rowsPerPage: any;
    summary: any;
    handleChangeStatus?: undefined;
    handleChangePage?: undefined;
    handleChangeRowsPerPage?: undefined;
    handleSelectTask?: undefined;
} | {
    handleChangeStatus: (status?: SelectableStatus[]) => void;
    handleChangePage: (page: number) => void;
    handleChangeRowsPerPage: (rowsPerPage: number) => void;
    handleSelectTask: (selectedTask: ExecutionTask) => void;
    taskListPage?: undefined;
    statusFilter?: undefined;
    totalHits?: undefined;
    isFetching?: undefined;
    rowsPerPage?: undefined;
    summary?: undefined;
})[];
