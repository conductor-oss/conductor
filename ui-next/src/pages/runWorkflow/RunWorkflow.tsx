import { Box, Grid, Paper, Theme } from "@mui/material";
import { Button } from "components";
import MuiAlert from "components/MuiAlert";
import NavLink from "components/NavLink";
import { ConductorAutoComplete } from "components/v1";
import { ConductorCodeBlockInput } from "components/v1/ConductorCodeBlockInput";
import ConductorInput from "components/v1/ConductorInput";
import SplitButton from "components/v1/ConductorSplitButton";
import PlayIcon from "components/v1/icons/PlayIcon";
import ResetIcon from "components/v1/icons/ResetIcon";
import XCloseIcon from "components/v1/icons/XCloseIcon";
import { RunWorkflowHistoryTable } from "pages/definition/RunWorkflow/RunWorkflowHistoryTable";
import {
  IdempotencyStrategyEnum,
  IdempotencyValuesProp,
} from "pages/definition/RunWorkflow/state";
import { useEffect, useMemo, useState } from "react";
import { Helmet } from "react-helmet";
import { useLocation, useNavigate } from "react-router";
import { useQueryState } from "react-router-use-location-state";
import { editor } from "shared/editor";
import SectionContainer from "shared/SectionContainer";
import SectionHeader from "shared/SectionHeader";
import { useAuth } from "shared/auth";
import { colors } from "theme/tokens/variables";
import { logger, tryToJson, useLocalStorage } from "utils/index";
import { useAction, useWorkflowDefsByVersions } from "utils/query";
import { v4 as uuidv4 } from "uuid";
import IdempotencyForm from "./IdempotencyForm";
import {
  BuildQueryOutput,
  RunWorkflowApiSearchModal,
} from "./RunWorkflowApiSearchModal";
import { getTemplateFromInputParams } from "./runWorkflowUtils";

type InputParameterType = {
  inputParameters: string[];
};

type CommonProperties = {
  correlationId: string;
  input: object;
  taskToDomain?: object;
  idempotencyKey?: string;
  idempotencyStrategy?: IdempotencyStrategyEnum;
};

type RunWorkflowParamType = {
  name: string;
  version: string;
} & CommonProperties;

interface LocationState {
  state: {
    execution?: {
      workflowName: string;
      workflowVersion: number;
    } & CommonProperties;
  };
}

interface RunWorkflowState {
  workflowType: string;
  workflowVersion: string | null;
  workflowVersions: string[];
  workflowInputParams: string[];
  workflowInputTemplate: string;
  taskToDomain: string;
  workflowCorrelationId: string;
  lastCreatedWorkflowId: string;
  idempotencyKey: string;
  idempotencyStrategy?: IdempotencyStrategyEnum;
}

const style = {
  root: {
    padding: 0,
    margin: 0,
    color: "rgba(0, 0, 0, 1)",
    borderLeft: "solid var(--backgroundLight) 2px",
    border: "4px solid green",
  },
  monaco: {
    padding: "10px",
    borderStyle: "solid",
    borderColor: (theme: Theme) =>
      theme.palette?.mode === "dark" ? colors.gray04 : colors.gray11,
    borderWidth: "1px",
    borderRadius: "4px",
    "&:focus-within": {
      margin: "-2px",
      borderColor: "rgb(73, 105, 228)",
      borderStyle: "solid",
      borderWidth: "2px",
    },
  },
  label: {
    display: "block",
    marginBottom: "8px",
  },
  menuBg: {
    background: "none",
    color: "black",
  },
  listClass: {
    fontSize: "14px",
    lineHeight: 2.1,
    display: "flex",
    justifyContent: "flex-start",
    paddingLeft: "30px",
    color: "#293845",
    "&:hover": {
      backgroundColor: "var(--backgroundLightest)",
    },
    "&.active": {
      backgroundColor: "var(--backgroundLightest)",
    },
  },
  buttonSpacer: {
    paddingTop: "30px",
    paddingLeft: "10px",
  },
  controls: {
    height: "calc(100%)",
    overflowY: "auto",
    width: "calc(100%)",
    overflowX: "hidden",
  },
  inputBox: {
    "& textarea": {
      padding: "0 10px",
    },
    "& input": {
      padding: "0 10px",
    },
  },
  largeInputBox: {
    width: "100%",
    "& div": {
      width: "100%",
    },
    "& input": {
      width: "100%",
    },
  },
  labelText: {
    position: "relative",
    fontSize: "13px",
    transform: "none",
    fontWeight: 600,
    paddingLeft: 0,
    marginBottom: "0.3em",
    color: "#767676",
  },
  dropDown: {
    "& .MuiOutlinedInput-root.MuiInputBase-sizeSmall .MuiAutocomplete-input": {
      padding: "2.5px 10px 2.5px",
    },
  },
};

