interface ScheduleTimingSectionProps {
    scheduleStartTime: string | number;
    scheduleEndTime: string | number;
    handleScheduleStartTime: (value: number) => void;
    handleScheduleEndTime: (value: number) => void;
    taskToDomain: string;
    setWorkflowTasksToDomainState: (value: string) => void;
    paused: boolean;
    setCronPausedState: () => void;
}
export declare function ScheduleTimingSection({ scheduleStartTime, scheduleEndTime, handleScheduleStartTime, handleScheduleEndTime, taskToDomain, setWorkflowTasksToDomainState, paused, setCronPausedState, }: ScheduleTimingSectionProps): import("react").JSX.Element;
export {};
