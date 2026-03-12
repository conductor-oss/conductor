import {
  Cards,
  CloudArrowDown,
  Function,
  Hourglass,
  Person as HumanTaskIcon,
  Pause,
  Repeat,
  RocketLaunch,
  X,
  Diamond,
  GitFork,
  ShieldCheck,
  Globe,
  FileJsIcon,
  HandshakeIcon,
  ClockClockwiseIcon,
  PersonSimpleRunIcon,
  BroadcastIcon,
  RowsIcon,
  FilesIcon,
  FileMagnifyingGlass,
} from "@phosphor-icons/react";

import { TaskType } from "types";
import { ForkJoinIcon } from "./icons/ForkJoinIcon";
import SendgridIcon from "./icons/Sendgrid";
import HttpPollIcon from "./icons/HttpPoll";
import JsonIcon from "./icons/Json";
import WorkerSimpleIcon from "./icons/Worker";
import SimpleWorkerIcon from "./icons/Simple";
import LlmTextComplete from "./icons/LlmTextComplete";
import LlmGenerateEmbeddings from "./icons/LlmGenerateEmbeddings";
import LlmGetEmbeddings from "./icons/LlmGetEmbeddings";
import LlmStoreEmbeddings from "./icons/LlmStoreEmbeddings";
import LlmSearchIndex from "./icons/LlmSearchIndex";
import LlmIndexDocument from "./icons/LlmIndexDocument";
import GetDocument from "./icons/GetDocument";
import LlmIndexText from "./icons/LlmIndexText";
import QueryProcessor from "./icons/QueryProcessor";
import OpsGenie from "./icons/OpsGenie";
import UpdateTaskIcon from "./icons/UpdateTaskIcon";
import UpdateSecretIcon from "./icons/UpdateSecret";
import LlmChatComplete from "./icons/LlmChatComplete";
import { IntegrationIcon } from "components/IntegrationIcon";
import { useMemo } from "react";

const CardIcon = ({ type, integrationType }) => {
  const MCPIntegrationIcon = useMemo(() => {
    return (
      <div
        style={{
          paddingRight: "10px",
          display: "flex",
          alignItems: "flex-start",
        }}
      >
        <IntegrationIcon integrationName={integrationType} size={24} />
      </div>
    );
  }, [integrationType]);
  const iconMap = {
    [TaskType.WAIT]: Hourglass,
    [TaskType.HTTP]: Globe,
    [TaskType.KAFKA_PUBLISH]: WorkerSimpleIcon,
    [TaskType.HUMAN]: HumanTaskIcon,
    [TaskType.BUSINESS_RULE]: HandshakeIcon,
    [TaskType.SENDGRID]: SendgridIcon,
    [TaskType.WAIT_FOR_WEBHOOK]: ClockClockwiseIcon,
    [TaskType.HTTP_POLL]: HttpPollIcon,
    [TaskType.DO_WHILE]: Repeat,
    [TaskType.SIMPLE]: SimpleWorkerIcon,
    [TaskType.YIELD]: Pause,
    [TaskType.JDBC]: PersonSimpleRunIcon,
    [TaskType.EVENT]: BroadcastIcon,
    [TaskType.JOIN]: GitFork,
    [TaskType.FORK_JOIN]: ForkJoinIcon,
    [TaskType.FORK_JOIN_DYNAMIC]: ForkJoinIcon,
    [TaskType.DYNAMIC]: Cards,
    [TaskType.INLINE]: FileJsIcon,
    [TaskType.SWITCH]: Diamond,
    [TaskType.JSON_JQ_TRANSFORM]: JsonIcon,
    [TaskType.TERMINATE]: X,
    [TaskType.SET_VARIABLE]: Function,
    [TaskType.TERMINATE_WORKFLOW]: X,
    [TaskType.SUB_WORKFLOW]: ForkJoinIcon,
    [TaskType.START_WORKFLOW]: RocketLaunch,
    [TaskType.LLM_TEXT_COMPLETE]: LlmTextComplete,
    [TaskType.LLM_GENERATE_EMBEDDINGS]: LlmGenerateEmbeddings,
    [TaskType.LLM_GET_EMBEDDINGS]: LlmGetEmbeddings,
    [TaskType.LLM_STORE_EMBEDDINGS]: LlmStoreEmbeddings,
    [TaskType.LLM_INDEX_DOCUMENT]: LlmIndexDocument,
    [TaskType.LLM_SEARCH_INDEX]: LlmSearchIndex,
    [TaskType.LLM_INDEX_TEXT]: LlmIndexText,
    [TaskType.UPDATE_SECRET]: UpdateSecretIcon,
    [TaskType.GET_DOCUMENT]: GetDocument,
    [TaskType.QUERY_PROCESSOR]: QueryProcessor,
    [TaskType.OPS_GENIE]: OpsGenie,
    [TaskType.GET_SIGNED_JWT]: ShieldCheck,
    [TaskType.UPDATE_TASK]: UpdateTaskIcon,
    [TaskType.GET_WORKFLOW]: CloudArrowDown,
    [TaskType.LLM_CHAT_COMPLETE]: LlmChatComplete,
    [TaskType.GRPC]: Globe,
    [TaskType.CHUNK_TEXT]: RowsIcon,
    [TaskType.LIST_FILES]: FilesIcon,
    [TaskType.PARSE_DOCUMENT]: FileMagnifyingGlass,
  };

  const IconComponent = iconMap[type];
  if (type === TaskType.MCP) {
    return MCPIntegrationIcon;
  }

  return IconComponent ? (
    <div
      style={{
        paddingRight: "10px",
      }}
    >
      <IconComponent size={24} />
    </div>
  ) : null;
};

export default CardIcon;
