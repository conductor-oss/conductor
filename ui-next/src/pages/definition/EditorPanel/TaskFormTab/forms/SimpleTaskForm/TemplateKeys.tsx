import { Box, CircularProgress, Stack } from "@mui/material";
import { Intersect } from "@phosphor-icons/react";
import Button from "components/MuiButton";
import IconButton from "components/MuiIconButton";
import MuiTypography from "components/MuiTypography";
import StrikedText from "components/StrikedText";
import Text from "components/Text";
import { FunctionComponent, useCallback, useMemo } from "react";
import { Link } from "@mui/material";
import { TaskDef } from "types";
import { FIELD_TYPE_OBJECT, IObject } from "types/common";
import { FEATURES, featureFlags, inferType, useFetch } from "utils";
import TaskFormSection from "../TaskFormSection";

const taskVisibility = featureFlags.getValue(FEATURES.TASK_VISIBILITY, "READ");

const grayedTextFieldLikeStyles = {
  padding: "4px 10px",
  borderRadius: "5px",
  background: "rgba(0,0,0,.15)",
  width: "100%",
};

const deserializeOrDash = (value: any): string => {
  try {
    const result = JSON.stringify(value, null, 2);
    return result;
  } catch {
    return "-";
  }
};

export interface TemplateKeysProps {
  task: Partial<TaskDef>;
  onUniteParameter: (partialInputParams: Record<string, unknown>) => void;
}

export const TemplateKeys: FunctionComponent<TemplateKeysProps> = ({
  task,
  onUniteParameter,
}) => {
  const {
    data,
    isFetching,
    refetch: refetchAllDefinitions,
  } = useFetch(`/metadata/taskdefs?access=${taskVisibility}`);

  const maybeTemplate = useMemo<IObject>(() => {
    const maybeSelectedTask = data?.find((t: TaskDef) => t.name === task.name);
    return maybeSelectedTask?.inputTemplate;
  }, [task, data]);

  const [currentTaskInputParams, inputParamsKeys] = useMemo(() => {
    const inputParameters = task.inputParameters || {};
    return [inputParameters, Object.keys(inputParameters)];
  }, [task]);

  const handleUniteParameter = useCallback(
    (keyParm: string) => {
      onUniteParameter({
        ...currentTaskInputParams,
        [keyParm]: maybeTemplate?.[keyParm],
      });
    },
    [maybeTemplate, onUniteParameter, currentTaskInputParams],
  );

  const copyAllValues = useCallback(() => {
    onUniteParameter({
      ...currentTaskInputParams,
      ...maybeTemplate,
    });
  }, [maybeTemplate, onUniteParameter, currentTaskInputParams]);

  const withTemplateKeysContent = useMemo(
    () =>
      maybeTemplate == null ? (
        <MuiTypography>No default input templates configured</MuiTypography>
      ) : (
        <Stack spacing={1}>
          <Box
            alignContent={"flex-end"}
            alignItems={"end"}
            alignSelf={"end"}
            pl={20}
          >
            <Button size="small" onClick={copyAllValues}>
              Override all
            </Button>
          </Box>
          {Object.entries(maybeTemplate || {}).map(([key, value]) => {
            const nonActionable = inputParamsKeys.includes(key);
            const TextComponent = nonActionable ? StrikedText : Text;
            return (
              <Stack direction={"row"} spacing={4} key={key}>
                <TextComponent sx={grayedTextFieldLikeStyles}>
                  {key}
                </TextComponent>
                <TextComponent sx={grayedTextFieldLikeStyles}>
                  {inferType(value) === FIELD_TYPE_OBJECT
                    ? deserializeOrDash(value)
                    : value}
                </TextComponent>
                {nonActionable ? (
                  <Box sx={{ minWidth: "32px" }} />
                ) : (
                  <IconButton onClick={() => handleUniteParameter(key)}>
                    <Intersect size={16} />
                  </IconButton>
                )}
              </Stack>
            );
          })}
        </Stack>
      ),
    [maybeTemplate, inputParamsKeys, handleUniteParameter, copyAllValues],
  );

  return (
    <TaskFormSection
      title={
        <Stack
          direction="row"
          spacing={2}
          pt={3}
          alignContent={"center"}
          alignItems={"center"}
        >
          <Box
            sx={{
              fontWeight: 600,
              opacity: 0.6,
            }}
          >
            Input templates (Default key-values from task definitions)
          </Box>
          <Button size="small" onClick={() => refetchAllDefinitions()}>
            Refresh
          </Button>
        </Stack>
      }
      accordionAdditionalProps={{ defaultExpanded: true }}
    >
      <Stack spacing={2} pt={2}>
        <MuiTypography>
          <Box component="span" sx={{ opacity: 0.5 }}>
            These are default inputs that will be provided into your task unless
            its <strong>overridden</strong> with an input parameter. To edit
            these default input templates - click to{" "}
          </Box>
          <Link
            href={`/taskDef/${encodeURIComponent(task?.name ?? "")}`}
            target="_blank"
            rel="noopener noreferrer"
            style={{
              fontWeight: 400,
              fontSize: "12px",
            }}
          >
            Edit task definition
          </Link>
        </MuiTypography>
        {isFetching ? (
          <Box
            width={"100%"}
            style={{ display: "flex", justifyContent: "center" }}
          >
            <CircularProgress />
          </Box>
        ) : (
          withTemplateKeysContent
        )}
      </Stack>
    </TaskFormSection>
  );
};
