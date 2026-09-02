export namespace NEW_TASK_TEMPLATE {
    let name: string;
    let description: string;
    let retryCount: number;
    let timeoutSeconds: number;
    let timeoutPolicy: string;
    let retryLogic: string;
    let retryDelaySeconds: number;
    let responseTimeoutSeconds: number;
    let rateLimitPerFrequency: number;
    let rateLimitFrequencyInSeconds: number;
    let ownerEmail: string;
    let pollTimeoutSeconds: number;
    let inputKeys: never[];
    let outputKeys: never[];
    let inputTemplate: {};
    let backoffScaleFactor: number;
    let concurrentExecLimit: number;
}
export function newTaskTemplate(ownerEmail: any): {
    ownerEmail: any;
    name: string;
    description: string;
    retryCount: number;
    timeoutSeconds: number;
    timeoutPolicy: string;
    retryLogic: string;
    retryDelaySeconds: number;
    responseTimeoutSeconds: number;
    rateLimitPerFrequency: number;
    rateLimitFrequencyInSeconds: number;
    pollTimeoutSeconds: number;
    inputKeys: never[];
    outputKeys: never[];
    inputTemplate: {};
    backoffScaleFactor: number;
    concurrentExecLimit: number;
};
export namespace NEW_WORKFLOW_TEMPLATE {
    let name_1: string;
    export { name_1 as name };
    let description_1: string;
    export { description_1 as description };
    export let version: number;
    export let tasks: never[];
    export let inputParameters: never[];
    export let outputParameters: {};
    export let schemaVersion: number;
    export let restartable: boolean;
    export let workflowStatusListenerEnabled: boolean;
    let ownerEmail_1: string;
    export { ownerEmail_1 as ownerEmail };
    let timeoutPolicy_1: string;
    export { timeoutPolicy_1 as timeoutPolicy };
    let timeoutSeconds_1: number;
    export { timeoutSeconds_1 as timeoutSeconds };
    export let failureWorkflow: string;
}
export function newWorkflowTemplate(ownerEmail: any): {
    name: string;
    ownerEmail: any;
    description: string;
    version: number;
    tasks: never[];
    inputParameters: never[];
    outputParameters: {};
    schemaVersion: number;
    restartable: boolean;
    workflowStatusListenerEnabled: boolean;
    timeoutPolicy: string;
    timeoutSeconds: number;
    failureWorkflow: string;
};
export namespace WORKFLOW_SCHEMA {
    export { JSON_SCHEMA_DRAFT_07_URL as $schema };
    export let $id: string;
    export let type: string;
    export let title: string;
    let description_2: string;
    export { description_2 as description };
    let _default: {};
    export { _default as default };
    export let examples: {
        name: string;
        description: string;
        version: number;
        tasks: {
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
        }[];
        inputParameters: never[];
        outputParameters: {
            data: string;
        };
        schemaVersion: number;
        restartable: boolean;
        workflowStatusListenerEnabled: boolean;
        ownerEmail: string;
        timeoutPolicy: string;
        timeoutSeconds: number;
        failureWorkflow: string;
    }[];
    export let required: string[];
    export namespace properties {
        export namespace name_2 {
            let $id_1: string;
            export { $id_1 as $id };
            let _default_1: string;
            export { _default_1 as default };
            let description_3: string;
            export { description_3 as description };
            let examples_1: string[];
            export { examples_1 as examples };
            export let maxLength: number;
            export let pattern: string;
            let title_1: string;
            export { title_1 as title };
            let type_1: string;
            export { type_1 as type };
        }
        export { name_2 as name };
        export namespace description_4 {
            let $id_2: string;
            export { $id_2 as $id };
            let type_2: string;
            export { type_2 as type };
            let title_2: string;
            export { title_2 as title };
            let description_5: string;
            export { description_5 as description };
            let _default_2: string;
            export { _default_2 as default };
            let examples_2: string[];
            export { examples_2 as examples };
        }
        export { description_4 as description };
        export namespace version_1 {
            let $id_3: string;
            export { $id_3 as $id };
            let _default_3: number;
            export { _default_3 as default };
            let description_6: string;
            export { description_6 as description };
            let examples_3: number[];
            export { examples_3 as examples };
            let title_3: string;
            export { title_3 as title };
            export let minimum: number;
            let type_3: string;
            export { type_3 as type };
        }
        export { version_1 as version };
        export namespace tasks_1 {
            let $id_4: string;
            export { $id_4 as $id };
            let type_4: string;
            export { type_4 as type };
            let title_4: string;
            export { title_4 as title };
            let description_7: string;
            export { description_7 as description };
            let _default_4: never[];
            export { _default_4 as default };
            let examples_4: {
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
            }[][];
            export { examples_4 as examples };
            export let additionalItems: boolean;
            export namespace items {
                let $id_5: string;
                export { $id_5 as $id };
                export let anyOf: {
                    $id: string;
                    type: string;
                    title: string;
                    description: string;
                    default: {
                        name: string;
                        taskReferenceName: string;
                        inputParameters: {};
                        type: string;
                    };
                    examples: {
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
                    }[];
                    required: string[];
                    properties: {
                        name: {
                            $id: string;
                            type: string;
                            title: string;
                            description: string;
                            default: string;
                            examples: string[];
                        };
                        taskReferenceName: {
                            $id: string;
                            type: string;
                            title: string;
                            description: string;
                            default: string;
                            examples: string[];
                        };
                        inputParameters: {
                            $id: string;
                            type: string;
                            title: string;
                            description: string;
                            default: {};
                            examples: {
                                http_request: {
                                    uri: string;
                                    method: string;
                                };
                            }[];
                            required: never[];
                            properties: {};
                            additionalProperties: boolean;
                        };
                        type: {
                            $id: string;
                            type: string;
                            title: string;
                            description: string;
                            default: string;
                            examples: string[];
                        };
                    };
                    additionalProperties: boolean;
                }[];
            }
        }
        export { tasks_1 as tasks };
        export namespace inputParameters_1 {
            let $id_6: string;
            export { $id_6 as $id };
            let type_5: string;
            export { type_5 as type };
            let title_5: string;
            export { title_5 as title };
            let description_8: string;
            export { description_8 as description };
            let _default_5: never[];
            export { _default_5 as default };
            let examples_5: never[][];
            export { examples_5 as examples };
            let additionalItems_1: boolean;
            export { additionalItems_1 as additionalItems };
            export namespace items_1 {
                let $id_7: string;
                export { $id_7 as $id };
            }
            export { items_1 as items };
        }
        export { inputParameters_1 as inputParameters };
        export namespace outputParameters_1 {
            let $id_8: string;
            export { $id_8 as $id };
            let type_6: string;
            export { type_6 as type };
            let title_6: string;
            export { title_6 as title };
            let description_9: string;
            export { description_9 as description };
            let _default_6: {};
            export { _default_6 as default };
            let examples_6: {
                data: string;
                source: string;
            }[];
            export { examples_6 as examples };
            let required_1: never[];
            export { required_1 as required };
            let properties_1: {};
            export { properties_1 as properties };
            export let additionalProperties: boolean;
        }
        export { outputParameters_1 as outputParameters };
        export namespace schemaVersion_1 {
            let $id_9: string;
            export { $id_9 as $id };
            let type_7: string;
            export { type_7 as type };
            let title_7: string;
            export { title_7 as title };
            let description_10: string;
            export { description_10 as description };
            let _default_7: number;
            export { _default_7 as default };
            let examples_7: number[];
            export { examples_7 as examples };
        }
        export { schemaVersion_1 as schemaVersion };
        export namespace restartable_1 {
            let $id_10: string;
            export { $id_10 as $id };
            let type_8: string;
            export { type_8 as type };
            let title_8: string;
            export { title_8 as title };
            let description_11: string;
            export { description_11 as description };
            let _default_8: boolean;
            export { _default_8 as default };
            let examples_8: boolean[];
            export { examples_8 as examples };
        }
        export { restartable_1 as restartable };
        export namespace workflowStatusListenerEnabled_1 {
            let $id_11: string;
            export { $id_11 as $id };
            let type_9: string;
            export { type_9 as type };
            let title_9: string;
            export { title_9 as title };
            let description_12: string;
            export { description_12 as description };
            let _default_9: boolean;
            export { _default_9 as default };
            let examples_9: boolean[];
            export { examples_9 as examples };
        }
        export { workflowStatusListenerEnabled_1 as workflowStatusListenerEnabled };
        export namespace ownerEmail_2 {
            let $id_12: string;
            export { $id_12 as $id };
            let type_10: string;
            export { type_10 as type };
            let title_10: string;
            export { title_10 as title };
            let description_13: string;
            export { description_13 as description };
            let _default_10: string;
            export { _default_10 as default };
            let examples_10: string[];
            export { examples_10 as examples };
        }
        export { ownerEmail_2 as ownerEmail };
        export namespace timeoutPolicy_2 {
            let $id_13: string;
            export { $id_13 as $id };
            let type_11: string;
            export { type_11 as type };
            let title_11: string;
            export { title_11 as title };
            let description_14: string;
            export { description_14 as description };
            let _default_11: string;
            export { _default_11 as default };
            let examples_11: string[];
            export { examples_11 as examples };
        }
        export { timeoutPolicy_2 as timeoutPolicy };
        export namespace timeoutSeconds_2 {
            let $id_14: string;
            export { $id_14 as $id };
            let type_12: string;
            export { type_12 as type };
            let title_12: string;
            export { title_12 as title };
            let description_15: string;
            export { description_15 as description };
            let _default_12: number;
            export { _default_12 as default };
            let examples_12: number[];
            export { examples_12 as examples };
        }
        export { timeoutSeconds_2 as timeoutSeconds };
        export namespace failureWorkflow_1 {
            let $id_15: string;
            export { $id_15 as $id };
            let type_13: string;
            export { type_13 as type };
            let title_13: string;
            export { title_13 as title };
            let description_16: string;
            export { description_16 as description };
            let _default_13: string;
            export { _default_13 as default };
            let examples_13: string[];
            export { examples_13 as examples };
        }
        export { failureWorkflow_1 as failureWorkflow };
    }
    let additionalProperties_1: boolean;
    export { additionalProperties_1 as additionalProperties };
}
import { JSON_SCHEMA_DRAFT_07_URL } from "utils/constants/jsonSchema";
