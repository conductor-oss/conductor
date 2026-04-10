import { Box, Grid, Stack, Typography } from "@mui/material";
import FormControlLabel from "@mui/material/FormControlLabel";
import { useSelector } from "@xstate/react";
import Button from "components/ui/buttons/MuiButton";
import MuiCheckbox from "components/ui/MuiCheckbox";
import MuiTypography from "components/ui/MuiTypography";
import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import {
  crumbsToTaskSteps,
  forkLastTaskReferences,
  tasksAsNodes,
} from "components/features/flow/nodes/mapper";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import _difference from "lodash/difference";
import _first from "lodash/first";
import { path as _path } from "lodash/fp";
import _initial from "lodash/initial";
import _isEqual from "lodash/isEqual";
import _last from "lodash/last";
import _nth from "lodash/nth";
import { WorkflowEditContext } from "pages/definition/state";
import { useCallback, useContext, useEffect, useMemo, useState } from "react";
import { JoinTaskDef, TaskDef, TaskType } from "types/index";
import { updateField } from "utils/fieldHelpers";
import { Optional } from "../OptionalFieldForm";
import TaskFormSection from "../TaskFormSection";
import { TaskFormProps } from "../types";
import { JoinCodeBlock } from "./JoinCodeBlock";

const DEFAULT_EXPRESSION =
  '(function(){\n  let results = {};\n  let pendingJoinsFound = false;\n  if($.joinOn){\n    $.joinOn.forEach((element)=>{\n      if($[element] && $[element].status !== \'COMPLETED\'){\n        results[element] = $[element].status;\n        pendingJoinsFound = true;\n      }\n    });\n    if(pendingJoinsFound){\n      return {\n        "status":"IN_PROGRESS",\n        "reasonForIncompletion":"Pending",\n        "outputData":{\n          "scriptResults": results\n        }\n      };\n    }\n    // To complete the Join - return true OR an object with status = \'COMPLETED\' like above.\n    return true;\n  }\n})();';

const EXPRESSION_PATH = "expression";
const INPUT_PARAMETERS_PATH = "inputParameters";

