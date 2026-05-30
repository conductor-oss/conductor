import { ApiSearchModal } from "components/ApiSearchModal";
import { curlHeaders } from "shared/CodeModal/curlHeader";
import { toCodeT, useParamsToSdk } from "shared/CodeModal/hook";
import { SupportedDisplayTypes } from "shared/CodeModal/types";
import { BuildQueryOutput } from "../ApiSearchModalIntegration";

interface TaskApiSearchModalProps {
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
  `${window.location.origin}/api/tasks/search?${new URLSearchParams({
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

const toCodeMap: toCodeT<BuildQueryOutput> = {
  curl: buildCurlCode,
};

export const TaskApiSearchModal = ({
  onClose,
  buildQueryOutput,
}: TaskApiSearchModalProps) => {
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
