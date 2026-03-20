import { ApiSearchModal } from "components/v1/ApiSearchModal/ApiSearchModal";
import { curlHeaders } from "shared/CodeModal/curlHeader";
import { toCodeT, useParamsToSdk } from "shared/CodeModal/hook";
import { SupportedDisplayTypes } from "shared/CodeModal/types";
import { BuildQueryOutput } from "./ApiSearchModalIntegration";

interface SchedulerApiSearchModalProps {
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
  `${
    window.location.origin
  }/api/scheduler/search/executions?${new URLSearchParams({
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
  const { start, size, sort, freeText, query } = buildQueryOutput;

  return `import { orkesConductorClient, SchedulerClient } from "@io-orkes/conductor-javascript";
    
async function searchSchedule(
  start = ${start},
  size = ${size},
  sort = "${sort}",
  freeText = "${freeText}",
  query = "${query}",
) {
  const client = await orkesConductorClient({
    TOKEN: "${accessToken}",
    serverUrl: "${window.location.origin}/api"
  });
  const executor = new SchedulerClient(client);
  const results = await executor.search(start, size, sort, freeText, query);
      
  return results;
}
  
searchSchedule();
      `;
};

const toCodeMap: toCodeT<BuildQueryOutput> = {
  curl: buildCurlCode,
  javascript: buildJsCode,
};

const SchedulerApiSearchModal = ({
  onClose,
  buildQueryOutput,
}: SchedulerApiSearchModalProps) => {
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

export { SchedulerApiSearchModal };
