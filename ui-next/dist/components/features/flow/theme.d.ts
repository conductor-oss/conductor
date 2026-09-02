export declare const DO_WHILE_MIN_WIDTH = 570;
export declare const getFlowTheme: (mode?: string) => {
    nodeTypes: {
        DEFAULT: {
            width: number;
            height: number;
        };
        DO_WHILE: {
            padding: number;
            width: number;
            height: number;
            itemHeight: number;
        };
        SWITCH: {
            width: number;
            height: number;
        };
        DECISION: {
            width: number;
            height: number;
        };
        KAFKA_PUBLISH: {
            width: number;
            height: number;
        };
        JSON_JQ_TRANSFORM: {
            width: number;
            height: number;
        };
        HTTP: {
            width: number;
            height: number;
        };
        EVENT: {
            width: number;
            height: number;
        };
        WAIT: {
            width: number;
            height: number;
        };
        FORK_JOIN: {
            width: number;
            height: number;
        };
        FORK_JOIN_DYNAMIC: {
            width: number;
            height: number;
        };
        TERMINAL: {
            width: number;
            height: number;
        };
        SWITCH_JOIN: {
            width: number;
            height: number;
        };
        FORK_JOIN_COLLAPSED: {
            width: number;
            height: number;
        };
        TASK_SUMMARY: {
            width: number;
            height: number;
        };
    };
    taskStatusOutline: {
        COMPLETED: string;
        COMPLETED_WITH_ERRORS: string;
        CANCELED: string;
        FAILED: string;
        FAILED_WITH_TERMINAL_ERROR: string;
        TIMED_OUT: string;
        IN_PROGRESS: string;
        SCHEDULED: string;
        SKIPPED: string;
        PENDING: string;
        NULL: string;
    };
    graph: {
        handleBorderColor: string;
        handleSize: number;
        backgroundColor: string;
    };
    taskCard: {
        selected: {
            outlineColor: string;
            boxShadow: string;
        };
        operators: {
            background: string;
            text: string;
        };
        systemTasks: {
            background: string;
            color: string;
        };
        cardLabel: {
            background: string;
            color: string;
        };
        addPathButton: {
            background: string;
            text: string;
            hoverBackground: string;
        };
        deleteButton: {
            iconColor: string;
            background: string;
        };
        switchAdd: {
            iconColor: string;
            background: string;
        };
    };
    terminalTask: {
        color: string;
        background: string;
        border: string;
    };
    decisionOperator: {
        caseLabel: {
            defaultCaseBackground: string;
            background: string;
        };
    };
    edges: {
        default: {
            stroke: string;
            strokeWidth: number;
        };
        completed: {
            stroke: string;
            strokeWidth: number;
        };
    };
};
declare const theme: {
    nodeTypes: {
        DEFAULT: {
            width: number;
            height: number;
        };
        DO_WHILE: {
            padding: number;
            width: number;
            height: number;
            itemHeight: number;
        };
        SWITCH: {
            width: number;
            height: number;
        };
        DECISION: {
            width: number;
            height: number;
        };
        KAFKA_PUBLISH: {
            width: number;
            height: number;
        };
        JSON_JQ_TRANSFORM: {
            width: number;
            height: number;
        };
        HTTP: {
            width: number;
            height: number;
        };
        EVENT: {
            width: number;
            height: number;
        };
        WAIT: {
            width: number;
            height: number;
        };
        FORK_JOIN: {
            width: number;
            height: number;
        };
        FORK_JOIN_DYNAMIC: {
            width: number;
            height: number;
        };
        TERMINAL: {
            width: number;
            height: number;
        };
        SWITCH_JOIN: {
            width: number;
            height: number;
        };
        FORK_JOIN_COLLAPSED: {
            width: number;
            height: number;
        };
        TASK_SUMMARY: {
            width: number;
            height: number;
        };
    };
    taskStatusOutline: {
        COMPLETED: string;
        COMPLETED_WITH_ERRORS: string;
        CANCELED: string;
        FAILED: string;
        FAILED_WITH_TERMINAL_ERROR: string;
        TIMED_OUT: string;
        IN_PROGRESS: string;
        SCHEDULED: string;
        SKIPPED: string;
        PENDING: string;
        NULL: string;
    };
    graph: {
        handleBorderColor: string;
        handleSize: number;
        backgroundColor: string;
    };
    taskCard: {
        selected: {
            outlineColor: string;
            boxShadow: string;
        };
        operators: {
            background: string;
            text: string;
        };
        systemTasks: {
            background: string;
            color: string;
        };
        cardLabel: {
            background: string;
            color: string;
        };
        addPathButton: {
            background: string;
            text: string;
            hoverBackground: string;
        };
        deleteButton: {
            iconColor: string;
            background: string;
        };
        switchAdd: {
            iconColor: string;
            background: string;
        };
    };
    terminalTask: {
        color: string;
        background: string;
        border: string;
    };
    decisionOperator: {
        caseLabel: {
            defaultCaseBackground: string;
            background: string;
        };
    };
    edges: {
        default: {
            stroke: string;
            strokeWidth: number;
        };
        completed: {
            stroke: string;
            strokeWidth: number;
        };
    };
};
export default theme;
