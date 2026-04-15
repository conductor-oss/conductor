import { TaskType } from "types";
import {
  Cards,
  CloudArrowDown,
  Function,
  Hourglass,
  Person as HumanTaskIcon,
  Pause,
  Repeat,
  RocketLaunch,
  ShieldCheck,
  X,
  Diamond,
  GitFork,
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
import SendgridIcon from "../shapes/TaskCard/icons/Sendgrid";

import HttpPollIcon from "../shapes/TaskCard/icons/HttpPoll";
import JsonIcon from "../shapes/TaskCard/icons/Json";

import WorkerSimpleIcon from "../shapes/TaskCard/icons/Worker";
import SimpleWorkerIcon from "../shapes/TaskCard/icons/Simple";
import LlmTextComplete from "../shapes/TaskCard/icons/LlmTextComplete";
import LlmGenerateEmbeddings from "../shapes/TaskCard/icons/LlmGenerateEmbeddings";
import LlmGetEmbeddings from "../shapes/TaskCard/icons/LlmGetEmbeddings";
import LlmStoreEmbeddings from "../shapes/TaskCard/icons/LlmStoreEmbeddings";
import LlmSearchIndex from "../shapes/TaskCard/icons/LlmSearchIndex";
import LlmIndexDocument from "../shapes/TaskCard/icons/LlmIndexDocument";
import GetDocument from "../shapes/TaskCard/icons/GetDocument";
import LlmIndexText from "../shapes/TaskCard/icons/LlmIndexText";
import QueryProcessor from "../shapes/TaskCard/icons/QueryProcessor";
import OpsGenie from "../shapes/TaskCard/icons/OpsGenie";
import UpdateTaskIcon from "../shapes/TaskCard/icons/UpdateTaskIcon";
import UpdateSecretIcon from "../shapes/TaskCard/icons/UpdateSecret";
import LlmChatComplete from "../shapes/TaskCard/icons/LlmChatComplete";

import { FormTaskType } from "types";
import React from "react";
import MCPIcon from "../shapes/TaskCard/icons/MCPIcon";
import { ForkJoinIcon } from "../shapes/TaskCard/icons/ForkJoinIcon";

export const iconForTaskTypeMap = {
  [TaskType.WAIT]: Hourglass,
  [TaskType.HTTP]: Globe,
  [TaskType.KAFKA_PUBLISH]: WorkerSimpleIcon, // This one is not really used
  [TaskType.HUMAN]: HumanTaskIcon,
  [TaskType.BUSINESS_RULE]: HandshakeIcon,
  [TaskType.SENDGRID]: SendgridIcon,
  [TaskType.WAIT_FOR_WEBHOOK]: ClockClockwiseIcon,
  [TaskType.HTTP_POLL]: HttpPollIcon,
  [TaskType.DO_WHILE]: Repeat,
  [TaskType.SIMPLE]: SimpleWorkerIcon,
  [TaskType.YIELD]: Pause,
  [TaskType.JDBC]: PersonSimpleRunIcon, // Would be great if it had a good icon
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
  [TaskType.MCP]: MCPIcon,
  [TaskType.CHUNK_TEXT]: RowsIcon,
  [TaskType.LIST_FILES]: FilesIcon,
  [TaskType.PARSE_DOCUMENT]: FileMagnifyingGlass,
} satisfies Record<FormTaskType, React.FC>;
