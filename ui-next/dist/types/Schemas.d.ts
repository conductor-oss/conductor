import { UpdateTaskStatus } from "./UpdateTaskStatus";
import { GetSignedJWTAlgorithmType, HTTPMethods, JDBCType, QueryProcessorType } from "./TaskType";
import { TaskType } from "./common";
import { TimeoutPolicy } from "types/TimeoutPolicy";
export declare const nameSchema: {
    $id: string;
    type: string;
    pattern: string;
    title: string;
    description: string;
    default: string;
};
export declare const taskReferenceName: {
    $id: string;
    type: string;
    title: string;
    minLength: number;
    description: string;
};
export declare const inputParameters: {
    $id: string;
    anyOf: ({
        type: string;
        properties: {};
        additionalProperties: boolean;
        pattern?: undefined;
    } | {
        type: string;
        pattern: string;
        properties?: undefined;
        additionalProperties?: undefined;
    })[];
    title: string;
    description: string;
    default: {};
};
export declare const genericSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            type: string;
            enum: TaskType[];
        };
    };
    additionalProperties: boolean;
};
export declare const simpleTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {};
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const yieldTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {};
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const doWhileSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {};
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            $ref: string;
        };
        loopCondition: {
            type: string;
        };
        loopOver: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const eventTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {};
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        sink: {
            type: string;
        };
        inputParameters: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const joinTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {};
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        joinOn: {
            type: string;
            items: {
                type: string;
            };
        };
        inputParameters: {
            $ref: string;
        };
        evaluatorType: {
            type: string;
        };
        expression: {
            type: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const forkTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {};
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            $ref: string;
        };
        forkTasks: {
            type: string;
            default: never[][];
            items: {
                $ref: string;
            };
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const waitSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const forkJoinDynamicSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {};
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            $ref: string;
        };
        dynamicForkTasksParam: {
            type: string;
        };
        dynamicForkTasksInputParamName: {
            type: string;
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const dynamicTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            $ref: string;
        };
        dynamicTaskNameParam: {
            type: string;
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const inlineTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            anyOf: ({
                type: string;
                required: string[];
                properties: {
                    evaluatorType: {
                        type: string;
                        enum: string[];
                    };
                    expression: {
                        type: string;
                    };
                    additionalProperties: boolean;
                };
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                required?: undefined;
                properties?: undefined;
            })[];
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const switchTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            $ref: string;
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
        evaluatorType: {
            enum: string[];
            type: string;
        };
        expression: {
            type: string;
        };
        decisionCases: {
            type: string;
            patternProperties: {
                ".*": {
                    $ref: string;
                };
            };
            additionalProperties: boolean;
        };
        defaultCase: {
            $ref: string;
        };
    };
    additionalProperties: boolean;
};
export declare const kafkaRequestTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    kafka_request: {
                        anyOf: ({
                            type: string;
                            properties: {
                                headers: {
                                    anyOf: ({
                                        type: string;
                                        pattern?: undefined;
                                    } | {
                                        type: string;
                                        pattern: string;
                                    })[];
                                };
                                key: {
                                    type: string;
                                };
                                value: {
                                    anyOf: {
                                        type: string;
                                    }[];
                                };
                            };
                            additionalProperties: boolean;
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            properties?: undefined;
                            additionalProperties?: undefined;
                        })[];
                    };
                };
                required: string[];
                additionalProperties: boolean;
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
                required?: undefined;
                additionalProperties?: undefined;
            })[];
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const baseHTTPRequestSchema: {
    $id: string;
    type: string;
    properties: {
        uri: {
            type: string;
        };
        method: {
            type: string;
            anyOf: ({
                enum: HTTPMethods[];
                pattern?: undefined;
            } | {
                pattern: string;
                enum?: undefined;
            })[];
        };
        headers: {
            type: string[];
            patternProperties: {
                "^\\S*$": {
                    type: string;
                };
            };
            additionalProperties: boolean;
        };
        terminationCondition: {
            type: string;
        };
        pollingInterval: {
            type: string;
        };
        pollingStrategy: {
            type: string;
        };
        encode: {
            type: string;
        };
        additionalProperties: boolean;
    };
    additionalProperties: boolean;
};
export declare const httpTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            type: string[];
            properties: {
                http_request: {
                    anyOf: ({
                        $ref: string;
                        type?: undefined;
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                        $ref?: undefined;
                    })[];
                };
            };
            additionalProperties: boolean;
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const httpPollTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            type: string[];
            properties: {
                http_request: {
                    anyOf: ({
                        type: string;
                        $ref: string;
                        required: string[];
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                        $ref?: undefined;
                        required?: undefined;
                    })[];
                };
            };
            required: string[];
            additionalProperties: boolean;
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const jsonJQTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    queryExpression: {
                        type: string;
                    };
                };
                required: string[];
                additionalProperties: boolean;
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
                required?: undefined;
                additionalProperties?: undefined;
            })[];
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const terminateTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    terminationStatus: {
                        enum: string[];
                        type: string;
                    };
                    workflowOutput: {
                        type: string;
                    };
                };
                additionalProperties: boolean;
                required: string[];
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
                additionalProperties?: undefined;
                required?: undefined;
            })[];
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const setVariableTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            $ref: string;
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const terminateWorkflowSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            workflowId: string;
        };
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            anyOf: ({
                type: string;
                required: string[];
                properties: {
                    workflowId: {
                        anyOf: ({
                            type: string;
                            items?: undefined;
                        } | {
                            type: string;
                            items: {
                                type: string;
                            };
                        })[];
                    };
                    terminationReason: {
                        type: string;
                    };
                };
                additionalProperties: boolean;
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                required?: undefined;
                properties?: undefined;
                additionalProperties?: undefined;
            })[];
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const businessRuleSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            ruleFileLocation: string;
            executionStrategy: string;
            inputColumns: {};
            outputColumns: never[];
        };
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    inputColumns: {
                        anyOf: ({
                            type: string;
                            additionalProperties: boolean;
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            additionalProperties?: undefined;
                        })[];
                    };
                    outputColumns: {
                        anyOf: ({
                            type: string;
                            items: {
                                type: string;
                            };
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            items?: undefined;
                        })[];
                    };
                    additionalProperties: boolean;
                };
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
            })[];
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const sendgridMailRequestSchema: {
    $id: string;
    type: string;
    required: string[];
    properties: {
        from: {
            type: string;
        };
        to: {
            type: string;
        };
        subject: {
            type: string;
        };
        contentType: {
            type: string;
        };
        content: {
            type: string;
        };
        sendgridConfiguration: {
            type: string;
        };
    };
    additionalProperties: boolean;
};
export declare const sendgridSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
        inputParameters: {
            $ref: string;
        };
        additionalProperties: boolean;
    };
};
export declare const subWorkflowTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            $ref: string;
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        subWorkflowParam: {
            type: string;
            properties: {
                name: {
                    type: string;
                };
                version: {
                    type: string[];
                };
                taskToDomain: {
                    type: string;
                    additionalProperties: boolean;
                };
                workflowDefinition: {
                    anyOf: ({
                        type: string;
                        $ref?: undefined;
                    } | {
                        $ref: string;
                        type?: undefined;
                    })[];
                };
            };
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const startWorkflowTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            startWorkflow: {
                name: string;
                input: {};
            };
        };
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
};
export declare const webhookTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            matches: {
                type: string;
            };
        };
        type: TaskType;
        required: string[];
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    matches: {
                        anyOf: ({
                            type: string;
                            additionalProperties: boolean;
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            additionalProperties?: undefined;
                        })[];
                    };
                };
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
            })[];
        };
        type: {
            const: TaskType;
        };
    };
};
export declare const humanTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    _humanTaskTemplate: {
                        type: string;
                    };
                    _humanTaskAssignmentPolicy: {
                        anyOf: ({
                            type: string;
                            properties: {
                                type: {
                                    type: string;
                                };
                                subjects: {
                                    type: string[];
                                    items: {
                                        type: string;
                                    };
                                };
                                groupId: {
                                    type: string;
                                };
                            };
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            properties?: undefined;
                        })[];
                    };
                    _humanTaskTimeoutPolicy: {
                        anyOf: ({
                            type: string;
                            properties: {
                                type: {
                                    type: string;
                                };
                                timeoutSeconds: {
                                    type: string;
                                };
                                subjects: {
                                    type: string[];
                                    items: {
                                        type: string;
                                    };
                                };
                            };
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            properties?: undefined;
                        })[];
                    };
                    additionalProperties: boolean;
                };
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
            })[];
        };
    };
    additionalProperties: boolean;
};
export declare const jdbcTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
        inputParameters: {
            connectionId: string;
            integrationName: string;
            statement: string;
            parameters: never[];
            expectedUpdateCount: number;
            jdbcType: JDBCType;
        };
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            type: string[];
            properties: {
                connectionId: {
                    type: string;
                };
                integrationName: {
                    type: string;
                };
                statement: {
                    type: string;
                };
                parameters: {
                    anyOf: ({
                        type: string;
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                    })[];
                };
                expectedUpdateCount: {
                    type: string;
                };
                type: {
                    type: string;
                    enum: JDBCType[];
                };
            };
            required: string[];
            additionalProperties: boolean;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const updateSecretTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
        inputParameters: {
            _secrets: {
                secretKey: string;
                secretValue: string;
            };
        };
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    _secrets: {
                        anyOf: ({
                            type: string;
                            properties: {
                                secretKey: {
                                    type: string;
                                };
                                secretValue: {
                                    type: string;
                                };
                            };
                            required: string[];
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            properties?: undefined;
                            required?: undefined;
                        })[];
                    };
                };
                required: string[];
                additionalProperties: boolean;
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
                required?: undefined;
                additionalProperties?: undefined;
            })[];
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const queryProcessorTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
        inputParameters: {
            workflowNames: never[];
            statuses: never[];
            queryType: QueryProcessorType;
        };
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            type: string[];
            properties: {
                workflowNames: {
                    anyOf: ({
                        type: string;
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                    })[];
                };
                statuses: {
                    anyOf: ({
                        type: string;
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                    })[];
                };
                queryType: {
                    type: string;
                    enum: QueryProcessorType[];
                };
            };
            required: string[];
            additionalProperties: boolean;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const getSignedJwtTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
        inputParameters: {
            subject: string;
            issuer: string;
            privateKey: string;
            privateKeyId: string;
            audience: string;
            ttlInSecond: number;
            scopes: never[];
            algorithm: GetSignedJWTAlgorithmType;
        };
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            type: string[];
            properties: {
                subject: {
                    type: string;
                };
                issuer: {
                    type: string;
                };
                privateKey: {
                    type: string;
                };
                privateKeyId: {
                    type: string;
                };
                audience: {
                    type: string;
                };
                ttlInSecond: {
                    anyOf: ({
                        type: string;
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                    })[];
                };
                scopes: {
                    anyOf: ({
                        type: string;
                        items: {
                            type: string;
                        };
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                        items?: undefined;
                    })[];
                };
                algorithm: {
                    type: string;
                    enum: GetSignedJWTAlgorithmType.RS256[];
                };
            };
            required: never[];
            additionalProperties: boolean;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const opsGenieTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
        inputParameters: {};
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            type: string[];
            properties: {
                alias: {
                    type: string;
                };
                description: {
                    type: string;
                };
                message: {
                    type: string;
                };
            };
            required: never[];
            additionalProperties: boolean;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const updateTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
        inputParameters: {};
    };
    required: never[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            type: string[];
            properties: {
                taskStatus: {
                    anyOf: ({
                        type: string;
                        enum: UpdateTaskStatus[];
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                        enum?: undefined;
                    })[];
                };
                taskRefName: {
                    type: string;
                };
                workflowId: {
                    type: string;
                };
                taskId: {
                    type: string;
                };
                taskOutput: {
                    type: string;
                };
                mergeOutput: {
                    type: string;
                };
            };
            additionalProperties: boolean;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
};
export declare const getWorkflowSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
        workflowId: {
            type: string;
        };
        includeTasks: {
            type: string;
        };
    };
    additionalProperties: boolean;
};
export declare const chunkTextTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            type: string[];
            properties: {
                text: {
                    type: string;
                };
                chunkSize: {
                    type: string;
                };
                mediaType: {
                    type: string;
                };
            };
            required: never[];
            additionalProperties: boolean;
        };
        type: {
            const: TaskType;
        };
    };
};
export declare const listFilesTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
        inputParameters: {
            type: string;
            required: string[];
            properties: {
                inputLocation: {
                    type: string;
                };
                integrationName: {
                    type: string;
                };
                outputLocation: {
                    type: string;
                };
                fileTypes: {
                    type: string;
                    items: {
                        type: string;
                    };
                };
                integrationNames: {
                    type: string;
                    additionalProperties: {
                        type: string;
                    };
                };
            };
            additionalProperties: boolean;
        };
    };
};
export declare const parseDocumentTaskSchema: {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: never[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
        integrationName: {
            type: string;
        };
        url: {
            type: string;
        };
        mediaType: {
            type: string;
        };
        chunkSize: {
            type: string;
        };
    };
    additionalProperties: boolean;
};
export declare const tasksItemsSchema: {
    $id: string;
    type: string;
    title: string;
    description: string;
    default: never[];
    items: {
        $id: string;
        oneOf: {
            $ref: string;
        }[];
    };
};
export declare const schemasByType: {
    SIMPLE: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    YIELD: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    DO_WHILE: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                $ref: string;
            };
            loopCondition: {
                type: string;
            };
            loopOver: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    FORK_JOIN: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                $ref: string;
            };
            forkTasks: {
                type: string;
                default: never[][];
                items: {
                    $ref: string;
                };
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    WAIT: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    FORK_JOIN_DYNAMIC: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
            type: TaskType;
        };
        required: string[];
        properties: {
            inputParameters: {
                $ref: string;
            };
            dynamicForkTasksParam: {
                type: string;
            };
            dynamicForkTasksInputParamName: {
                type: string;
            };
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    DYNAMIC: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            inputParameters: {
                $ref: string;
            };
            dynamicTaskNameParam: {
                type: string;
            };
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    TERMINATE: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            inputParameters: {
                anyOf: ({
                    type: string;
                    properties: {
                        terminationStatus: {
                            enum: string[];
                            type: string;
                        };
                        workflowOutput: {
                            type: string;
                        };
                    };
                    additionalProperties: boolean;
                    required: string[];
                    pattern?: undefined;
                } | {
                    type: string;
                    pattern: string;
                    properties?: undefined;
                    additionalProperties?: undefined;
                    required?: undefined;
                })[];
            };
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    SET_VARIABLE: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            inputParameters: {
                $ref: string;
            };
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    SUB_WORKFLOW: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            inputParameters: {
                $ref: string;
            };
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            subWorkflowParam: {
                type: string;
                properties: {
                    name: {
                        type: string;
                    };
                    version: {
                        type: string[];
                    };
                    taskToDomain: {
                        type: string;
                        additionalProperties: boolean;
                    };
                    workflowDefinition: {
                        anyOf: ({
                            type: string;
                            $ref?: undefined;
                        } | {
                            $ref: string;
                            type?: undefined;
                        })[];
                    };
                };
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    JSON_JQ_TRANSFORM: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            inputParameters: {
                anyOf: ({
                    type: string;
                    properties: {
                        queryExpression: {
                            type: string;
                        };
                    };
                    required: string[];
                    additionalProperties: boolean;
                    pattern?: undefined;
                } | {
                    type: string;
                    pattern: string;
                    properties?: undefined;
                    required?: undefined;
                    additionalProperties?: undefined;
                })[];
            };
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    HTTP: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            inputParameters: {
                type: string[];
                properties: {
                    http_request: {
                        anyOf: ({
                            $ref: string;
                            type?: undefined;
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            $ref?: undefined;
                        })[];
                    };
                };
                additionalProperties: boolean;
            };
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    SWITCH: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            inputParameters: {
                $ref: string;
            };
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
            evaluatorType: {
                enum: string[];
                type: string;
            };
            expression: {
                type: string;
            };
            decisionCases: {
                type: string;
                patternProperties: {
                    ".*": {
                        $ref: string;
                    };
                };
                additionalProperties: boolean;
            };
            defaultCase: {
                $ref: string;
            };
        };
        additionalProperties: boolean;
    };
    INLINE: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            inputParameters: {
                anyOf: ({
                    type: string;
                    required: string[];
                    properties: {
                        evaluatorType: {
                            type: string;
                            enum: string[];
                        };
                        expression: {
                            type: string;
                        };
                        additionalProperties: boolean;
                    };
                    pattern?: undefined;
                } | {
                    type: string;
                    pattern: string;
                    required?: undefined;
                    properties?: undefined;
                })[];
            };
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    JOIN: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            joinOn: {
                type: string;
                items: {
                    type: string;
                };
            };
            inputParameters: {
                $ref: string;
            };
            evaluatorType: {
                type: string;
            };
            expression: {
                type: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    EVENT: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            inputParameters: {};
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            sink: {
                type: string;
            };
            inputParameters: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    KAFKA_PUBLISH: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            inputParameters: {
                anyOf: ({
                    type: string;
                    properties: {
                        kafka_request: {
                            anyOf: ({
                                type: string;
                                properties: {
                                    headers: {
                                        anyOf: ({
                                            type: string;
                                            pattern?: undefined;
                                        } | {
                                            type: string;
                                            pattern: string;
                                        })[];
                                    };
                                    key: {
                                        type: string;
                                    };
                                    value: {
                                        anyOf: {
                                            type: string;
                                        }[];
                                    };
                                };
                                additionalProperties: boolean;
                                pattern?: undefined;
                            } | {
                                type: string;
                                pattern: string;
                                properties?: undefined;
                                additionalProperties?: undefined;
                            })[];
                        };
                    };
                    required: string[];
                    additionalProperties: boolean;
                    pattern?: undefined;
                } | {
                    type: string;
                    pattern: string;
                    properties?: undefined;
                    required?: undefined;
                    additionalProperties?: undefined;
                })[];
            };
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    TERMINATE_WORKFLOW: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                workflowId: string;
            };
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                anyOf: ({
                    type: string;
                    required: string[];
                    properties: {
                        workflowId: {
                            anyOf: ({
                                type: string;
                                items?: undefined;
                            } | {
                                type: string;
                                items: {
                                    type: string;
                                };
                            })[];
                        };
                        terminationReason: {
                            type: string;
                        };
                    };
                    additionalProperties: boolean;
                    pattern?: undefined;
                } | {
                    type: string;
                    pattern: string;
                    required?: undefined;
                    properties?: undefined;
                    additionalProperties?: undefined;
                })[];
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    BUSINESS_RULE: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                ruleFileLocation: string;
                executionStrategy: string;
                inputColumns: {};
                outputColumns: never[];
            };
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                anyOf: ({
                    type: string;
                    properties: {
                        inputColumns: {
                            anyOf: ({
                                type: string;
                                additionalProperties: boolean;
                                pattern?: undefined;
                            } | {
                                type: string;
                                pattern: string;
                                additionalProperties?: undefined;
                            })[];
                        };
                        outputColumns: {
                            anyOf: ({
                                type: string;
                                items: {
                                    type: string;
                                };
                                pattern?: undefined;
                            } | {
                                type: string;
                                pattern: string;
                                items?: undefined;
                            })[];
                        };
                        additionalProperties: boolean;
                    };
                    pattern?: undefined;
                } | {
                    type: string;
                    pattern: string;
                    properties?: undefined;
                })[];
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    SENDGRID: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
            inputParameters: {
                $ref: string;
            };
            additionalProperties: boolean;
        };
    };
    START: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    DECISION: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    USER_DEFINED: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    LAMBDA: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    EXCLUSIVE_JOIN: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    TERMINAL: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    HUMAN: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
            inputParameters: {
                anyOf: ({
                    type: string;
                    properties: {
                        _humanTaskTemplate: {
                            type: string;
                        };
                        _humanTaskAssignmentPolicy: {
                            anyOf: ({
                                type: string;
                                properties: {
                                    type: {
                                        type: string;
                                    };
                                    subjects: {
                                        type: string[];
                                        items: {
                                            type: string;
                                        };
                                    };
                                    groupId: {
                                        type: string;
                                    };
                                };
                                pattern?: undefined;
                            } | {
                                type: string;
                                pattern: string;
                                properties?: undefined;
                            })[];
                        };
                        _humanTaskTimeoutPolicy: {
                            anyOf: ({
                                type: string;
                                properties: {
                                    type: {
                                        type: string;
                                    };
                                    timeoutSeconds: {
                                        type: string;
                                    };
                                    subjects: {
                                        type: string[];
                                        items: {
                                            type: string;
                                        };
                                    };
                                };
                                pattern?: undefined;
                            } | {
                                type: string;
                                pattern: string;
                                properties?: undefined;
                            })[];
                        };
                        additionalProperties: boolean;
                    };
                    pattern?: undefined;
                } | {
                    type: string;
                    pattern: string;
                    properties?: undefined;
                })[];
            };
        };
        additionalProperties: boolean;
    };
    TASK_SUMMARY: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    WAIT_FOR_EVENT: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    WAIT_FOR_WEBHOOK: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                matches: {
                    type: string;
                };
            };
            type: TaskType;
            required: string[];
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                anyOf: ({
                    type: string;
                    properties: {
                        matches: {
                            anyOf: ({
                                type: string;
                                additionalProperties: boolean;
                                pattern?: undefined;
                            } | {
                                type: string;
                                pattern: string;
                                additionalProperties?: undefined;
                            })[];
                        };
                    };
                    pattern?: undefined;
                } | {
                    type: string;
                    pattern: string;
                    properties?: undefined;
                })[];
            };
            type: {
                const: TaskType;
            };
        };
    };
    START_WORKFLOW: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            inputParameters: {
                startWorkflow: {
                    name: string;
                    input: {};
                };
            };
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
    };
    HTTP_POLL: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            inputParameters: {
                type: string[];
                properties: {
                    http_request: {
                        anyOf: ({
                            type: string;
                            $ref: string;
                            required: string[];
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            $ref?: undefined;
                            required?: undefined;
                        })[];
                    };
                };
                required: string[];
                additionalProperties: boolean;
            };
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    JDBC: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
            inputParameters: {
                connectionId: string;
                integrationName: string;
                statement: string;
                parameters: never[];
                expectedUpdateCount: number;
                jdbcType: JDBCType;
            };
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                type: string[];
                properties: {
                    connectionId: {
                        type: string;
                    };
                    integrationName: {
                        type: string;
                    };
                    statement: {
                        type: string;
                    };
                    parameters: {
                        anyOf: ({
                            type: string;
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                        })[];
                    };
                    expectedUpdateCount: {
                        type: string;
                    };
                    type: {
                        type: string;
                        enum: JDBCType[];
                    };
                };
                required: string[];
                additionalProperties: boolean;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    _ai_tc: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    SWITCH_JOIN: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    LLM_TEXT_COMPLETE: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    LLM_GENERATE_EMBEDDINGS: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    LLM_GET_EMBEDDINGS: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    LLM_STORE_EMBEDDINGS: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    LLM_SEARCH_INDEX: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    LLM_INDEX_DOCUMENT: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    GET_DOCUMENT: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    LLM_CHAT_COMPLETE: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    LLM_INDEX_TEXT: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    UPDATE_SECRET: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
            inputParameters: {
                _secrets: {
                    secretKey: string;
                    secretValue: string;
                };
            };
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                anyOf: ({
                    type: string;
                    properties: {
                        _secrets: {
                            anyOf: ({
                                type: string;
                                properties: {
                                    secretKey: {
                                        type: string;
                                    };
                                    secretValue: {
                                        type: string;
                                    };
                                };
                                required: string[];
                                pattern?: undefined;
                            } | {
                                type: string;
                                pattern: string;
                                properties?: undefined;
                                required?: undefined;
                            })[];
                        };
                    };
                    required: string[];
                    additionalProperties: boolean;
                    pattern?: undefined;
                } | {
                    type: string;
                    pattern: string;
                    properties?: undefined;
                    required?: undefined;
                    additionalProperties?: undefined;
                })[];
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    JUMP: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    QUERY_PROCESSOR: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
            inputParameters: {
                workflowNames: never[];
                statuses: never[];
                queryType: QueryProcessorType;
            };
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                type: string[];
                properties: {
                    workflowNames: {
                        anyOf: ({
                            type: string;
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                        })[];
                    };
                    statuses: {
                        anyOf: ({
                            type: string;
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                        })[];
                    };
                    queryType: {
                        type: string;
                        enum: QueryProcessorType[];
                    };
                };
                required: string[];
                additionalProperties: boolean;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    OPS_GENIE: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
            inputParameters: {};
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                type: string[];
                properties: {
                    alias: {
                        type: string;
                    };
                    description: {
                        type: string;
                    };
                    message: {
                        type: string;
                    };
                };
                required: never[];
                additionalProperties: boolean;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    GET_SIGNED_JWT: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
            inputParameters: {
                subject: string;
                issuer: string;
                privateKey: string;
                privateKeyId: string;
                audience: string;
                ttlInSecond: number;
                scopes: never[];
                algorithm: GetSignedJWTAlgorithmType;
            };
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                type: string[];
                properties: {
                    subject: {
                        type: string;
                    };
                    issuer: {
                        type: string;
                    };
                    privateKey: {
                        type: string;
                    };
                    privateKeyId: {
                        type: string;
                    };
                    audience: {
                        type: string;
                    };
                    ttlInSecond: {
                        anyOf: ({
                            type: string;
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                        })[];
                    };
                    scopes: {
                        anyOf: ({
                            type: string;
                            items: {
                                type: string;
                            };
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            items?: undefined;
                        })[];
                    };
                    algorithm: {
                        type: string;
                        enum: GetSignedJWTAlgorithmType.RS256[];
                    };
                };
                required: never[];
                additionalProperties: boolean;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    UPDATE_TASK: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
            inputParameters: {};
        };
        required: never[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                type: string[];
                properties: {
                    taskStatus: {
                        anyOf: ({
                            type: string;
                            enum: UpdateTaskStatus[];
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            enum?: undefined;
                        })[];
                    };
                    taskRefName: {
                        type: string;
                    };
                    workflowId: {
                        type: string;
                    };
                    taskId: {
                        type: string;
                    };
                    taskOutput: {
                        type: string;
                    };
                    mergeOutput: {
                        type: string;
                    };
                };
                additionalProperties: boolean;
            };
            type: {
                const: TaskType;
            };
        };
        additionalProperties: boolean;
    };
    GET_WORKFLOW: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
            workflowId: {
                type: string;
            };
            includeTasks: {
                type: string;
            };
        };
        additionalProperties: boolean;
    };
    GRPC: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    INTEGRATION: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    MCP_REMOTE: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    CHUNK_TEXT: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            inputParameters: {
                type: string[];
                properties: {
                    text: {
                        type: string;
                    };
                    chunkSize: {
                        type: string;
                    };
                    mediaType: {
                        type: string;
                    };
                };
                required: never[];
                additionalProperties: boolean;
            };
            type: {
                const: TaskType;
            };
        };
    };
    LIST_FILES: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
            inputParameters: {
                type: string;
                required: string[];
                properties: {
                    inputLocation: {
                        type: string;
                    };
                    integrationName: {
                        type: string;
                    };
                    outputLocation: {
                        type: string;
                    };
                    fileTypes: {
                        type: string;
                        items: {
                            type: string;
                        };
                    };
                    integrationNames: {
                        type: string;
                        additionalProperties: {
                            type: string;
                        };
                    };
                };
                additionalProperties: boolean;
            };
        };
    };
    PARSE_DOCUMENT: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: never[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                const: TaskType;
            };
            integrationName: {
                type: string;
            };
            url: {
                type: string;
            };
            mediaType: {
                type: string;
            };
            chunkSize: {
                type: string;
            };
        };
        additionalProperties: boolean;
    };
    AGENT: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    GET_AGENT_CARD: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    CANCEL_AGENT: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    LLM_SEARCH_EMBEDDINGS: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    LIST_MCP_TOOLS: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    CALL_MCP_TOOL: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    GENERATE_IMAGE: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    GENERATE_AUDIO: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    GENERATE_VIDEO: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
    GENERATE_PDF: {
        $id: string;
        type: string;
        description: string;
        default: {
            name: string;
            taskReferenceName: string;
            type: TaskType;
        };
        required: string[];
        properties: {
            name: {
                $ref: string;
            };
            taskReferenceName: {
                $ref: string;
            };
            type: {
                type: string;
                enum: TaskType[];
            };
        };
        additionalProperties: boolean;
    };
};
export declare const workflowSchema: {
    $id: string;
    required: string[];
    type: string;
    properties: {
        name: {
            $id: string;
            default: string;
            description: string;
            maxLength: number;
            pattern: string;
            title: string;
            type: string;
        };
        description: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: string;
        };
        version: {
            $id: string;
            default: number;
            description: string;
            title: string;
            minimum: number;
            type: string;
        };
        tasks: {
            $ref: string;
        };
        inputParameters: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: never[];
            examples: never[][];
            items: {
                $id: string;
            };
        };
        outputParameters: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: {};
            required: never[];
            properties: {};
            additionalProperties: boolean;
        };
        schemaVersion: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: number;
        };
        restartable: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: boolean;
        };
        workflowStatusListenerEnabled: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: boolean;
        };
        ownerEmail: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: string;
        };
        timeoutPolicy: {
            $id: string;
            type: string;
            enum: TimeoutPolicy[];
            title: string;
            description: string;
            default: string;
        };
        timeoutSeconds: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: number;
        };
        failureWorkflow: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: string;
        };
    };
    additionalProperties: boolean;
};
export declare const workflowDefinitionSchemaWithDeps: ({
    $id: string;
    type: string;
    pattern: string;
    title: string;
    description: string;
    default: string;
} | {
    $id: string;
    type: string;
    title: string;
    minLength: number;
    description: string;
} | {
    $id: string;
    anyOf: ({
        type: string;
        properties: {};
        additionalProperties: boolean;
        pattern?: undefined;
    } | {
        type: string;
        pattern: string;
        properties?: undefined;
        additionalProperties?: undefined;
    })[];
    title: string;
    description: string;
    default: {};
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            type: string;
            enum: TaskType[];
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            anyOf: ({
                type: string;
                required: string[];
                properties: {
                    evaluatorType: {
                        type: string;
                        enum: string[];
                    };
                    expression: {
                        type: string;
                    };
                    additionalProperties: boolean;
                };
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                required?: undefined;
                properties?: undefined;
            })[];
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    kafka_request: {
                        anyOf: ({
                            type: string;
                            properties: {
                                headers: {
                                    anyOf: ({
                                        type: string;
                                        pattern?: undefined;
                                    } | {
                                        type: string;
                                        pattern: string;
                                    })[];
                                };
                                key: {
                                    type: string;
                                };
                                value: {
                                    anyOf: {
                                        type: string;
                                    }[];
                                };
                            };
                            additionalProperties: boolean;
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            properties?: undefined;
                            additionalProperties?: undefined;
                        })[];
                    };
                };
                required: string[];
                additionalProperties: boolean;
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
                required?: undefined;
                additionalProperties?: undefined;
            })[];
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    properties: {
        uri: {
            type: string;
        };
        method: {
            type: string;
            anyOf: ({
                enum: HTTPMethods[];
                pattern?: undefined;
            } | {
                pattern: string;
                enum?: undefined;
            })[];
        };
        headers: {
            type: string[];
            patternProperties: {
                "^\\S*$": {
                    type: string;
                };
            };
            additionalProperties: boolean;
        };
        terminationCondition: {
            type: string;
        };
        pollingInterval: {
            type: string;
        };
        pollingStrategy: {
            type: string;
        };
        encode: {
            type: string;
        };
        additionalProperties: boolean;
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            type: string[];
            properties: {
                http_request: {
                    anyOf: ({
                        $ref: string;
                        type?: undefined;
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                        $ref?: undefined;
                    })[];
                };
            };
            additionalProperties: boolean;
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            type: string[];
            properties: {
                http_request: {
                    anyOf: ({
                        type: string;
                        $ref: string;
                        required: string[];
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                        $ref?: undefined;
                        required?: undefined;
                    })[];
                };
            };
            required: string[];
            additionalProperties: boolean;
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    queryExpression: {
                        type: string;
                    };
                };
                required: string[];
                additionalProperties: boolean;
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
                required?: undefined;
                additionalProperties?: undefined;
            })[];
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    terminationStatus: {
                        enum: string[];
                        type: string;
                    };
                    workflowOutput: {
                        type: string;
                    };
                };
                additionalProperties: boolean;
                required: string[];
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
                additionalProperties?: undefined;
                required?: undefined;
            })[];
        };
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            workflowId: string;
        };
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            anyOf: ({
                type: string;
                required: string[];
                properties: {
                    workflowId: {
                        anyOf: ({
                            type: string;
                            items?: undefined;
                        } | {
                            type: string;
                            items: {
                                type: string;
                            };
                        })[];
                    };
                    terminationReason: {
                        type: string;
                    };
                };
                additionalProperties: boolean;
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                required?: undefined;
                properties?: undefined;
                additionalProperties?: undefined;
            })[];
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            ruleFileLocation: string;
            executionStrategy: string;
            inputColumns: {};
            outputColumns: never[];
        };
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    inputColumns: {
                        anyOf: ({
                            type: string;
                            additionalProperties: boolean;
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            additionalProperties?: undefined;
                        })[];
                    };
                    outputColumns: {
                        anyOf: ({
                            type: string;
                            items: {
                                type: string;
                            };
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            items?: undefined;
                        })[];
                    };
                    additionalProperties: boolean;
                };
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
            })[];
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    required: string[];
    properties: {
        from: {
            type: string;
        };
        to: {
            type: string;
        };
        subject: {
            type: string;
        };
        contentType: {
            type: string;
        };
        content: {
            type: string;
        };
        sendgridConfiguration: {
            type: string;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
        inputParameters: {
            $ref: string;
        };
        additionalProperties: boolean;
    };
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            startWorkflow: {
                name: string;
                input: {};
            };
        };
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
    };
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        inputParameters: {
            matches: {
                type: string;
            };
        };
        type: TaskType;
        required: string[];
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    matches: {
                        anyOf: ({
                            type: string;
                            additionalProperties: boolean;
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            additionalProperties?: undefined;
                        })[];
                    };
                };
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
            })[];
        };
        type: {
            const: TaskType;
        };
    };
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    _humanTaskTemplate: {
                        type: string;
                    };
                    _humanTaskAssignmentPolicy: {
                        anyOf: ({
                            type: string;
                            properties: {
                                type: {
                                    type: string;
                                };
                                subjects: {
                                    type: string[];
                                    items: {
                                        type: string;
                                    };
                                };
                                groupId: {
                                    type: string;
                                };
                            };
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            properties?: undefined;
                        })[];
                    };
                    _humanTaskTimeoutPolicy: {
                        anyOf: ({
                            type: string;
                            properties: {
                                type: {
                                    type: string;
                                };
                                timeoutSeconds: {
                                    type: string;
                                };
                                subjects: {
                                    type: string[];
                                    items: {
                                        type: string;
                                    };
                                };
                            };
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            properties?: undefined;
                        })[];
                    };
                    additionalProperties: boolean;
                };
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
            })[];
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
        inputParameters: {
            connectionId: string;
            integrationName: string;
            statement: string;
            parameters: never[];
            expectedUpdateCount: number;
            jdbcType: JDBCType;
        };
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            type: string[];
            properties: {
                connectionId: {
                    type: string;
                };
                integrationName: {
                    type: string;
                };
                statement: {
                    type: string;
                };
                parameters: {
                    anyOf: ({
                        type: string;
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                    })[];
                };
                expectedUpdateCount: {
                    type: string;
                };
                type: {
                    type: string;
                    enum: JDBCType[];
                };
            };
            required: string[];
            additionalProperties: boolean;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
        inputParameters: {
            _secrets: {
                secretKey: string;
                secretValue: string;
            };
        };
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            anyOf: ({
                type: string;
                properties: {
                    _secrets: {
                        anyOf: ({
                            type: string;
                            properties: {
                                secretKey: {
                                    type: string;
                                };
                                secretValue: {
                                    type: string;
                                };
                            };
                            required: string[];
                            pattern?: undefined;
                        } | {
                            type: string;
                            pattern: string;
                            properties?: undefined;
                            required?: undefined;
                        })[];
                    };
                };
                required: string[];
                additionalProperties: boolean;
                pattern?: undefined;
            } | {
                type: string;
                pattern: string;
                properties?: undefined;
                required?: undefined;
                additionalProperties?: undefined;
            })[];
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
        inputParameters: {
            workflowNames: never[];
            statuses: never[];
            queryType: QueryProcessorType;
        };
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            type: string[];
            properties: {
                workflowNames: {
                    anyOf: ({
                        type: string;
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                    })[];
                };
                statuses: {
                    anyOf: ({
                        type: string;
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                    })[];
                };
                queryType: {
                    type: string;
                    enum: QueryProcessorType[];
                };
            };
            required: string[];
            additionalProperties: boolean;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
        inputParameters: {
            subject: string;
            issuer: string;
            privateKey: string;
            privateKeyId: string;
            audience: string;
            ttlInSecond: number;
            scopes: never[];
            algorithm: GetSignedJWTAlgorithmType;
        };
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            type: string[];
            properties: {
                subject: {
                    type: string;
                };
                issuer: {
                    type: string;
                };
                privateKey: {
                    type: string;
                };
                privateKeyId: {
                    type: string;
                };
                audience: {
                    type: string;
                };
                ttlInSecond: {
                    anyOf: ({
                        type: string;
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                    })[];
                };
                scopes: {
                    anyOf: ({
                        type: string;
                        items: {
                            type: string;
                        };
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                        items?: undefined;
                    })[];
                };
                algorithm: {
                    type: string;
                    enum: GetSignedJWTAlgorithmType.RS256[];
                };
            };
            required: never[];
            additionalProperties: boolean;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
        inputParameters: {};
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            type: string[];
            properties: {
                alias: {
                    type: string;
                };
                description: {
                    type: string;
                };
                message: {
                    type: string;
                };
            };
            required: never[];
            additionalProperties: boolean;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
        inputParameters: {};
    };
    required: never[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            type: string[];
            properties: {
                taskStatus: {
                    anyOf: ({
                        type: string;
                        enum: UpdateTaskStatus[];
                        pattern?: undefined;
                    } | {
                        type: string;
                        pattern: string;
                        enum?: undefined;
                    })[];
                };
                taskRefName: {
                    type: string;
                };
                workflowId: {
                    type: string;
                };
                taskId: {
                    type: string;
                };
                taskOutput: {
                    type: string;
                };
                mergeOutput: {
                    type: string;
                };
            };
            additionalProperties: boolean;
        };
        type: {
            const: TaskType;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
        workflowId: {
            type: string;
        };
        includeTasks: {
            type: string;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        inputParameters: {
            type: string[];
            properties: {
                text: {
                    type: string;
                };
                chunkSize: {
                    type: string;
                };
                mediaType: {
                    type: string;
                };
            };
            required: never[];
            additionalProperties: boolean;
        };
        type: {
            const: TaskType;
        };
    };
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: string[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
        inputParameters: {
            type: string;
            required: string[];
            properties: {
                inputLocation: {
                    type: string;
                };
                integrationName: {
                    type: string;
                };
                outputLocation: {
                    type: string;
                };
                fileTypes: {
                    type: string;
                    items: {
                        type: string;
                    };
                };
                integrationNames: {
                    type: string;
                    additionalProperties: {
                        type: string;
                    };
                };
            };
            additionalProperties: boolean;
        };
    };
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: TaskType;
    };
    required: never[];
    properties: {
        name: {
            $ref: string;
        };
        taskReferenceName: {
            $ref: string;
        };
        type: {
            const: TaskType;
        };
        integrationName: {
            type: string;
        };
        url: {
            type: string;
        };
        mediaType: {
            type: string;
        };
        chunkSize: {
            type: string;
        };
    };
    additionalProperties: boolean;
} | {
    $id: string;
    type: string;
    title: string;
    description: string;
    default: never[];
    items: {
        $id: string;
        oneOf: {
            $ref: string;
        }[];
    };
} | {
    $id: string;
    required: string[];
    type: string;
    properties: {
        name: {
            $id: string;
            default: string;
            description: string;
            maxLength: number;
            pattern: string;
            title: string;
            type: string;
        };
        description: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: string;
        };
        version: {
            $id: string;
            default: number;
            description: string;
            title: string;
            minimum: number;
            type: string;
        };
        tasks: {
            $ref: string;
        };
        inputParameters: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: never[];
            examples: never[][];
            items: {
                $id: string;
            };
        };
        outputParameters: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: {};
            required: never[];
            properties: {};
            additionalProperties: boolean;
        };
        schemaVersion: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: number;
        };
        restartable: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: boolean;
        };
        workflowStatusListenerEnabled: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: boolean;
        };
        ownerEmail: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: string;
        };
        timeoutPolicy: {
            $id: string;
            type: string;
            enum: TimeoutPolicy[];
            title: string;
            description: string;
            default: string;
        };
        timeoutSeconds: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: number;
        };
        failureWorkflow: {
            $id: string;
            type: string;
            title: string;
            description: string;
            default: string;
        };
    };
    additionalProperties: boolean;
})[];