const GENERIC_ERROR_MESSAGE = "Error while running workflow.";
const INVALID_DATA_MESSAGE = "Invalid data. Cannot run Workflow.";

// function getInputAreaLength(workflowInputTemplate: string) {
//   return Math.max(
//     6,
//     (workflowInputTemplate || "").split(/\r\n|\r|\n/).length + 1
//   );
// }

const INITIAL_STATE = {
  workflowType: "",
  workflowVersion: null,
  workflowVersions: [],
  workflowInputParams: [],
  workflowInputTemplate: "",
  taskToDomain: "",
  workflowCorrelationId: "",
  lastCreatedWorkflowId: "",
  idempotencyKey: "",
  idempotencyStrategy: IdempotencyStrategyEnum.RETURN_EXISTING,
};

export function RunWorkflow() {
  const [workflowHistory, setWorkflowHistory] = useLocalStorage(
    "workflowHistory",
    [],
  );

  const { isTrialExpired } = useAuth();

  const workflowDefByVersions = useWorkflowDefsByVersions();
  const workflowNames = useMemo(
    (): string[] =>
      workflowDefByVersions
        ? Array.from(workflowDefByVersions.get("lookups").keys())
        : [],
    [workflowDefByVersions],
  );

  const location: LocationState = useLocation();
  const latestExecution = useMemo(() => location.state?.execution, [location]);

  const [selectedWorkflow, setSelectedWorkflow] = useLocalStorage(
    "selectedWorkflow",
    {},
  );
  const memorizedState = useMemo(() => {
    const workflowName =
      latestExecution?.workflowName || selectedWorkflow?.name;
    return {
      ...INITIAL_STATE,
      workflowType: workflowName || null,
      workflowVersion:
        latestExecution?.workflowVersion?.toString() ||
        selectedWorkflow?.version ||
        null,
      workflowVersions: workflowName
        ? workflowDefByVersions.get("lookups").get(workflowName) || []
        : [],
      workflowInputParams: [],
      workflowInputTemplate:
        JSON.stringify(latestExecution?.input, null, 2) ||
        selectedWorkflow?.workflowInput ||
        "",
      taskToDomain: latestExecution?.taskToDomain
        ? JSON.stringify(latestExecution.taskToDomain, null, 2)
        : "",
      workflowCorrelationId: latestExecution?.correlationId
        ? latestExecution.correlationId
        : "",
      lastCreatedWorkflowId: "",
    };
  }, [latestExecution, selectedWorkflow, workflowDefByVersions]);

  const [runWorkflowState, setRunWorkflowState] =
    useState<RunWorkflowState>(memorizedState);
  const [errorMessage, setErrorMessage] = useState("");
  const [showCodeDialog, setShowCodeDialog] = useQueryState("displayCode", "");

  const runWorkflowAction = useAction(
    "/workflow",
    "post",
    {
      onSuccess(data: string, input: { body: string }) {
        setRunWorkflowState({
          ...runWorkflowState,
          lastCreatedWorkflowId: data,
        });
        setErrorMessage("");
        const existingHistory = workflowHistory || [];
        const newHistoryItem = {
          id: uuidv4(),
          executionLink: data,
          executionTime: Date.now(),
        };
        const parsedBody = tryToJson(input.body);
        if (parsedBody) {
          Object.assign(newHistoryItem, parsedBody);
        }
        setWorkflowHistory([newHistoryItem, ...existingHistory].slice(0, 20));
      },
      onError: (error: Response) => {
        const parseErrorResponse = async () => {
          try {
            const json = await error.json();
            if (json?.message) {
              setErrorMessage(json.message);
            } else {
              setErrorMessage(GENERIC_ERROR_MESSAGE);
            }
          } catch {
            setErrorMessage(GENERIC_ERROR_MESSAGE);
          }
        };
        parseErrorResponse();
      },
    },
    true,
  );

  const setLastCreatedWorkflowId = function (value: string) {
    setRunWorkflowState((prevState) => ({
      ...prevState,
      lastCreatedWorkflowId: value,
    }));
  };
  const templateForNoInput = () => {
    return JSON.stringify({}, null, 2);
  };

  const setWorkflowTypeState = function (workflowType: string) {
    let workflowVersionsVal = [];

    if (workflowType !== null) {
      workflowVersionsVal = workflowDefByVersions
        .get("lookups")
        .get(workflowType);
    }

    let versionObj = {
      workflowVersion: null,
      workflowInputParams: [] as string[],
      workflowInputTemplate: "",
    };

    if (workflowVersionsVal.length > 0) {
      const latestVersion = workflowVersionsVal.slice(-1).pop();
      let def: InputParameterType = {
        inputParameters: [],
      };
      if (latestVersion !== null) {
        def = workflowDefByVersions
          .get("values")
          .get(workflowType)
          .get(latestVersion);
      }

      const templateFromInputParams =
        def["inputParameters"].length > 0
          ? getTemplateFromInputParams(def["inputParameters"])
          : templateForNoInput();

      versionObj = {
        workflowVersion: latestVersion,
        workflowInputParams: def["inputParameters"],
        workflowInputTemplate: templateFromInputParams,
      };
    }

    setRunWorkflowState({
      ...runWorkflowState,
      ...versionObj,
      workflowVersions: workflowVersionsVal,
      workflowType: workflowType,
      taskToDomain: "",
      workflowCorrelationId: runWorkflowState.workflowCorrelationId,
      lastCreatedWorkflowId: "",
    });
    setWorkflowInputTemplatesState(versionObj.workflowInputTemplate);
  };

  const setWorkflowVersionState = function (workflowVersion: string) {
    let def: InputParameterType = {
      inputParameters: [],
    };
    if (workflowVersion !== null) {
      def = workflowDefByVersions
        .get("values")
        .get(runWorkflowState.workflowType)
        .get(workflowVersion);
    }
    const templateFromInputParams = getTemplateFromInputParams(
      def["inputParameters"],
    );
    setRunWorkflowState({
      ...runWorkflowState,
      workflowVersion: workflowVersion,
      workflowInputParams: def["inputParameters"],
      workflowInputTemplate: templateFromInputParams,
    });
    setWorkflowInputTemplatesState(templateFromInputParams);
  };

  const runThisWorkflow = function () {
    //TODO per input validation
    try {
      const input =
        (runWorkflowState.workflowInputTemplate &&
          JSON.parse(runWorkflowState.workflowInputTemplate)) ||
        undefined;
      const taskToDomain =
        (runWorkflowState.taskToDomain &&
          JSON.parse(runWorkflowState.taskToDomain)) ||
        undefined;
      const postObject = {
        name: runWorkflowState.workflowType,
        version: runWorkflowState.workflowVersion,
        correlationId: runWorkflowState.workflowCorrelationId,
        taskToDomain,
        input,
        idempotencyKey: runWorkflowState.idempotencyKey,
        ...(runWorkflowState.idempotencyKey &&
          runWorkflowState.idempotencyStrategy && {
            idempotencyStrategy: runWorkflowState.idempotencyStrategy,
          }),
      };
      const postBody = JSON.stringify(postObject);
      // @ts-ignore
      runWorkflowAction.mutate({
        body: postBody,
      });
      // for localStorage
      const selectedWorkflow = {
        name: runWorkflowState?.workflowType,
        version: runWorkflowState?.workflowVersion,
        workflowInput: runWorkflowState?.workflowInputTemplate,
      };
      setSelectedWorkflow(selectedWorkflow);
    } catch {
      setErrorMessage(INVALID_DATA_MESSAGE);
    }
  };

  const clearWorkflow = function () {
    setErrorMessage("");
    setRunWorkflowState(INITIAL_STATE);
    setSelectedWorkflow(INITIAL_STATE);
    setWorkflowInputTemplatesState("");
  };

  const setWorkflowInputTemplatesState = function (value: string) {
    setRunWorkflowState((prevState) => ({
      ...prevState,
      workflowInputTemplate: value,
    }));
  };

  const setWorkflowTasksToDomainState = function (value: string) {
    setRunWorkflowState((prevState) => ({
      ...prevState,
      taskToDomain: value,
    }));
  };

  const setWorkflowCorrelationIdState = function (value: string) {
    setRunWorkflowState((prevState) => ({
      ...prevState,
      workflowCorrelationId: value,
    }));
  };

  const handleChangeIdempotencyValues = (data: IdempotencyValuesProp) => {
    setRunWorkflowState((prevState) => ({
      ...prevState,
      idempotencyKey: data?.idempotencyKey,
      idempotencyStrategy: data?.idempotencyStrategy,
    }));
  };

  const fillRerunWorkflowFields = function (row: RunWorkflowParamType) {
    // PATCH if the workflow does not exist dont populate
    if (workflowNames.find((name) => name === row.name)) {
      setRunWorkflowState({
        ...memorizedState,
        lastCreatedWorkflowId: runWorkflowState.lastCreatedWorkflowId,
        workflowCorrelationId: row.correlationId,
        workflowVersion: row.version,
        workflowType: row.name,
        workflowVersions: workflowDefByVersions.get("lookups").get(row.name),
        idempotencyKey: row?.idempotencyKey ?? "",
        ...(row.idempotencyStrategy && {
          idempotencyStrategy: row.idempotencyStrategy,
        }),
      });
      try {
        if (row.taskToDomain) {
          setWorkflowTasksToDomainState(
            JSON.stringify(row.taskToDomain, null, 2),
          );
        }
        if (row.input) {
          setWorkflowInputTemplatesState(JSON.stringify(row.input, null, 2));
        }
      } catch (err) {
        logger.error("Could not parse row:", row, err);
      }
    } else {
      logger.warn("Workflow selected does not exist", row);
    }
  };

  useEffect(() => {
    if (latestExecution?.workflowName) {
      setRunWorkflowState((prevState) => ({
        ...prevState,
        workflowVersions:
          workflowDefByVersions
            .get("lookups")
            .get(latestExecution.workflowName) || [],
      }));
    }
  }, [latestExecution, workflowDefByVersions, setRunWorkflowState]);

  const navigate = useNavigate();

  const buildQueryForCode = (): BuildQueryOutput => {
    const {
      workflowInputTemplate,
      taskToDomain,
      workflowType,
      workflowVersion,
      workflowCorrelationId,
      idempotencyKey,
      idempotencyStrategy,
    } = runWorkflowState;

    const input =
      (workflowInputTemplate &&
        tryToJson<Record<string, unknown>>(workflowInputTemplate)) ||
      {};

    const taskToDomainVal =
      (taskToDomain && tryToJson(taskToDomain)) || undefined;

    const queryObject = {
      input,
      taskToDomain: taskToDomainVal,
      name: workflowType || "",
      version: workflowVersion || "",
      correlationId: workflowCorrelationId,
      idempotencyKey: idempotencyKey,
      ...(idempotencyKey &&
        idempotencyStrategy && {
          idempotencyStrategy: idempotencyStrategy,
        }),
    };

    return queryObject;
  };

  return (
    <>
      <Helmet>
        <title>Run Workflow</title>
      </Helmet>
      {showCodeDialog && (
        <RunWorkflowApiSearchModal
          onClose={() => setShowCodeDialog("")}
          buildQueryOutput={buildQueryForCode()}
        />
      )}
      <Box
        style={{
          width: "100%",
          visibility: "visible",
        }}
      >
        <SectionContainer
          header={
            <SectionHeader
              _deprecate_marginTop={0}
              title="Run Workflow"
              actions={
                <>
                  <Button
                    variant="text"
                    onClick={() => navigate(-1)}
                    startIcon={<XCloseIcon />}
                  >
                    Close
                  </Button>
                  <Button
                    id="clear-info-btn"
                    variant="text"
                    onClick={clearWorkflow}
                    startIcon={<ResetIcon />}
                  >
                    Reset
                  </Button>
                  <SplitButton
                    id="run-workflow-btn"
                    startIcon={<PlayIcon />}
                    options={[
                      {
                        label: "Show as code",
                        onClick: () => setShowCodeDialog("active"),
                      },
                    ]}
                    primaryOnClick={runThisWorkflow}
                    disabled={isTrialExpired}
                  >
                    Run workflow
                  </SplitButton>
                </>
              }
            />
          }
        >
          {errorMessage && (
            <Box mb={5}>
              <MuiAlert
                onClose={() => {
                  setLastCreatedWorkflowId("");
                  setErrorMessage("");
                }}
                severity="error"
              >
                {errorMessage}
              </MuiAlert>
            </Box>
          )}
          {runWorkflowState.lastCreatedWorkflowId !== "" && (
            <Box mb={5}>
              <MuiAlert
                id="workflow-created-alert"
                onClose={() => {
                  setLastCreatedWorkflowId("");
                }}
                severity="success"
              >
                Workflow created :&nbsp;
                <NavLink
                  id="workflow-execution-id"
                  path={`/execution/${runWorkflowState.lastCreatedWorkflowId}`}
                >
                  {runWorkflowState.lastCreatedWorkflowId}
                </NavLink>
              </MuiAlert>
            </Box>
          )}
          <Box
            sx={{
              overflowY: "auto",
            }}
          >
            <Grid container sx={{ width: "100%" }} spacing={3}>
              <Grid
                sx={{ width: "100%" }}
                size={{
                  md: 6,
                }}
              >
                <Paper variant="outlined" sx={{ padding: 6, pb: 13 }}>
                  <Grid
                    container
                    sx={{ width: "100%" }}
                    spacing={3}
                    rowSpacing={6}
                  >
                    <Grid size={9}>
                      <ConductorAutoComplete
                        id="workflow-name-dropdown"
                        fullWidth
                        sx={style.dropDown}
                        label="Workflow name"
                        options={workflowNames}
                        onChange={(__, val) => {
                          setWorkflowTypeState(val);
                        }}
                        value={runWorkflowState.workflowType}
                        autoFocus
                        required
                      />
                    </Grid>
                    <Grid size={3}>
                      <ConductorAutoComplete
                        id="workflow-version-dropdown"
                        fullWidth
                        sx={style.dropDown}
                        label="Version"
                        options={runWorkflowState.workflowVersions}
                        onChange={(__, val) => setWorkflowVersionState(val)}
                        value={runWorkflowState.workflowVersion}
                      />
                    </Grid>
                    <Grid size={12}>
                      <ConductorCodeBlockInput
                        label="Input params"
                        defaultLanguage="json"
                        value={runWorkflowState.workflowInputTemplate}
                        onChange={(value) =>
                          setWorkflowInputTemplatesState(value)
                        }
                        options={{
                          scrollBeyondLastLine: false,
                          wrappingStrategy: "advanced",
                          lightbulb: {
                            enabled: editor.ShowLightbulbIconMode.On,
                          },
                          quickSuggestions: true,
                          lineNumbers: "on",
                          wordWrap: "on",
                          glyphMargin: false,
                          folding: false,
                          lineDecorationsWidth: 10,
                          lineNumbersMinChars: 0,
                          renderLineHighlight: "none",
                          hideCursorInOverviewRuler: false,
                          overviewRulerBorder: false,
                          automaticLayout: true, // Important
                        }}
                        autoformat
                      />
                    </Grid>
                    <IdempotencyForm
                      idempotencyValues={{
                        idempotencyKey: runWorkflowState.idempotencyKey,
                        idempotencyStrategy:
                          runWorkflowState.idempotencyStrategy,
                      }}
                      onChange={handleChangeIdempotencyValues}
                    />
                    <Grid size={12}>
                      <ConductorInput
                        id="correlation-id-field"
                        fullWidth
                        label="Correlation id"
                        value={runWorkflowState.workflowCorrelationId}
                        onTextInputChange={(val) =>
                          setWorkflowCorrelationIdState(val)
                        }
                      />
                    </Grid>
                    <Grid size={12}>
                      <ConductorCodeBlockInput
                        label="Tasks to domain mapping"
                        height={80}
                        defaultLanguage="json"
                        value={runWorkflowState.taskToDomain}
                        onChange={(value) =>
                          setWorkflowTasksToDomainState(value)
                        }
                      />
                    </Grid>
                  </Grid>
                </Paper>
              </Grid>
              <Grid
                size={{
                  md: 6,
                }}
              >
                <RunWorkflowHistoryTable
                  fillReRunWfFields={fillRerunWorkflowFields}
                  workflowHistory={workflowHistory}
                  setWorkflowHistory={setWorkflowHistory}
                />
              </Grid>
            </Grid>
          </Box>
        </SectionContainer>
      </Box>
    </>
  );
}
