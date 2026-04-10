import { ApiSearchModal } from "components/ApiSearchModal";
import { toCodeT, useParamsToSdk } from "shared/CodeModal/hook";
import { SupportedDisplayTypes } from "shared/CodeModal/types";
import { curlHeaders } from "shared/CodeModal/curlHeader";

export type BuildQueryOutput = {
  query: string;
  freeText: string;
  start: number;
  size: number;
  sort: string;
};

interface ApiSearchModalIntegrationProps {
  buildQueryOutput: BuildQueryOutput;
  onClose: () => void;
}

const buildEndpoint = ({
  start,
  size,
  sort,
  freeText,
  query,
}: BuildQueryOutput) =>
  `${window.location.origin}/api/workflow/search?${new URLSearchParams({
    start: String(start),
    size: String(size),
    sort,
    freeText,
    query,
  }).toString()}`;

const buildCurlCode = (
  buildQueryOutput: BuildQueryOutput,
  accessToken: string,
) => {
  const endpoint = buildEndpoint(buildQueryOutput);

  const headers = curlHeaders(accessToken);

  const curlCommand = `curl '${endpoint}' \\${Object.entries(headers)
    .map(([key, value]) => `\n-H '${key}: ${value}' \\`)
    .join("")}\n--compressed`;

  return curlCommand;
};

const buildJsCode = (
  buildQueryOutput: BuildQueryOutput,
  accessToken: string,
) => {
  return `import { orkesConductorClient, WorkflowExecutor } from "@io-orkes/conductor-javascript";
    
async function searchExecution(
  start = ${buildQueryOutput.start},
  size = ${buildQueryOutput.size},
  query = "${buildQueryOutput.query}",
  freeText = "${buildQueryOutput.freeText}",
  sort = "${buildQueryOutput.sort}"
) {
  const client = await orkesConductorClient({
    TOKEN: "${accessToken}",
    serverUrl: "${window.location.origin}/api"
  });
  const executor = new WorkflowExecutor(client);
  const results = await executor.search(start, size, query, freeText, sort );
      
  return results;
  }
  
  searchExecution();
      `;
};

const toCodeMap: toCodeT<BuildQueryOutput> = {
  curl: buildCurlCode,
  javascript: buildJsCode,
};

const ApiSearchModalIntegration = ({
  onClose,
  buildQueryOutput,
}: ApiSearchModalIntegrationProps) => {
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
      languages={Object.keys(toCodeMap) as SupportedDisplayTypes[]}
    />
  );
};

export { ApiSearchModalIntegration };
