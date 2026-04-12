import { useMemo, Fragment, FunctionComponent, ReactNode } from "react";
import { Article as FormIcon } from "@phosphor-icons/react";
import { Box, ToggleButton, Tooltip, Stack, SxProps } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import _isString from "lodash/isString";
import { FormTaskType } from "types/TaskType";
import _path from "lodash/fp/path";
import { taskGeneratorMap } from "components/features/flow/nodes";
import _isNil from "lodash/isNil";
import HelperText from "components/ui/inputs/HelperText";

interface MaybeVariableProps {
  value: string | any;
  onChange: (v: any) => void;
  path: string;
  taskType: FormTaskType;
  children?: ReactNode;
  helperTextStyle?: SxProps;
  fieldStyle?: SxProps;
}

type ValidTypes = "string" | "object";

export const MaybeVariable: FunctionComponent<MaybeVariableProps> = ({
  children,
  value,
  onChange,
  path,
  taskType,
  helperTextStyle = {},
  fieldStyle = {},
}) => {
  const valueType = useMemo(
    (): ValidTypes => (_isString(value) ? "string" : "object"),
    [value],
  );

  const handleChangeType = () => {
    const generateTask = taskGeneratorMap[taskType];
    const newTask = generateTask({});
    const valueForObject = _path(path, newTask);
    onChange(_isNil(valueForObject) ? {} : valueForObject);
  };

  const referenceKey = path.split(".").slice(-1).join("");
  return (
    <Fragment>
      <Box
        width="100%"
        sx={{
          display: "flex",
          justifyContent: "flex-end",
          paddingRight: 6,
          paddingBottom: 2,
          ...helperTextStyle,
        }}
      >
        {valueType === "string" && (
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 4,
              flexWrap: "wrap",
            }}
          >
            <HelperText>
              Selecting form fields will show form with default value
            </HelperText>
            <ToggleButton
              value="object"
              size="small"
              onClick={handleChangeType}
            >
              <Tooltip title="Form fields">
                <FormIcon size={16} />
              </Tooltip>
            </ToggleButton>
          </Box>
        )}
      </Box>
      {valueType === "string" ? (
        <Box py={3} px={0} sx={fieldStyle}>
          <Stack
            direction="row"
            alignContent="center"
            alignItems={"center"}
            spacing={2}
            width="100%"
          >
            <Box width="100%">
              <ConductorAutocompleteVariables
                label={`${referenceKey}:`}
                onChange={onChange}
                value={(value as string) || ""}
                fullWidth
              />
            </Box>
          </Stack>
        </Box>
      ) : (
        children
      )}
    </Fragment>
  );
};
