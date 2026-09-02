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
export namespace simpleLoopSample {
    let updateTime_4: number;
    export { updateTime_4 as updateTime };
    let name_4: string;
    export { name_4 as name };
    let description_4: string;
    export { description_4 as description };
    let version_4: number;
    export { version_4 as version };
    let tasks_4: ({
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
        loopOver?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {};
        type: string;
        decisionCases: {};
        defaultCase: never[];
        forkTasks: ({
            name: string;
            taskReferenceName: string;
            inputParameters: {};
            type: string;
            decisionCases: {};
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
                    name?: undefined;
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
                    name: string;
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
        }[] | {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                value: string;
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
            loopCondition: string;
            loopOver: {
                name: string;
                taskReferenceName: string;
                inputParameters: {};
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
        }[])[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {};
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
    export { tasks_4 as tasks };
    let inputParameters_4: never[];
    export { inputParameters_4 as inputParameters };
    let outputParameters_4: {};
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
export namespace kitchenSink {
    let createTime_1: number;
    export { createTime_1 as createTime };
    let name_5: string;
    export { name_5 as name };
    let description_5: string;
    export { description_5 as description };
    let version_5: number;
    export { version_5 as version };
    let tasks_5: ({
        name: string;
        taskReferenceName: string;
        inputParameters: {
            mod: string;
            oddEven: string;
            taskToExecute?: undefined;
            http_request?: undefined;
            statuses?: undefined;
            workflowIds?: undefined;
        };
        type: string;
        decisionCases: {
            0?: undefined;
            1?: undefined;
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        sink?: undefined;
        dynamicTaskNameParam?: undefined;
        caseValueParam?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            mod: string;
            oddEven: string;
            taskToExecute?: undefined;
            http_request?: undefined;
            statuses?: undefined;
            workflowIds?: undefined;
        };
        type: string;
        decisionCases: {
            0?: undefined;
            1?: undefined;
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        sink: string;
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        dynamicTaskNameParam?: undefined;
        caseValueParam?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            taskToExecute: string;
            mod?: undefined;
            oddEven?: undefined;
            http_request?: undefined;
            statuses?: undefined;
            workflowIds?: undefined;
        };
        type: string;
        dynamicTaskNameParam: string;
        decisionCases: {
            0?: undefined;
            1?: undefined;
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        sink?: undefined;
        caseValueParam?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            oddEven: string;
            mod?: undefined;
            taskToExecute?: undefined;
            http_request?: undefined;
            statuses?: undefined;
            workflowIds?: undefined;
        };
        type: string;
        caseValueParam: string;
        decisionCases: {
            0: ({
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    mod: string;
                    oddEven: string;
                    dynamicTasks?: undefined;
                    input?: undefined;
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
                dynamicForkTasksParam?: undefined;
                dynamicForkTasksInputParamName?: undefined;
            } | {
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    dynamicTasks: string;
                    input: string;
                    mod?: undefined;
                    oddEven?: undefined;
                };
                type: string;
                decisionCases: {};
                dynamicForkTasksParam: string;
                dynamicForkTasksInputParamName: string;
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
                    mod?: undefined;
                    oddEven?: undefined;
                    dynamicTasks?: undefined;
                    input?: undefined;
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
                dynamicForkTasksParam?: undefined;
                dynamicForkTasksInputParamName?: undefined;
            })[];
            1: ({
                name: string;
                taskReferenceName: string;
                inputParameters: {};
                type: string;
                decisionCases: {};
                defaultCase: never[];
                forkTasks: ({
                    name: string;
                    taskReferenceName: string;
                    inputParameters: {
                        mod?: undefined;
                        oddEven?: undefined;
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
                    subWorkflowParam?: undefined;
                } | {
                    name: string;
                    taskReferenceName: string;
                    inputParameters: {
                        mod: string;
                        oddEven: string;
                    };
                    type: string;
                    decisionCases: {};
                    defaultCase: never[];
                    forkTasks: never[];
                    startDelay: number;
                    subWorkflowParam: {
                        name: string;
                        version: number;
                    };
                    joinOn: never[];
                    optional: boolean;
                    defaultExclusiveJoinTask: never[];
                    asyncComplete: boolean;
                    loopOver: never[];
                })[][];
                startDelay: number;
                joinOn: never[];
                optional: boolean;
                defaultExclusiveJoinTask: never[];
                asyncComplete: boolean;
                loopOver: never[];
            } | {
                name: string;
                taskReferenceName: string;
                inputParameters: {};
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
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        sink?: undefined;
        dynamicTaskNameParam?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            http_request: {
                uri: string;
                method: string;
            };
            mod?: undefined;
            oddEven?: undefined;
            taskToExecute?: undefined;
            statuses?: undefined;
            workflowIds?: undefined;
        };
        type: string;
        decisionCases: {
            0?: undefined;
            1?: undefined;
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        sink?: undefined;
        dynamicTaskNameParam?: undefined;
        caseValueParam?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            statuses: string;
            workflowIds: string;
            mod?: undefined;
            oddEven?: undefined;
            taskToExecute?: undefined;
            http_request?: undefined;
        };
        type: string;
        decisionCases: {
            0?: undefined;
            1?: undefined;
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        sink?: undefined;
        dynamicTaskNameParam?: undefined;
        caseValueParam?: undefined;
    })[];
    export { tasks_5 as tasks };
    let inputParameters_5: never[];
    export { inputParameters_5 as inputParameters };
    let outputParameters_5: {};
    export { outputParameters_5 as outputParameters };
    let schemaVersion_5: number;
    export { schemaVersion_5 as schemaVersion };
    let restartable_5: boolean;
    export { restartable_5 as restartable };
    let workflowStatusListenerEnabled_5: boolean;
    export { workflowStatusListenerEnabled_5 as workflowStatusListenerEnabled };
    let ownerEmail_5: string;
    export { ownerEmail_5 as ownerEmail };
    let timeoutPolicy_5: string;
    export { timeoutPolicy_5 as timeoutPolicy };
    let timeoutSeconds_5: number;
    export { timeoutSeconds_5 as timeoutSeconds };
    let failureWorkflow_5: string;
    export { failureWorkflow_5 as failureWorkflow };
    let variables_5: {};
    export { variables_5 as variables };
    let inputTemplate_5: {};
    export { inputTemplate_5 as inputTemplate };
}
export namespace switchExample {
    let updateTime_5: number;
    export { updateTime_5 as updateTime };
    let name_6: string;
    export { name_6 as name };
    let description_6: string;
    export { description_6 as description };
    let version_6: number;
    export { version_6 as version };
    let tasks_6: ({
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
        loopOver?: undefined;
        evaluatorType?: undefined;
        expression?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            case_value_param: string;
        };
        type: string;
        decisionCases: {
            0: {
                name: string;
                taskReferenceName: string;
                inputParameters: {};
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
            1: {
                name: string;
                taskReferenceName: string;
                inputParameters: {};
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
        defaultCase: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
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
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        evaluatorType: string;
        expression: string;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            case_value_param?: undefined;
        };
        type: string;
        decisionCases: {
            0?: undefined;
            1?: undefined;
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        evaluatorType?: undefined;
        expression?: undefined;
    })[];
    export { tasks_6 as tasks };
    let inputParameters_6: never[];
    export { inputParameters_6 as inputParameters };
    let outputParameters_6: {};
    export { outputParameters_6 as outputParameters };
    let schemaVersion_6: number;
    export { schemaVersion_6 as schemaVersion };
    let restartable_6: boolean;
    export { restartable_6 as restartable };
    let workflowStatusListenerEnabled_6: boolean;
    export { workflowStatusListenerEnabled_6 as workflowStatusListenerEnabled };
    let ownerEmail_6: string;
    export { ownerEmail_6 as ownerEmail };
    let timeoutPolicy_6: string;
    export { timeoutPolicy_6 as timeoutPolicy };
    let timeoutSeconds_6: number;
    export { timeoutSeconds_6 as timeoutSeconds };
    let failureWorkflow_6: string;
    export { failureWorkflow_6 as failureWorkflow };
    let variables_6: {};
    export { variables_6 as variables };
    let inputTemplate_6: {};
    export { inputTemplate_6 as inputTemplate };
}
export namespace switchTasksWithTerminationNodes {
    let name_7: string;
    export { name_7 as name };
    export let taskReferenceName: string;
    export namespace inputParameters_7 {
        let loantype: string;
    }
    export { inputParameters_7 as inputParameters };
    export let type: string;
    export namespace decisionCases {
        let education: ({
            name: string;
            taskReferenceName: string;
            inputParameters: {
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
                workflowOutput: {
                    result: string;
                };
            };
            type: string;
            startDelay: number;
            optional: boolean;
            decisionCases?: undefined;
            defaultCase?: undefined;
            forkTasks?: undefined;
            joinOn?: undefined;
            defaultExclusiveJoinTask?: undefined;
            asyncComplete?: undefined;
            loopOver?: undefined;
        })[];
        let property: ({
            name: string;
            taskReferenceName: string;
            inputParameters: {
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
                workflowOutput: {
                    result: string;
                };
            };
            type: string;
            startDelay: number;
            optional: boolean;
            decisionCases?: undefined;
            defaultCase?: undefined;
            forkTasks?: undefined;
            joinOn?: undefined;
            defaultExclusiveJoinTask?: undefined;
            asyncComplete?: undefined;
            loopOver?: undefined;
        })[];
    }
    export let defaultCase: ({
        name: string;
        taskReferenceName: string;
        inputParameters: {
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
            workflowOutput: {
                result: string;
            };
        };
        type: string;
        startDelay: number;
        optional: boolean;
        decisionCases?: undefined;
        defaultCase?: undefined;
        forkTasks?: undefined;
        joinOn?: undefined;
        defaultExclusiveJoinTask?: undefined;
        asyncComplete?: undefined;
        loopOver?: undefined;
    })[];
    export let forkTasks: never[];
    export let startDelay: number;
    export let joinOn: never[];
    export let optional: boolean;
    export let defaultExclusiveJoinTask: never[];
    export let asyncComplete: boolean;
    export let loopOver: never[];
    export let evaluatorType: string;
    export let expression: string;
}
export namespace lonleySwitchTask {
    let name_8: string;
    export { name_8 as name };
    let taskReferenceName_1: string;
    export { taskReferenceName_1 as taskReferenceName };
    export namespace inputParameters_8 {
        let loantype_1: string;
        export { loantype_1 as loantype };
    }
    export { inputParameters_8 as inputParameters };
    let type_1: string;
    export { type_1 as type };
    export namespace decisionCases_1 {
        export let emptyCase: never[];
        let education_1: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
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
        export { education_1 as education };
        let property_1: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
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
        export { property_1 as property };
        export let business: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
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
    }
    export { decisionCases_1 as decisionCases };
    let defaultCase_1: {
        name: string;
        taskReferenceName: string;
        inputParameters: {};
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
    export { defaultCase_1 as defaultCase };
    let forkTasks_1: never[];
    export { forkTasks_1 as forkTasks };
    let startDelay_1: number;
    export { startDelay_1 as startDelay };
    let joinOn_1: never[];
    export { joinOn_1 as joinOn };
    let optional_1: boolean;
    export { optional_1 as optional };
    let defaultExclusiveJoinTask_1: never[];
    export { defaultExclusiveJoinTask_1 as defaultExclusiveJoinTask };
    let asyncComplete_1: boolean;
    export { asyncComplete_1 as asyncComplete };
    let loopOver_1: never[];
    export { loopOver_1 as loopOver };
    let evaluatorType_1: string;
    export { evaluatorType_1 as evaluatorType };
    let expression_1: string;
    export { expression_1 as expression };
}
export namespace forkJoinTask {
    let name_9: string;
    export { name_9 as name };
    let taskReferenceName_2: string;
    export { taskReferenceName_2 as taskReferenceName };
    let inputParameters_9: {};
    export { inputParameters_9 as inputParameters };
    let type_2: string;
    export { type_2 as type };
    let decisionCases_2: {};
    export { decisionCases_2 as decisionCases };
    let defaultCase_2: never[];
    export { defaultCase_2 as defaultCase };
    let forkTasks_2: {
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
    export { forkTasks_2 as forkTasks };
    let startDelay_2: number;
    export { startDelay_2 as startDelay };
    let joinOn_2: never[];
    export { joinOn_2 as joinOn };
    let optional_2: boolean;
    export { optional_2 as optional };
    let defaultExclusiveJoinTask_2: never[];
    export { defaultExclusiveJoinTask_2 as defaultExclusiveJoinTask };
    let asyncComplete_2: boolean;
    export { asyncComplete_2 as asyncComplete };
    let loopOver_2: never[];
    export { loopOver_2 as loopOver };
}
export namespace loanBanking {
    let createTime_2: number;
    export { createTime_2 as createTime };
    let updateTime_6: number;
    export { updateTime_6 as updateTime };
    let name_10: string;
    export { name_10 as name };
    let description_7: string;
    export { description_7 as description };
    let version_7: number;
    export { version_7 as version };
    let tasks_7: ({
        name: string;
        taskReferenceName: string;
        inputParameters: {
            loantype?: undefined;
            creditScore?: undefined;
        };
        type: string;
        decisionCases: {
            education?: undefined;
            property?: undefined;
            possible?: undefined;
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        evaluatorType?: undefined;
        expression?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            loantype: string;
            creditScore?: undefined;
        };
        type: string;
        decisionCases: {
            education: {
                name: string;
                taskReferenceName: string;
                inputParameters: {};
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
            property: {
                name: string;
                taskReferenceName: string;
                inputParameters: {};
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
            possible?: undefined;
        };
        defaultCase: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
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
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        evaluatorType: string;
        expression: string;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            creditScore: string;
            loantype?: undefined;
        };
        type: string;
        decisionCases: {
            possible: ({
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    decision?: undefined;
                };
                type: string;
                decisionCases: {
                    yes?: undefined;
                };
                defaultCase: never[];
                forkTasks: never[];
                startDelay: number;
                joinOn: never[];
                optional: boolean;
                defaultExclusiveJoinTask: never[];
                asyncComplete: boolean;
                loopOver: never[];
                evaluatorType?: undefined;
                expression?: undefined;
            } | {
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    decision: string;
                };
                type: string;
                decisionCases: {
                    yes: {
                        name: string;
                        taskReferenceName: string;
                        inputParameters: {};
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
                defaultCase: {
                    name: string;
                    taskReferenceName: string;
                    inputParameters: {
                        terminationStatus: string;
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
                forkTasks: never[];
                startDelay: number;
                joinOn: never[];
                optional: boolean;
                defaultExclusiveJoinTask: never[];
                asyncComplete: boolean;
                loopOver: never[];
                evaluatorType: string;
                expression: string;
            })[];
            education?: undefined;
            property?: undefined;
        };
        defaultCase: {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                terminationStatus: string;
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
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        evaluatorType: string;
        expression: string;
    })[];
    export { tasks_7 as tasks };
    let inputParameters_10: never[];
    export { inputParameters_10 as inputParameters };
    let outputParameters_7: {};
    export { outputParameters_7 as outputParameters };
    let schemaVersion_7: number;
    export { schemaVersion_7 as schemaVersion };
    let restartable_7: boolean;
    export { restartable_7 as restartable };
    let workflowStatusListenerEnabled_7: boolean;
    export { workflowStatusListenerEnabled_7 as workflowStatusListenerEnabled };
    let ownerEmail_7: string;
    export { ownerEmail_7 as ownerEmail };
    let timeoutPolicy_7: string;
    export { timeoutPolicy_7 as timeoutPolicy };
    let timeoutSeconds_7: number;
    export { timeoutSeconds_7 as timeoutSeconds };
    let failureWorkflow_7: string;
    export { failureWorkflow_7 as failureWorkflow };
    let variables_7: {};
    export { variables_7 as variables };
    let inputTemplate_7: {};
    export { inputTemplate_7 as inputTemplate };
}
export namespace switchTaskCorrectlyTerminated {
    let name_11: string;
    export { name_11 as name };
    let taskReferenceName_3: string;
    export { taskReferenceName_3 as taskReferenceName };
    export namespace inputParameters_11 {
        let creditScore: string;
    }
    export { inputParameters_11 as inputParameters };
    let type_3: string;
    export { type_3 as type };
    export namespace decisionCases_3 {
        let possible: ({
            name: string;
            taskReferenceName: string;
            inputParameters: {
                terminationStatus?: undefined;
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
    }
    export { decisionCases_3 as decisionCases };
    let defaultCase_3: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            terminationStatus: string;
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
    export { defaultCase_3 as defaultCase };
    let forkTasks_3: never[];
    export { forkTasks_3 as forkTasks };
    let startDelay_3: number;
    export { startDelay_3 as startDelay };
    let joinOn_3: never[];
    export { joinOn_3 as joinOn };
    let optional_3: boolean;
    export { optional_3 as optional };
    let defaultExclusiveJoinTask_3: never[];
    export { defaultExclusiveJoinTask_3 as defaultExclusiveJoinTask };
    let asyncComplete_3: boolean;
    export { asyncComplete_3 as asyncComplete };
    let loopOver_3: never[];
    export { loopOver_3 as loopOver };
    let evaluatorType_2: string;
    export { evaluatorType_2 as evaluatorType };
    let expression_2: string;
    export { expression_2 as expression };
}
export namespace switchTaskOneNotTerminated {
    let name_12: string;
    export { name_12 as name };
    let taskReferenceName_4: string;
    export { taskReferenceName_4 as taskReferenceName };
    export namespace inputParameters_12 {
        let creditScore_1: string;
        export { creditScore_1 as creditScore };
    }
    export { inputParameters_12 as inputParameters };
    let type_4: string;
    export { type_4 as type };
    export namespace decisionCases_4 {
        let possible_1: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
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
        export { possible_1 as possible };
    }
    export { decisionCases_4 as decisionCases };
    let defaultCase_4: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            terminationStatus: string;
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
    export { defaultCase_4 as defaultCase };
    let forkTasks_4: never[];
    export { forkTasks_4 as forkTasks };
    let startDelay_4: number;
    export { startDelay_4 as startDelay };
    let joinOn_4: never[];
    export { joinOn_4 as joinOn };
    let optional_4: boolean;
    export { optional_4 as optional };
    let defaultExclusiveJoinTask_4: never[];
    export { defaultExclusiveJoinTask_4 as defaultExclusiveJoinTask };
    let asyncComplete_4: boolean;
    export { asyncComplete_4 as asyncComplete };
    let loopOver_4: never[];
    export { loopOver_4 as loopOver };
    let evaluatorType_3: string;
    export { evaluatorType_3 as evaluatorType };
    let expression_3: string;
    export { expression_3 as expression };
}
export namespace switchWithinSwitchLeafNotTerminated {
    let name_13: string;
    export { name_13 as name };
    let taskReferenceName_5: string;
    export { taskReferenceName_5 as taskReferenceName };
    export namespace inputParameters_13 {
        let creditScore_2: string;
        export { creditScore_2 as creditScore };
    }
    export { inputParameters_13 as inputParameters };
    let type_5: string;
    export { type_5 as type };
    export namespace decisionCases_5 {
        let possible_2: ({
            name: string;
            taskReferenceName: string;
            inputParameters: {
                decision?: undefined;
            };
            type: string;
            decisionCases: {
                yes?: undefined;
            };
            defaultCase: never[];
            forkTasks: never[];
            startDelay: number;
            joinOn: never[];
            optional: boolean;
            defaultExclusiveJoinTask: never[];
            asyncComplete: boolean;
            loopOver: never[];
            evaluatorType?: undefined;
            expression?: undefined;
        } | {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                decision: string;
            };
            type: string;
            decisionCases: {
                yes: {
                    name: string;
                    taskReferenceName: string;
                    inputParameters: {};
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
            defaultCase: {
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    terminationStatus: string;
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
            forkTasks: never[];
            startDelay: number;
            joinOn: never[];
            optional: boolean;
            defaultExclusiveJoinTask: never[];
            asyncComplete: boolean;
            loopOver: never[];
            evaluatorType: string;
            expression: string;
        })[];
        export { possible_2 as possible };
    }
    export { decisionCases_5 as decisionCases };
    let defaultCase_5: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            terminationStatus: string;
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
    export { defaultCase_5 as defaultCase };
    let forkTasks_5: never[];
    export { forkTasks_5 as forkTasks };
    let startDelay_5: number;
    export { startDelay_5 as startDelay };
    let joinOn_5: never[];
    export { joinOn_5 as joinOn };
    let optional_5: boolean;
    export { optional_5 as optional };
    let defaultExclusiveJoinTask_5: never[];
    export { defaultExclusiveJoinTask_5 as defaultExclusiveJoinTask };
    let asyncComplete_5: boolean;
    export { asyncComplete_5 as asyncComplete };
    let loopOver_5: never[];
    export { loopOver_5 as loopOver };
    let evaluatorType_4: string;
    export { evaluatorType_4 as evaluatorType };
    let expression_4: string;
    export { expression_4 as expression };
}
export namespace taskStub {
    let id: string;
    let text: string;
    namespace data {
        export namespace task {
            let name_14: string;
            export { name_14 as name };
            let taskReferenceName_6: string;
            export { taskReferenceName_6 as taskReferenceName };
            export namespace inputParameters_14 {
                export namespace http_request {
                    let uri: string;
                    let method: string;
                    namespace headers {
                        let Authorization: string;
                        let Accept: string;
                    }
                    let connectionTimeOut: number;
                    let readTimeOut: number;
                }
                let asyncComplete_6: boolean;
                export { asyncComplete_6 as asyncComplete };
            }
            export { inputParameters_14 as inputParameters };
            let type_6: string;
            export { type_6 as type };
            let decisionCases_6: {};
            export { decisionCases_6 as decisionCases };
            let defaultCase_6: never[];
            export { defaultCase_6 as defaultCase };
            let forkTasks_6: never[];
            export { forkTasks_6 as forkTasks };
            let startDelay_6: number;
            export { startDelay_6 as startDelay };
            let joinOn_6: never[];
            export { joinOn_6 as joinOn };
            let optional_6: boolean;
            export { optional_6 as optional };
            let defaultExclusiveJoinTask_6: never[];
            export { defaultExclusiveJoinTask_6 as defaultExclusiveJoinTask };
            let asyncComplete_7: boolean;
            export { asyncComplete_7 as asyncComplete };
            let loopOver_6: never[];
            export { loopOver_6 as loopOver };
            export namespace executionData {
                let status: string;
                let executed: boolean;
                let attempts: number;
            }
        }
        export let crumbs: {
            parent: null;
            ref: string;
            refIdx: number;
        }[];
        let status_1: string;
        export { status_1 as status };
        let executed_1: boolean;
        export { executed_1 as executed };
        let attempts_1: number;
        export { attempts_1 as attempts };
        export let selected: boolean;
    }
    let width: number;
    let height: number;
}
export namespace unConnectedSwitchTask {
    let name_15: string;
    export { name_15 as name };
    let taskReferenceName_7: string;
    export { taskReferenceName_7 as taskReferenceName };
    export namespace inputParameters_15 {
        let switchCaseValue: string;
    }
    export { inputParameters_15 as inputParameters };
    let type_7: string;
    export { type_7 as type };
    let decisionCases_7: {};
    export { decisionCases_7 as decisionCases };
    let defaultCase_7: never[];
    export { defaultCase_7 as defaultCase };
    let evaluatorType_5: string;
    export { evaluatorType_5 as evaluatorType };
    let expression_5: string;
    export { expression_5 as expression };
}
export namespace unConnectedSwitch {
    let name_16: string;
    export { name_16 as name };
    let description_8: string;
    export { description_8 as description };
    let version_8: number;
    export { version_8 as version };
    let tasks_8: ({
        name: string;
        taskReferenceName: string;
        inputParameters: {
            switchCaseValue: string;
        };
        type: string;
        decisionCases: {};
        defaultCase: never[];
        evaluatorType: string;
        expression: string;
    } | {
        name: string;
        taskReferenceName: string;
        type: string;
        sink: string;
        inputParameters?: undefined;
    } | {
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
        sink?: undefined;
    })[];
    export { tasks_8 as tasks };
    let inputParameters_16: never[];
    export { inputParameters_16 as inputParameters };
    export namespace outputParameters_8 {
        let data_1: string;
        export { data_1 as data };
    }
    export { outputParameters_8 as outputParameters };
    let schemaVersion_8: number;
    export { schemaVersion_8 as schemaVersion };
    let restartable_8: boolean;
    export { restartable_8 as restartable };
    let workflowStatusListenerEnabled_8: boolean;
    export { workflowStatusListenerEnabled_8 as workflowStatusListenerEnabled };
    let ownerEmail_8: string;
    export { ownerEmail_8 as ownerEmail };
    let timeoutPolicy_8: string;
    export { timeoutPolicy_8 as timeoutPolicy };
    let timeoutSeconds_8: number;
    export { timeoutSeconds_8 as timeoutSeconds };
}
export namespace switchTaskWithADecisionButNoTerminateTasks {
    let name_17: string;
    export { name_17 as name };
    let taskReferenceName_8: string;
    export { taskReferenceName_8 as taskReferenceName };
    export namespace inputParameters_17 {
        let switchCaseValue_1: string;
        export { switchCaseValue_1 as switchCaseValue };
    }
    export { inputParameters_17 as inputParameters };
    let type_8: string;
    export { type_8 as type };
    export namespace decisionCases_8 {
        let some_case: {
            name: string;
            taskReferenceName: string;
            type: string;
            inputParameters: {
                http_request: {
                    uri: string;
                    method: string;
                };
            };
        }[];
    }
    export { decisionCases_8 as decisionCases };
    let defaultCase_8: never[];
    export { defaultCase_8 as defaultCase };
    let evaluatorType_6: string;
    export { evaluatorType_6 as evaluatorType };
    let expression_6: string;
    export { expression_6 as expression };
}
export namespace workflowWithASwitchWithoutTermination {
    let name_18: string;
    export { name_18 as name };
    let description_9: string;
    export { description_9 as description };
    let version_9: number;
    export { version_9 as version };
    let tasks_9: ({
        name: string;
        taskReferenceName: string;
        inputParameters: {
            switchCaseValue: string;
        };
        type: string;
        decisionCases: {
            some_case: {
                name: string;
                taskReferenceName: string;
                type: string;
                inputParameters: {
                    http_request: {
                        uri: string;
                        method: string;
                    };
                };
            }[];
        };
        defaultCase: never[];
        evaluatorType: string;
        expression: string;
    } | {
        name: string;
        taskReferenceName: string;
        type: string;
        sink: string;
        inputParameters?: undefined;
    } | {
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
        sink?: undefined;
    })[];
    export { tasks_9 as tasks };
    let inputParameters_18: never[];
    export { inputParameters_18 as inputParameters };
    export namespace outputParameters_9 {
        let data_2: string;
        export { data_2 as data };
    }
    export { outputParameters_9 as outputParameters };
    let schemaVersion_9: number;
    export { schemaVersion_9 as schemaVersion };
    let restartable_9: boolean;
    export { restartable_9 as restartable };
    let workflowStatusListenerEnabled_9: boolean;
    export { workflowStatusListenerEnabled_9 as workflowStatusListenerEnabled };
    let ownerEmail_9: string;
    export { ownerEmail_9 as ownerEmail };
    let timeoutPolicy_9: string;
    export { timeoutPolicy_9 as timeoutPolicy };
    let timeoutSeconds_9: number;
    export { timeoutSeconds_9 as timeoutSeconds };
}
export namespace workflowWithSwitchWithinSwitchUnterminated {
    let name_19: string;
    export { name_19 as name };
    let description_10: string;
    export { description_10 as description };
    let version_10: number;
    export { version_10 as version };
    let tasks_10: ({
        name: string;
        taskReferenceName: string;
        type: string;
        sink: string;
        inputParameters?: undefined;
        decisionCases?: undefined;
        defaultCase?: undefined;
        evaluatorType?: undefined;
        expression?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            switchCaseValue: string;
            http_request?: undefined;
        };
        type: string;
        decisionCases: {
            new_case_a65un: {
                name: string;
                taskReferenceName: string;
                type: string;
                inputParameters: {
                    http_request: {
                        uri: string;
                        method: string;
                    };
                };
            }[];
            case_going_to_switch: {
                name: string;
                taskReferenceName: string;
                inputParameters: {
                    switchCaseValue: string;
                };
                type: string;
                decisionCases: {
                    nestedCase: {
                        name: string;
                        taskReferenceName: string;
                        inputParameters: {
                            taskToExecute: string;
                        };
                        type: string;
                        dynamicTaskNameParam: string;
                    }[];
                };
                defaultCase: never[];
                evaluatorType: string;
                expression: string;
            }[];
        };
        defaultCase: never[];
        evaluatorType: string;
        expression: string;
        sink?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            http_request: {
                uri: string;
                method: string;
                connectionTimeOut: number;
                readTimeOut: number;
            };
            switchCaseValue?: undefined;
        };
        type: string;
        sink?: undefined;
        decisionCases?: undefined;
        defaultCase?: undefined;
        evaluatorType?: undefined;
        expression?: undefined;
    })[];
    export { tasks_10 as tasks };
    let inputParameters_19: never[];
    export { inputParameters_19 as inputParameters };
    export namespace outputParameters_10 {
        let data_3: string;
        export { data_3 as data };
    }
    export { outputParameters_10 as outputParameters };
    let schemaVersion_10: number;
    export { schemaVersion_10 as schemaVersion };
    let restartable_10: boolean;
    export { restartable_10 as restartable };
    let workflowStatusListenerEnabled_10: boolean;
    export { workflowStatusListenerEnabled_10 as workflowStatusListenerEnabled };
    let ownerEmail_10: string;
    export { ownerEmail_10 as ownerEmail };
    let timeoutPolicy_10: string;
    export { timeoutPolicy_10 as timeoutPolicy };
    let timeoutSeconds_10: number;
    export { timeoutSeconds_10 as timeoutSeconds };
}
export namespace wfWithWhileWithSubWorkflow {
    let createTime_3: number;
    export { createTime_3 as createTime };
    let updateTime_7: number;
    export { updateTime_7 as updateTime };
    let name_20: string;
    export { name_20 as name };
    let description_11: string;
    export { description_11 as description };
    let version_11: number;
    export { version_11 as version };
    let tasks_11: ({
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
        loopCondition?: undefined;
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
        joinOn: never[];
        optional: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopCondition: string;
        loopOver: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
            type: string;
            subWorkflowParam: {
                name: string;
                version: number;
            };
        }[];
    })[];
    export { tasks_11 as tasks };
    let inputParameters_20: never[];
    export { inputParameters_20 as inputParameters };
    export namespace outputParameters_11 {
        let data_4: string;
        export { data_4 as data };
    }
    export { outputParameters_11 as outputParameters };
    let schemaVersion_11: number;
    export { schemaVersion_11 as schemaVersion };
    let restartable_11: boolean;
    export { restartable_11 as restartable };
    let workflowStatusListenerEnabled_11: boolean;
    export { workflowStatusListenerEnabled_11 as workflowStatusListenerEnabled };
    let ownerEmail_11: string;
    export { ownerEmail_11 as ownerEmail };
    let timeoutPolicy_11: string;
    export { timeoutPolicy_11 as timeoutPolicy };
    let timeoutSeconds_11: number;
    export { timeoutSeconds_11 as timeoutSeconds };
    let variables_8: {};
    export { variables_8 as variables };
    let inputTemplate_8: {};
    export { inputTemplate_8 as inputTemplate };
}
export namespace subWorkflowWithinAFork {
    let createTime_4: number;
    export { createTime_4 as createTime };
    let updateTime_8: number;
    export { updateTime_8 as updateTime };
    let name_21: string;
    export { name_21 as name };
    let description_12: string;
    export { description_12 as description };
    let version_12: number;
    export { version_12 as version };
    let tasks_12: ({
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
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            http_request?: undefined;
        };
        type: string;
        decisionCases: {};
        defaultCase: never[];
        forkTasks: ({
            name: string;
            taskReferenceName: string;
            type: string;
            sink: string;
        }[] | {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
            type: string;
            subWorkflowParam: {
                name: string;
                version: number;
            };
        }[])[];
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
            http_request?: undefined;
        };
        type: string;
        decisionCases: {};
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        asyncComplete: boolean;
        defaultExclusiveJoinTask?: undefined;
        loopOver?: undefined;
    })[];
    export { tasks_12 as tasks };
    let inputParameters_21: never[];
    export { inputParameters_21 as inputParameters };
    export namespace outputParameters_12 {
        let data_5: string;
        export { data_5 as data };
    }
    export { outputParameters_12 as outputParameters };
    let schemaVersion_12: number;
    export { schemaVersion_12 as schemaVersion };
    let restartable_12: boolean;
    export { restartable_12 as restartable };
    let workflowStatusListenerEnabled_12: boolean;
    export { workflowStatusListenerEnabled_12 as workflowStatusListenerEnabled };
    let ownerEmail_12: string;
    export { ownerEmail_12 as ownerEmail };
    let timeoutPolicy_12: string;
    export { timeoutPolicy_12 as timeoutPolicy };
    let timeoutSeconds_12: number;
    export { timeoutSeconds_12 as timeoutSeconds };
    let variables_9: {};
    export { variables_9 as variables };
    let inputTemplate_9: {};
    export { inputTemplate_9 as inputTemplate };
}
export namespace workflowWithUnknownType {
    let updateTime_9: number;
    export { updateTime_9 as updateTime };
    let name_22: string;
    export { name_22 as name };
    let description_13: string;
    export { description_13 as description };
    let version_13: number;
    export { version_13 as version };
    let tasks_13: ({
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
    export { tasks_13 as tasks };
    let inputParameters_22: never[];
    export { inputParameters_22 as inputParameters };
    export namespace outputParameters_13 {
        let fileLocation_1: string;
        export { fileLocation_1 as fileLocation };
    }
    export { outputParameters_13 as outputParameters };
    let schemaVersion_13: number;
    export { schemaVersion_13 as schemaVersion };
    let restartable_13: boolean;
    export { restartable_13 as restartable };
    let workflowStatusListenerEnabled_13: boolean;
    export { workflowStatusListenerEnabled_13 as workflowStatusListenerEnabled };
    let ownerEmail_13: string;
    export { ownerEmail_13 as ownerEmail };
    let timeoutPolicy_13: string;
    export { timeoutPolicy_13 as timeoutPolicy };
    let timeoutSeconds_13: number;
    export { timeoutSeconds_13 as timeoutSeconds };
    let variables_10: {};
    export { variables_10 as variables };
    let inputTemplate_10: {};
    export { inputTemplate_10 as inputTemplate };
}
export namespace nestedForkJoin {
    let name_23: string;
    export { name_23 as name };
    let description_14: string;
    export { description_14 as description };
    let version_14: number;
    export { version_14 as version };
    let tasks_14: ({
        name: string;
        taskReferenceName: string;
        inputParameters: {
            http_request: {
                uri: string;
                method: string;
                connectionTimeOut: number;
                readTimeOut: number;
            };
            switchCaseValue?: undefined;
        };
        type: string;
        decisionCases?: undefined;
        defaultCase?: undefined;
        forkTasks?: undefined;
        startDelay?: undefined;
        joinOn?: undefined;
        optional?: undefined;
        asyncComplete?: undefined;
        evaluatorType?: undefined;
        expression?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            http_request?: undefined;
            switchCaseValue?: undefined;
        };
        type: string;
        decisionCases: {
            new_case_ms0jy?: undefined;
        };
        defaultCase: never[];
        forkTasks: ({
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
        }[] | {
            name: string;
            taskReferenceName: string;
            type: string;
            sink: string;
        }[])[];
        startDelay?: undefined;
        joinOn?: undefined;
        optional?: undefined;
        asyncComplete?: undefined;
        evaluatorType?: undefined;
        expression?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            http_request?: undefined;
            switchCaseValue?: undefined;
        };
        type: string;
        decisionCases: {
            new_case_ms0jy?: undefined;
        };
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: string[];
        optional: boolean;
        asyncComplete: boolean;
        evaluatorType?: undefined;
        expression?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            http_request?: undefined;
            switchCaseValue?: undefined;
        };
        type: string;
        decisionCases: {
            new_case_ms0jy?: undefined;
        };
        defaultCase: never[];
        forkTasks: ({
            name: string;
            taskReferenceName: string;
            type: string;
            inputParameters: {
                kafka_request: {
                    topic: string;
                    value: string;
                    bootStrapServers: string;
                    headers: {
                        "X-Auth": string;
                    };
                    key: string;
                    keySerializer: string;
                };
            };
        }[] | {
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
        }[])[];
        startDelay?: undefined;
        joinOn?: undefined;
        optional?: undefined;
        asyncComplete?: undefined;
        evaluatorType?: undefined;
        expression?: undefined;
    } | {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            switchCaseValue: string;
            http_request?: undefined;
        };
        type: string;
        decisionCases: {
            new_case_ms0jy: ({
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
                asyncComplete?: undefined;
            } | {
                name: string;
                taskReferenceName: string;
                inputParameters: {};
                type: string;
                decisionCases: {};
                defaultCase: never[];
                forkTasks: {
                    name: string;
                    taskReferenceName: string;
                    type: string;
                    inputParameters: {
                        expression: string;
                        evaluatorType: string;
                        value1: number;
                        value2: number;
                    };
                }[][];
                startDelay?: undefined;
                joinOn?: undefined;
                optional?: undefined;
                asyncComplete?: undefined;
            } | {
                name: string;
                taskReferenceName: string;
                inputParameters: {};
                type: string;
                decisionCases: {};
                defaultCase: never[];
                forkTasks: never[];
                startDelay: number;
                joinOn: never[];
                optional: boolean;
                asyncComplete: boolean;
            })[];
        };
        defaultCase: never[];
        evaluatorType: string;
        expression: string;
        forkTasks?: undefined;
        startDelay?: undefined;
        joinOn?: undefined;
        optional?: undefined;
        asyncComplete?: undefined;
    })[];
    export { tasks_14 as tasks };
    let inputParameters_23: never[];
    export { inputParameters_23 as inputParameters };
    export namespace outputParameters_14 {
        let data_6: string;
        export { data_6 as data };
    }
    export { outputParameters_14 as outputParameters };
    let schemaVersion_14: number;
    export { schemaVersion_14 as schemaVersion };
    let restartable_14: boolean;
    export { restartable_14 as restartable };
    let workflowStatusListenerEnabled_14: boolean;
    export { workflowStatusListenerEnabled_14 as workflowStatusListenerEnabled };
    let ownerEmail_14: string;
    export { ownerEmail_14 as ownerEmail };
    let timeoutPolicy_14: string;
    export { timeoutPolicy_14 as timeoutPolicy };
    let timeoutSeconds_14: number;
    export { timeoutSeconds_14 as timeoutSeconds };
}
export namespace unknownTaskTypeWf {
    let name_24: string;
    export { name_24 as name };
    let description_15: string;
    export { description_15 as description };
    let version_15: number;
    export { version_15 as version };
    let tasks_15: {
        name: string;
        taskReferenceName: string;
        type: string;
        sink: string;
        inputParameters: {};
    }[];
    export { tasks_15 as tasks };
    let inputParameters_24: never[];
    export { inputParameters_24 as inputParameters };
    let outputParameters_15: {};
    export { outputParameters_15 as outputParameters };
    let schemaVersion_15: number;
    export { schemaVersion_15 as schemaVersion };
    let restartable_15: boolean;
    export { restartable_15 as restartable };
    let workflowStatusListenerEnabled_15: boolean;
    export { workflowStatusListenerEnabled_15 as workflowStatusListenerEnabled };
    let ownerEmail_15: string;
    export { ownerEmail_15 as ownerEmail };
    let timeoutPolicy_15: string;
    export { timeoutPolicy_15 as timeoutPolicy };
    let timeoutSeconds_15: number;
    export { timeoutSeconds_15 as timeoutSeconds };
    let failureWorkflow_8: string;
    export { failureWorkflow_8 as failureWorkflow };
}
export namespace switchExecutionDefaultByEvaluationResultNull {
    let name_25: string;
    export { name_25 as name };
    let taskReferenceName_9: string;
    export { taskReferenceName_9 as taskReferenceName };
    export namespace inputParameters_25 {
        let _case: string;
        export { _case as case };
    }
    export { inputParameters_25 as inputParameters };
    let type_9: string;
    export { type_9 as type };
    let decisionCases_9: {
        "": never[];
        "CASE-2": never[];
        "CASE-1": never[];
    };
    export { decisionCases_9 as decisionCases };
    let defaultCase_9: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            method: string;
            asyncComplete: boolean;
            readTimeOut: string;
            uri: string;
            connectionTimeOut: number;
            contentType: string;
            accept: string;
        };
        type: string;
        decisionCases: {};
        defaultCase: never[];
        forkTasks: never[];
        startDelay: number;
        joinOn: never[];
        optional: boolean;
        rateLimited: boolean;
        defaultExclusiveJoinTask: never[];
        asyncComplete: boolean;
        loopOver: never[];
        onStateChange: {};
        executionData: {
            status: string;
            executed: boolean;
            attempts: number;
            outputData: {
                response: {
                    headers: {
                        "Strict-Transport-Security": string[];
                        Connection: string[];
                        "Content-Length": string[];
                        Date: string[];
                        "Content-Type": string[];
                    };
                    reasonPhrase: string;
                    body: {
                        randomInt: number;
                        hostName: string;
                        randomString: string;
                        queryParams: {};
                        sleepFor: string;
                        apiRandomDelay: string;
                        statusCode: string;
                    };
                    statusCode: number;
                };
            };
        };
    }[];
    export { defaultCase_9 as defaultCase };
    let forkTasks_7: never[];
    export { forkTasks_7 as forkTasks };
    let startDelay_7: number;
    export { startDelay_7 as startDelay };
    let joinOn_7: never[];
    export { joinOn_7 as joinOn };
    let optional_7: boolean;
    export { optional_7 as optional };
    export let rateLimited: boolean;
    let defaultExclusiveJoinTask_7: never[];
    export { defaultExclusiveJoinTask_7 as defaultExclusiveJoinTask };
    let asyncComplete_8: boolean;
    export { asyncComplete_8 as asyncComplete };
    let loopOver_7: never[];
    export { loopOver_7 as loopOver };
    let evaluatorType_7: string;
    export { evaluatorType_7 as evaluatorType };
    let expression_7: string;
    export { expression_7 as expression };
    export let onStateChange: {};
    export namespace executionData_1 {
        let status_2: string;
        export { status_2 as status };
        let executed_2: boolean;
        export { executed_2 as executed };
        let attempts_2: number;
        export { attempts_2 as attempts };
        export namespace outputData {
            let evaluationResult: string[];
            let selectedCase: string;
        }
    }
    export { executionData_1 as executionData };
}
export namespace decisionExecutionDataWithValidCase {
    let name_26: string;
    export { name_26 as name };
    let taskReferenceName_10: string;
    export { taskReferenceName_10 as taskReferenceName };
    export namespace inputParameters_26 {
        let case_value_param: string;
    }
    export { inputParameters_26 as inputParameters };
    let type_10: string;
    export { type_10 as type };
    export let caseValueParam: string;
    export namespace decisionCases_10 {
        let LOW: {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                uri: string;
                method: string;
                accept: string;
                contentType: string;
                encode: boolean;
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
            onStateChange: {};
            permissive: boolean;
        }[];
        let MEDIUM: {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                uri: string;
                method: string;
                accept: string;
                contentType: string;
                encode: boolean;
                asyncComplete: boolean;
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
            onStateChange: {};
            permissive: boolean;
        }[];
        let HIGH: {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                uri: string;
                method: string;
                accept: string;
                contentType: string;
                encode: boolean;
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
            onStateChange: {};
            permissive: boolean;
        }[];
    }
    export { decisionCases_10 as decisionCases };
    let defaultCase_10: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            uri: string;
            method: string;
            accept: string;
            contentType: string;
            encode: boolean;
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
        onStateChange: {};
        permissive: boolean;
    }[];
    export { defaultCase_10 as defaultCase };
    let forkTasks_8: never[];
    export { forkTasks_8 as forkTasks };
    let startDelay_8: number;
    export { startDelay_8 as startDelay };
    let joinOn_8: never[];
    export { joinOn_8 as joinOn };
    let optional_8: boolean;
    export { optional_8 as optional };
    let defaultExclusiveJoinTask_8: never[];
    export { defaultExclusiveJoinTask_8 as defaultExclusiveJoinTask };
    let asyncComplete_9: boolean;
    export { asyncComplete_9 as asyncComplete };
    let loopOver_8: never[];
    export { loopOver_8 as loopOver };
    let onStateChange_1: {};
    export { onStateChange_1 as onStateChange };
    export let permissive: boolean;
}
