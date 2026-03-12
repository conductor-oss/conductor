import _dropRight from "lodash/dropRight";
import _merge from "lodash/merge";
import _set from "lodash/set";
import { TimeoutPolicy } from "types/TimeoutPolicy";
import { WORKFLOW_NAME_ERROR_MESSAGE } from "utils/constants/common";
import {
  nameSchema,
  workflowDefinitionSchemaWithDeps,
  workflowSchema,
} from "./Schemas";

export const nameSchemaAjv = _merge({}, nameSchema, {
  errorMessage: {
    pattern: WORKFLOW_NAME_ERROR_MESSAGE,
  },
});

// use 1st parameter as an empty object to prevent mutating the original object
export const workflowSchemaAjv = _merge({}, workflowSchema, {
  properties: {
    name: {
      errorMessage: {
        pattern: WORKFLOW_NAME_ERROR_MESSAGE,
        maxLength: "name must less than 100 characters.",
      },
    },
    timeoutPolicy: {
      errorMessage: {
        enum: `timeoutPolicy must be ${Object.values(TimeoutPolicy).join(
          " | ",
        )}.`,
      },
    },
  },
});

const schemaWithDepsAjv = _dropRight(workflowDefinitionSchemaWithDeps);

// override nameSchema
_set(schemaWithDepsAjv, 0, nameSchemaAjv);

export const workflowDefinitionSchemaWithDepsAjv = [
  // Note that order matters here.
  ...schemaWithDepsAjv,
  // workflow must be at the end, because it wraps another schemas
  workflowSchemaAjv,
];
