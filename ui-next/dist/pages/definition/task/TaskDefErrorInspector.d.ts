declare const TaskDefErrorInspector: ({ error, title, }: {
    error: {
        [key: string]: {
            message: string;
        };
    };
    title?: string;
}) => import("react").JSX.Element;
export default TaskDefErrorInspector;
