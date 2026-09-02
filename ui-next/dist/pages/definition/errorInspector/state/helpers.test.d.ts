export declare const simpleNodeDiagram: ({
    id: string;
    text: string;
    ports: {
        id: string;
        width: number;
        height: number;
        side: string;
        disabled: boolean;
    }[];
    data: {
        task: {
            name: string;
            taskReferenceName: string;
            type: string;
            inputParameters?: undefined;
            decisionCases?: undefined;
            defaultCase?: undefined;
            forkTasks?: undefined;
            startDelay?: undefined;
            joinOn?: undefined;
            optional?: undefined;
            defaultExclusiveJoinTask?: undefined;
            asyncComplete?: undefined;
            loopOver?: undefined;
        };
        crumbs: never[];
        selected: boolean;
    };
    width: number;
    height: number;
} | {
    id: string;
    text: string;
    ports: {
        id: string;
        width: number;
        height: number;
        side: string;
        disabled: boolean;
    }[];
    data: {
        task: {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                http_request: {
                    uri: string;
                    method: string;
                    connectionTimeOut: number;
                    readTimeOut: number;
                };
            };
            type: string;
            decisionCases: {};
            defaultCase: never[];
            forkTasks: never[];
            startDelay: number;
            joinOn: never[];
            optional: boolean;
            defaultExclusiveJoinTask: never[];
            asyncComplete: boolean;
            loopOver: never[];
        };
        crumbs: {
            parent: null;
            ref: string;
            refIdx: number;
        }[];
        selected: boolean;
    };
    width: number;
    height: number;
} | {
    id: string;
    text: string;
    ports: {
        id: string;
        width: number;
        height: number;
        side: string;
        disabled: boolean;
    }[];
    data: {
        task: {
            name: string;
            taskReferenceName: string;
            type: string;
            inputParameters: {
                http_request: {
                    uri: string;
                    method: string;
                    connectionTimeOut: number;
                    readTimeOut: number;
                };
            };
            decisionCases?: undefined;
            defaultCase?: undefined;
            forkTasks?: undefined;
            startDelay?: undefined;
            joinOn?: undefined;
            optional?: undefined;
            defaultExclusiveJoinTask?: undefined;
            asyncComplete?: undefined;
            loopOver?: undefined;
        };
        crumbs: {
            parent: null;
            ref: string;
            refIdx: number;
        }[];
        selected: boolean;
    };
    width: number;
    height: number;
} | {
    id: string;
    text: string;
    data: {
        task: {
            name: string;
            taskReferenceName: string;
            type: string;
            inputParameters?: undefined;
            decisionCases?: undefined;
            defaultCase?: undefined;
            forkTasks?: undefined;
            startDelay?: undefined;
            joinOn?: undefined;
            optional?: undefined;
            defaultExclusiveJoinTask?: undefined;
            asyncComplete?: undefined;
            loopOver?: undefined;
        };
        crumbs: never[];
        selected: boolean;
    };
    width: number;
    height: number;
    ports?: undefined;
})[];
