import { TimeoutPolicy } from "types/TimeoutPolicy";
export declare const nameSchemaAjv: {
    $id: string;
    type: string;
    pattern: string;
    title: string;
    description: string;
    default: string;
} & {
    errorMessage: {
        pattern: string;
    };
};
export declare const workflowSchemaAjv: {
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
} & {
    properties: {
        name: {
            errorMessage: {
                pattern: string;
                maxLength: string;
            };
        };
        timeoutPolicy: {
            errorMessage: {
                enum: string;
            };
        };
    };
};
export declare const workflowDefinitionSchemaWithDepsAjv: ({
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
        type: import("./common").TaskType;
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
            enum: import("./common").TaskType[];
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
                enum: import("./TaskType").HTTPMethods[];
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
        };
    };
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
        inputParameters: {
            connectionId: string;
            integrationName: string;
            statement: string;
            parameters: never[];
            expectedUpdateCount: number;
            jdbcType: import("./TaskType").JDBCType;
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
                    enum: import("./TaskType").JDBCType[];
                };
            };
            required: string[];
            additionalProperties: boolean;
        };
        type: {
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
        inputParameters: {
            workflowNames: never[];
            statuses: never[];
            queryType: import("./TaskType").QueryProcessorType;
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
                    enum: import("./TaskType").QueryProcessorType[];
                };
            };
            required: string[];
            additionalProperties: boolean;
        };
        type: {
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
        inputParameters: {
            subject: string;
            issuer: string;
            privateKey: string;
            privateKeyId: string;
            audience: string;
            ttlInSecond: number;
            scopes: never[];
            algorithm: import("./TaskType").GetSignedJWTAlgorithmType;
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
                    enum: import("./TaskType").GetSignedJWTAlgorithmType.RS256[];
                };
            };
            required: never[];
            additionalProperties: boolean;
        };
        type: {
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
                        enum: import("./UpdateTaskStatus").UpdateTaskStatus[];
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
        };
    };
} | {
    $id: string;
    type: string;
    description: string;
    default: {
        name: string;
        taskReferenceName: string;
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
        type: import("./common").TaskType;
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
            const: import("./common").TaskType;
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