export const JOINTaskForm = ({ task, onChange }: TaskFormProps) => {
  const [possibleTaskReferences, setPossibleTaskReferences] = useState<
    string[]
  >([]);

  const [showConfirmOverrideDialog, setShowConfirmOverrideDialog] =
    useState(false);

  const { workflowDefinitionActor } = useContext(WorkflowEditContext);
  const selectedTaskCrumbs = useSelector(
    workflowDefinitionActor!,
    (state) => state.context.selectedTaskCrumbs,
  );
  const editorTasks = useSelector(
    workflowDefinitionActor!,
    (state) => state.context.workflowChanges.tasks,
  );

  const tasksInCrumbBranch = useMemo(() => {
    return _initial(crumbsToTaskSteps(selectedTaskCrumbs, editorTasks));
  }, [editorTasks, selectedTaskCrumbs]);

  const forkLastTaskReferencesWrapper = async (forkTask: TaskDef[]) => {
    if (forkTask?.length > 0 && _last(forkTask)?.type === TaskType.TERMINATE) {
      return [];
    }
    if (forkTask?.length === 1 && _first(forkTask)?.type === TaskType.SWITCH) {
      return [_first(forkTask)?.taskReferenceName];
    }
    return forkLastTaskReferences(forkTask, tasksAsNodes);
  };

  useEffect(() => {
    const forkTasksInBranch = tasksInCrumbBranch.reduce(
      (acc: any, ct: any, idx: number) =>
        ct.type === TaskType.FORK_JOIN
          ? acc.concat(
              Promise.all(
                ct.forkTasks.map((t: TaskDef[]) =>
                  forkLastTaskReferencesWrapper(t),
                ),
              ).then((trList) => {
                return _difference(
                  trList.flat(),
                  (_nth(tasksInCrumbBranch, idx + 1) as any)?.joinOn || [],
                );
              }),
            )
          : acc,
      [],
    );
    async function setPossibleTaskReferencesAsync(
      upperForkTask: Promise<string>[],
    ) {
      const taskReferences = await Promise.all(upperForkTask);
      setPossibleTaskReferences(taskReferences.flat());
    }
    setPossibleTaskReferencesAsync(forkTasksInBranch);
  }, [tasksInCrumbBranch]);

  const onChangeHandler = useCallback(
    (a: any) => {
      if (!a || !a.target) return;
      const { name, checked } = a.target;
      const currentSelections = task.joinOn;
      const validCurrentSelections = possibleTaskReferences.filter((tr) =>
        currentSelections?.includes(tr),
      );
      onChange({
        ...task,
        joinOn: checked
          ? validCurrentSelections.concat(name)
          : validCurrentSelections.filter((n) => n !== name),
      });
    },
    [onChange, possibleTaskReferences, task],
  );

  const handleApplySampleScript = useCallback(() => {
    onChange(updateField(EXPRESSION_PATH, DEFAULT_EXPRESSION, task));
  }, [task, onChange]);

  const checkEveryJoin = useCallback(() => {
    if (possibleTaskReferences.length > 0) {
      onChange(updateField("joinOn", possibleTaskReferences, task));
    }
  }, [task, onChange, possibleTaskReferences]);

  const unSelectAll = () => {
    onChange(updateField("joinOn", [], task));
  };

  const hasScriptExpression = useMemo((): boolean => {
    return _path(EXPRESSION_PATH, task) != null;
  }, [task]);

  const toggleScriptExpression = useCallback(() => {
    if (hasScriptExpression) {
      onChange({ ...task, expression: undefined, evaluatorType: undefined });
    } else {
      onChange({ ...task, expression: "", evaluatorType: "js" });
    }
  }, [task, onChange, hasScriptExpression]);

  const isEveryJoinSelected = useMemo(() => {
    const selectedJoins = task?.joinOn || [];
    return _isEqual(selectedJoins.sort(), possibleTaskReferences.sort());
  }, [task, possibleTaskReferences]);

  return (
    <Box width="100%">
      <TaskFormSection
        title={
          <Stack
            direction={"row"}
            spacing={4}
            mt={2}
            alignContent={"center"}
            alignItems={"center"}
          >
            <Typography
              fontWeight={600}
              sx={{
                opacity: 0.6,
              }}
            >
              Input joins
            </Typography>
            <Button
              size="small"
              onClick={!isEveryJoinSelected ? checkEveryJoin : unSelectAll}
              disabled={possibleTaskReferences?.length === 0}
            >
              {isEveryJoinSelected && possibleTaskReferences?.length !== 0
                ? "Unselect all"
                : "Select all"}
            </Button>
          </Stack>
        }
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Grid
          container
          wrap="wrap"
          direction={"row"}
          py={possibleTaskReferences?.length > 0 ? 2 : 1}
          sx={{ width: "100%" }}
          id="input-joins-section"
        >
          {possibleTaskReferences?.map((forkTaskReferenceName) => (
            <Grid key={forkTaskReferenceName}>
              <FormControlLabel
                onChange={onChangeHandler}
                control={
                  <MuiCheckbox
                    name={forkTaskReferenceName}
                    checked={task?.joinOn?.includes(forkTaskReferenceName)}
                  />
                }
                label={forkTaskReferenceName}
              />
            </Grid>
          ))}
        </Grid>
      </TaskFormSection>
      <Box pt={2} pb={2}>
        <TaskFormSection
          title="Script Parameters"
          accordionAdditionalProps={{ defaultExpanded: true }}
        >
          <Grid container sx={{ width: "100%" }} spacing={3}>
            <Grid size={12}>
              <ConductorFlatMapFormBase
                showFieldTypes={true}
                keyColumnLabel="Key"
                valueColumnLabel="Value"
                addItemLabel="Add parameter"
                value={_path(INPUT_PARAMETERS_PATH, task)}
                onChange={(value) =>
                  onChange(updateField(INPUT_PARAMETERS_PATH, value, task))
                }
                autoFocusField={false}
              />
            </Grid>
          </Grid>
        </TaskFormSection>
      </Box>
      <TaskFormSection
        title="Join script (optional)"
        accordionAdditionalProps={{ defaultExpanded: true }}
      >
        <Stack spacing={3}>
          <Box>
            <FormControlLabel
              onChange={toggleScriptExpression}
              control={
                <MuiCheckbox
                  name={"joinScript"}
                  checked={hasScriptExpression}
                />
              }
              label={"Use scripting to determine join"}
            />
          </Box>
          <Box>
            <MuiTypography
              variant="body2"
              color="#000000"
              paddingLeft={11}
              fontSize="12px"
            >
              When checked, you must provide a script to control how the join
              task completes. The script will have access to a variable called{" "}
              <strong>$.joinOn</strong> which is an array of the task references
              mapped to this join, and the output data of each joined task, such
              as <i>$['task-reference-name']</i>
              <Button
                variant="text"
                href="https://orkes.io/content/reference-docs/operators/join#join-script-configuration"
                target="_blank"
                rel="noopener noreferrer"
                size="small"
              >
                Learn more.
              </Button>
            </MuiTypography>
          </Box>
          <Box>
            <FormControlLabel
              onChange={() => setShowConfirmOverrideDialog(true)}
              control={
                <MuiCheckbox
                  name={"joinScript"}
                  checked={_path(EXPRESSION_PATH, task) === DEFAULT_EXPRESSION}
                />
              }
              label={"Apply sample script template"}
            />
          </Box>
          {hasScriptExpression ? (
            <JoinCodeBlock
              label="Code"
              language="javascript"
              minHeight={150}
              autoformat={false}
              languageLabel="ECMASCRIPT"
              autoSizeBox={true}
              task={task as Partial<JoinTaskDef>}
              onChange={onChange}
            />
          ) : null}
        </Stack>
      </TaskFormSection>
      {showConfirmOverrideDialog && (
        <ConfirmChoiceDialog
          handleConfirmationValue={(confirmed) => {
            if (confirmed) {
              handleApplySampleScript();
            }
            setShowConfirmOverrideDialog(false);
          }}
          message={
            "Applying the sample script will overwrite any existing script. Are you sure you want to proceed?"
          }
        />
      )}
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
