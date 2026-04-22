import { ApiSearchModal } from "components/ApiSearchModal";
import { curlHeaders } from "shared/CodeModal/curlHeader";
import { toCodeT, useParamsToSdk } from "shared/CodeModal/hook";
import { SupportedDisplayTypes } from "shared/CodeModal/types";
import { IdempotencyStrategyEnum } from "./types";

export type BuildQueryOutput = {
  input?: Record<string, unknown>;
  taskToDomain?: object;
  name: string;
  version: string | null;
  correlationId: string;
  idempotencyKey?: string;
  idempotencyStrategy?: IdempotencyStrategyEnum;
};

interface RunWorkflowApiSearchModalProps {
  buildQueryOutput: BuildQueryOutput;
  onClose: () => void;
}

const buildCurlCode = (
  buildQueryOutput: BuildQueryOutput,
  accessToken: string,
) => {
  const {
    correlationId,
    name,
    version,
    input,
    taskToDomain,
    idempotencyKey,
    idempotencyStrategy,
  } = buildQueryOutput;

  const headers = {
    ...curlHeaders(accessToken),
    "Content-Type": "application/json",
  };

  const dataRawJSON = {
    name: name,
    version: version,
    input,
    correlationId: correlationId,
    idempotencyKey: idempotencyKey,
    ...(idempotencyStrategy && { idempotencyStrategy: idempotencyStrategy }),
    ...(taskToDomain && { taskToDomain: taskToDomain }),
  };

  const curlCommand = `curl '${
    window.location.origin
  }/api/workflow' \\${Object.entries(headers)
    .map(([key, value]) => `\n-H '${key}: ${value}' \\`)
    .join("")}\n--data-raw '${JSON.stringify(dataRawJSON)}'`;

  return curlCommand;
};

const buildJsCode = (
  buildQueryOutput: BuildQueryOutput,
  accessToken: string,
) => {
  const {
    correlationId,
    name,
    version,
    input,
    taskToDomain,
    idempotencyKey,
    idempotencyStrategy,
  } = buildQueryOutput;

  return `import { orkesConductorClient, WorkflowExecutor } from "@io-orkes/conductor-javascript";
    
async function runWorkflow() {
  const client = await orkesConductorClient({
    TOKEN: "${accessToken}",
    serverUrl: "${window.location.origin}/api"
  });
  const executor = new WorkflowExecutor(client);

  const data = ${`{
    name: "${name}",
    version: "${version}",
    input: ${JSON.stringify(input)},
    correlationId: "${correlationId}",
    idempotencyKey:"${idempotencyKey}",
    ${
      idempotencyStrategy ? `idempotencyStrategy:"${idempotencyStrategy}",` : ""
    }
    ${taskToDomain ? `taskToDomain: ${JSON.stringify(taskToDomain)},` : ""}
  };`.replace(/^\s*[\r\n]/gm, "")}

  const result = await executor.startWorkflow(data);
      
  return result;
}
  
runWorkflow();
      `;
};

const toCodeMap: toCodeT<BuildQueryOutput> = {
  curl: buildCurlCode,
  javascript: buildJsCode,
};

const RunWorkflowApiSearchModal = ({
  onClose,
  buildQueryOutput,
}: RunWorkflowApiSearchModalProps) => {
  const { selectedLanguage, setSelectedLanguage, code } =
    useParamsToSdk<BuildQueryOutput>(buildQueryOutput, toCodeMap);

  return (
    <ApiSearchModal
      displayLanguage={selectedLanguage}
      handleClose={onClose}
      code={code}
      onTabChange={(val) => {
        setSelectedLanguage(val);
      }}
      dialogTitle="Run Workflow API"
      dialogHeaderText="Here is the code for the run workflow."
      languages={Object.keys(toCodeMap) as SupportedDisplayTypes[]}
    />
  );
};

export { RunWorkflowApiSearchModal };
