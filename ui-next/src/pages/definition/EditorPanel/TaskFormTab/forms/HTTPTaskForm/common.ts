import _clone from "lodash/clone";
import _path from "lodash/fp/path";
import _get from "lodash/get";
import { ChangeEvent, useState } from "react";
import { HttpInputParameters } from "types";
import {
  ACCEPT_PATH,
  CONTENT_TYPE_PATH,
  HEADERS_PATH,
  HEDGING_CONFIG_PATH,
  HTTP_REQUEST_BODY,
  HTTP_REQUEST_ENCODE,
  METHOD_PATH,
  POLLING_STRATEGY_PATH,
  SERVICE_PATH,
  URI_PATH,
} from "utils/constants/httpSuggestions";
import { updateField } from "utils/fieldHelpers";
import { tryToJson } from "utils/utils";
import { GrpcTaskFormProps } from "../GRPCTaskForm/types";
import { HttpTaskFormProps } from "./types";

export const useCreateHttpRequestHandlers = ({
  onChange,
  task,
}: HttpTaskFormProps | GrpcTaskFormProps) => {
  const generatePath = (path: any) => {
    if (_path("inputParameters.http_request", task)) {
      return `inputParameters.http_request.${path}`;
    } else {
      return `inputParameters.${path}`;
    }
  };

  const onChangeHttpRequest = (request: string | HttpInputParameters) =>
    onChange(updateField("inputParameters.http_request", request, task));

  const onChangeMethod = (method: string) =>
    onChange(updateField(generatePath(METHOD_PATH), method, task));

  const onChangeHedgingConfig = (hedgingConfig: Record<string, unknown>) => {
    onChange(
      updateField(generatePath(HEDGING_CONFIG_PATH), hedgingConfig, task),
    );
  };

  const onChangeAccept = (accept: string) =>
    onChange(updateField(generatePath(ACCEPT_PATH), accept, task));

  const onChangeContentType = (contentType: string) =>
    onChange(updateField(generatePath(CONTENT_TYPE_PATH), contentType, task));

  const onChangeHeaders = (modHttpHeaders: any) =>
    onChange(updateField(generatePath(HEADERS_PATH), modHttpHeaders, task));

  const onChangePollingStrategy = (pollingStrategy: string) =>
    onChange(
      updateField(generatePath(POLLING_STRATEGY_PATH), pollingStrategy, task),
    );

  const onChangeUri = (uri: string) => {
    onChange(updateField(generatePath(URI_PATH), uri, task));
  };

  const onChangeAsyncComplete = (event: ChangeEvent<HTMLInputElement>) => {
    onChange(updateField("asyncComplete", event.target.checked, task));
  };

  const onChangeOptional = (event: ChangeEvent<HTMLInputElement>) => {
    onChange(updateField("optional", event.target.checked, task));
  };

  const onChangeEncode = (value: boolean) => {
    onChange(updateField(generatePath(HTTP_REQUEST_ENCODE), value, task));
  };

  const onChangeService = (value: string) =>
    onChange(updateField(generatePath(SERVICE_PATH), value, task));

  const [errorInJsonField, setErrorInJsonField] = useState(false);

  const onChangeHttpRequestBody = (maybeEventOrValue: string | any) => {
    const json = tryToJson(maybeEventOrValue);
    if (json != null) {
      onChange(updateField(generatePath(HTTP_REQUEST_BODY), json, task));
      setErrorInJsonField(false);
    } else if (json == null && httpRequestBody != null) {
      setErrorInJsonField(true);
    }
  };

  const onChangeHttpRequestBodyParameter = (maybeEventOrValue: any) => {
    let newValue;
    if (maybeEventOrValue?.nativeEvent) {
      newValue = maybeEventOrValue.target.value;
    } else if (maybeEventOrValue) {
      newValue = maybeEventOrValue;
    }
    onChange(updateField(generatePath(HTTP_REQUEST_BODY), newValue, task));
  };

  const httpHeaders = _clone(_get(task, generatePath(HEADERS_PATH), {}));
  const accept = _clone(_get(task, generatePath(ACCEPT_PATH)));
  const contentType = _clone(_get(task, generatePath(CONTENT_TYPE_PATH)));
  const method = _clone(_get(task, generatePath(METHOD_PATH)));
  const hedgingConfig = _clone(_get(task, generatePath(HEDGING_CONFIG_PATH)));
  const service = _clone(_get(task, generatePath(SERVICE_PATH)));
  const pollingStrategy = _clone(
    _get(task, generatePath(POLLING_STRATEGY_PATH)),
  );

  const uri = _clone(_get(task, generatePath(URI_PATH)));
  const httpRequestBody = _clone(_get(task, generatePath(HTTP_REQUEST_BODY)));

  const HTTP_REQUEST_PATH = _path("inputParameters.http_request", task)
    ? "inputParameters.http_request"
    : "inputParameters";

  const httpRequestEncode = _clone(
    _get(task, generatePath(HTTP_REQUEST_ENCODE)),
  );

  return [
    {
      onChangeHttpRequest,
      onChangeMethod,
      onChangeAccept,
      onChangeService,
      onChangeContentType,
      onChangeHeaders,
      onChangePollingStrategy,
      onChangeUri,
      onChangeAsyncComplete,
      onChangeOptional,
      onChangeHttpRequestBody,
      onChangeEncode,
      generatePath,
      onChangeHttpRequestBodyParameter,
      onChangeHedgingConfig,
    },
    {
      httpHeaders,
      accept,
      contentType,
      method,
      pollingStrategy,
      uri,
      httpRequestBody,
      HTTP_REQUEST_PATH,
      httpRequestEncode,
      errorInJsonField,
      hedgingConfig,
      service,
    },
  ] as const;
};
