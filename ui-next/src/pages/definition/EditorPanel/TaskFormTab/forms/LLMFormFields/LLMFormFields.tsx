import { Grid } from "@mui/material";
import { TaskDef } from "types";
import { UiIntegrationsFieldType } from "types/FormFieldTypes";
import { FieldComponentType } from "utils/fieldHelpers";
import { ActorRef } from "xstate";
import { LLMFormFieldsEvents } from "./state";

interface LLMFormFieldsProps {
  onChange: (task: Partial<TaskDef>) => void;
  task: Partial<TaskDef>;
  fieldFieldComponents: Array<[UiIntegrationsFieldType, FieldComponentType]>;
  actor: ActorRef<LLMFormFieldsEvents>;
}

const sizeMap = (type: UiIntegrationsFieldType) => {
  if (
    [
      UiIntegrationsFieldType.PROMPT_NAME,
      UiIntegrationsFieldType.VECTOR_DB,
      UiIntegrationsFieldType.MESSAGES,
      UiIntegrationsFieldType.INSTRUCTIONS,
      UiIntegrationsFieldType.JSON_OUTPUT,
      UiIntegrationsFieldType.STOP_WORDS,
    ].includes(type)
  ) {
    return 12;
  }
  if (
    [
      UiIntegrationsFieldType.TEMPERATURE,
      UiIntegrationsFieldType.TOP_P,
    ].includes(type)
  ) {
    return 3;
  }
  return 6;
};

export const LLMFormFields = ({
  fieldFieldComponents,
  onChange,
  task,
  actor,
}: LLMFormFieldsProps) => {
  return (
    <Grid
      container
      sx={{ width: "100%" }}
      spacing={3}
      key={task.taskReferenceName}
    >
      {fieldFieldComponents.map(([type, FieldComponent]) => {
        return (
          <Grid
            key={type}
            sx={{
              alignSelf:
                type === UiIntegrationsFieldType.EMBEDDINGS ? "end" : "",
            }}
            size={sizeMap(type)}
          >
            <FieldComponent onChange={onChange} actor={actor} task={task} />
          </Grid>
        );
      })}
    </Grid>
  );
};

export type { LLMFormFieldsProps };
