export namespace simpleDiagram {
    let updateTime: number;
    let name: string;
    let description: string;
    let version: number;
    let tasks: ({
        name: string;
        taskReferenceName: string;
        inputParameters: {
            fileLocation: string;
            outputFormat: string;
            outputWidth: string;
            outputHeight: string;
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
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            fileLocation: string;
            outputFormat?: undefined;
            outputWidth?: undefined;
            outputHeight?: undefined;
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
    })[];
    let inputParameters: never[];
    namespace outputParameters {
        let fileLocation: string;
    }
    let schemaVersion: number;
    let restartable: boolean;
    let workflowStatusListenerEnabled: boolean;
    let ownerEmail: string;
    let timeoutPolicy: string;
    let timeoutSeconds: number;
    let failureWorkflow: string;
    let variables: {};
    let inputTemplate: {};
}
export namespace populationMinMax {
    let updateTime_1: number;
    export { updateTime_1 as updateTime };
    let name_1: string;
    export { name_1 as name };
    let description_1: string;
    export { description_1 as description };
    let version_1: number;
    export { version_1 as version };
    let tasks_1: ({
        name: string;
        taskReferenceName: string;
        inputParameters: {
            http_request: {
                uri: string;
                method: string;
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
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            http_request?: undefined;
        };
        type: string;
        decisionCases: {};
        defaultCase: never[];
        forkTasks: {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                body: string;
                queryExpression: string;
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
        }[][];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            http_request?: undefined;
        };
        type: string;
        decisionCases: {};
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: string[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
    })[];
    export { tasks_1 as tasks };
    let inputParameters_1: never[];
    export { inputParameters_1 as inputParameters };
    export namespace outputParameters_1 {
        let maxPopulation: string;
        let minPopulation: string;
    }
    export { outputParameters_1 as outputParameters };
    let schemaVersion_1: number;
    export { schemaVersion_1 as schemaVersion };
    let restartable_1: boolean;
    export { restartable_1 as restartable };
    let workflowStatusListenerEnabled_1: boolean;
    export { workflowStatusListenerEnabled_1 as workflowStatusListenerEnabled };
    let ownerEmail_1: string;
    export { ownerEmail_1 as ownerEmail };
    let timeoutPolicy_1: string;
    export { timeoutPolicy_1 as timeoutPolicy };
    let timeoutSeconds_1: number;
    export { timeoutSeconds_1 as timeoutSeconds };
    let failureWorkflow_1: string;
    export { failureWorkflow_1 as failureWorkflow };
    let variables_1: {};
    export { variables_1 as variables };
    let inputTemplate_1: {};
    export { inputTemplate_1 as inputTemplate };
}
export namespace decisionSample {
    let updateTime_2: number;
    export { updateTime_2 as updateTime };
    let name_2: string;
    export { name_2 as name };
    let description_2: string;
    export { description_2 as description };
    let version_2: number;
    export { version_2 as version };
    let tasks_2: ({
        type: string;
        name: string;
        taskReferenceName: string;
        inputParameters?: undefined;
        caseValueParam?: undefined;
        decisionCases?: undefined;
        defaultCase?: undefined;
        forkTasks?: undefined;
        startDelay?: undefined;
        joinOn?: undefined;
        optional?: undefined;
        defaultExclusiveJoinTask?: undefined;
        asyncComplete?: undefined;
        loopOver?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            case_value_param: string;
        };
        type: string;
        caseValueParam: string;
        decisionCases: {
            POST: {
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    http_request: {
                        uri: string;
                        method: string;
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
            }[];
            COMMENT: {
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    http_request: {
                        uri: string;
                        method: string;
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
            }[];
            USER: {
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    http_request: {
                        uri: string;
                        method: string;
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
            }[];
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            case_value_param?: undefined;
        };
        type: string;
        decisionCases: {
            POST?: undefined;
            COMMENT?: undefined;
            USER?: undefined;
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: string[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        caseValueParam?: undefined;
    })[];
    export { tasks_2 as tasks };
    let inputParameters_2: never[];
    export { inputParameters_2 as inputParameters };
    let outputParameters_2: {};
    export { outputParameters_2 as outputParameters };
    let schemaVersion_2: number;
    export { schemaVersion_2 as schemaVersion };
    let restartable_2: boolean;
    export { restartable_2 as restartable };
    let workflowStatusListenerEnabled_2: boolean;
    export { workflowStatusListenerEnabled_2 as workflowStatusListenerEnabled };
    let ownerEmail_2: string;
    export { ownerEmail_2 as ownerEmail };
    let timeoutPolicy_2: string;
    export { timeoutPolicy_2 as timeoutPolicy };
    let timeoutSeconds_2: number;
    export { timeoutSeconds_2 as timeoutSeconds };
    let failureWorkflow_2: string;
    export { failureWorkflow_2 as failureWorkflow };
    let variables_2: {};
    export { variables_2 as variables };
    let inputTemplate_2: {};
    export { inputTemplate_2 as inputTemplate };
}
export namespace complexDiagram {
    export let createTime: number;
    let updateTime_3: number;
    export { updateTime_3 as updateTime };
    let name_3: string;
    export { name_3 as name };
    let description_3: string;
    export { description_3 as description };
    let version_3: number;
    export { version_3 as version };
    let tasks_3: ({
        type: string;
        name: string;
        taskReferenceName: string;
        inputParameters?: undefined;
        decisionCases?: undefined;
        defaultCase?: undefined;
        forkTasks?: undefined;
        startDelay?: undefined;
        joinOn?: undefined;
        optional?: undefined;
        defaultExclusiveJoinTask?: undefined;
        asyncComplete?: undefined;
        loopCondition?: undefined;
        loopOver?: undefined;
        caseValueParam?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            value: string;
            terminate: string;
            switchCaseValue?: undefined;
        };
        type: string;
        decisionCases: {
            false?: undefined;
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopCondition: string;
        loopOver: ({
            name: string;
            taskReferenceName: string;
            inputParameters: {
                http_request: {
                    uri: string;
                    method: string;
                };
                prev_task_result?: undefined;
                switchCaseValue?: undefined;
            };
            type: string;
            decisionCases: {
                COMPLETED?: undefined;
                COMPLETED_WITH_ERRORS?: undefined;
            };
            defaultCase: never[];
            forkTasks: never[];
            startDelay: number;
            joinOn: never[];
            optional: boolean;
            defaultExclusiveJoinTask: never[];
            asyncComplete: boolean;
            loopOver: never[];
            caseValueParam?: undefined;
        } | {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                prev_task_result: string;
                switchCaseValue: string;
                http_request?: undefined;
            };
            type: string;
            caseValueParam: string;
            decisionCases: {
                COMPLETED: {
                    name: string;
                    taskReferenceName: string;
                    inputParameters: {
                        terminate_loop: boolean;
                        success: boolean;
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
                }[];
                COMPLETED_WITH_ERRORS: ({
                    name: string;
                    taskReferenceName: string;
                    inputParameters: {
                        terminate_loop: boolean;
                        success: boolean;
                        update_records_on_retry?: undefined;
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
                } | {
                    name: string;
                    taskReferenceName: string;
                    inputParameters: {
                        update_records_on_retry: number;
                        terminate_loop?: undefined;
                        success?: undefined;
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
                })[];
            };
            defaultCase: ({
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    terminate_loop: boolean;
                    success: boolean;
                    update_records_on_retry?: undefined;
                    terminationStatus?: undefined;
                    workflowOutput?: undefined;
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
            } | {
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    update_records_on_retry: number;
                    terminate_loop?: undefined;
                    success?: undefined;
                    terminationStatus?: undefined;
                    workflowOutput?: undefined;
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
            } | {
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    terminationStatus: string;
                    workflowOutput: string;
                    terminate_loop?: undefined;
                    success?: undefined;
                    update_records_on_retry?: undefined;
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
            })[];
            forkTasks: never[];
            startDelay: number;
            joinOn: never[];
            optional: boolean;
            defaultExclusiveJoinTask: never[];
            asyncComplete: boolean;
            loopOver: never[];
        })[];
        caseValueParam?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            switchCaseValue: string;
            value?: undefined;
            terminate?: undefined;
        };
        type: string;
        caseValueParam: string;
        decisionCases: {
            false: ({
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    update_records_on_retry: number;
                    terminationStatus?: undefined;
                    workflowOutput?: undefined;
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
            } | {
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    terminationStatus: string;
                    workflowOutput: string;
                    update_records_on_retry?: undefined;
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
            })[];
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        loopCondition?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            value?: undefined;
            terminate?: undefined;
            switchCaseValue?: undefined;
        };
        type: string;
        decisionCases: {
            false?: undefined;
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        loopCondition?: undefined;
        caseValueParam?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            switchCaseValue: string;
            value?: undefined;
            terminate?: undefined;
        };
        type: string;
        caseValueParam: string;
        decisionCases: {
            false: ({
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    response: string;
                    terminationStatus?: undefined;
                    workflowOutput?: undefined;
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
            } | {
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    terminationStatus: string;
                    workflowOutput: string;
                    response?: undefined;
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
            })[];
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        loopCondition?: undefined;
    })[];
    export { tasks_3 as tasks };
    let inputParameters_3: never[];
    export { inputParameters_3 as inputParameters };
    let outputParameters_3: {};
    export { outputParameters_3 as outputParameters };
    let schemaVersion_3: number;
    export { schemaVersion_3 as schemaVersion };
    let restartable_3: boolean;
    export { restartable_3 as restartable };
    let workflowStatusListenerEnabled_3: boolean;
    export { workflowStatusListenerEnabled_3 as workflowStatusListenerEnabled };
    let ownerEmail_3: string;
    export { ownerEmail_3 as ownerEmail };
    let timeoutPolicy_3: string;
    export { timeoutPolicy_3 as timeoutPolicy };
    let timeoutSeconds_3: number;
    export { timeoutSeconds_3 as timeoutSeconds };
    let failureWorkflow_3: string;
    export { failureWorkflow_3 as failureWorkflow };
    export namespace variables_3 {
        let success: boolean;
    }
    export { variables_3 as variables };
    let inputTemplate_3: {};
    export { inputTemplate_3 as inputTemplate };
}
export namespace allTaskTypes {
    let updateTime_4: number;
    export { updateTime_4 as updateTime };
    let name_4: string;
    export { name_4 as name };
    let description_4: string;
    export { description_4 as description };
    let version_4: number;
    export { version_4 as version };
    let tasks_4: ({
        name: string;
        taskReferenceName: string;
        inputParameters: {
            body: string;
            queryExpression: string;
            value?: undefined;
            evaluatorType?: undefined;
            expression?: undefined;
            kafka_request?: undefined;
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
    } | {
        name: string;
        taskReferenceName: string;
        type: string;
        inputParameters: {
            value: string;
            evaluatorType: string;
            expression: string;
            body?: undefined;
            queryExpression?: undefined;
            kafka_request?: undefined;
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
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            kafka_request: {
                topic: string;
                value: string;
                bootStrapServers: string;
                headers: {
                    "x-Auth": string;
                };
                key: {
                    Key_1: string;
                };
                keySerializer: string;
            };
            body?: undefined;
            queryExpression?: undefined;
            value?: undefined;
            evaluatorType?: undefined;
            expression?: undefined;
        };
        type: string;
        decisionCases?: undefined;
        defaultCase?: undefined;
        forkTasks?: undefined;
        startDelay?: undefined;
        joinOn?: undefined;
        optional?: undefined;
        defaultExclusiveJoinTask?: undefined;
        asyncComplete?: undefined;
        loopOver?: undefined;
    })[];
    export { tasks_4 as tasks };
    let inputParameters_4: never[];
    export { inputParameters_4 as inputParameters };
    export namespace outputParameters_4 {
        let fileLocation_1: string;
        export { fileLocation_1 as fileLocation };
    }
    export { outputParameters_4 as outputParameters };
    let schemaVersion_4: number;
    export { schemaVersion_4 as schemaVersion };
    let restartable_4: boolean;
    export { restartable_4 as restartable };
    let workflowStatusListenerEnabled_4: boolean;
    export { workflowStatusListenerEnabled_4 as workflowStatusListenerEnabled };
    let ownerEmail_4: string;
    export { ownerEmail_4 as ownerEmail };
    let timeoutPolicy_4: string;
    export { timeoutPolicy_4 as timeoutPolicy };
    let timeoutSeconds_4: number;
    export { timeoutSeconds_4 as timeoutSeconds };
    let failureWorkflow_4: string;
    export { failureWorkflow_4 as failureWorkflow };
    let variables_4: {};
    export { variables_4 as variables };
    let inputTemplate_4: {};
    export { inputTemplate_4 as inputTemplate };
}
