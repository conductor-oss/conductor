/**
 * Search helpers for the core OSS search machine.
 *
 * This file handles fuzzy search and result formatting for the core OSS
 * searchable categories: workflows, task definitions, schedulers, and events.
 *
 * Enterprise categories (users, groups, applications, webhooks, integrations,
 * prompts, user forms) are handled by enterprise plugins via searchProviders.
 */

import { MenuItemType } from "components/providers/sidebar/types";
import fastDeepEqual from "fast-deep-equal";
import Fuse from "fuse.js";
import { CommonDef } from "./types";
import { getUniqueWorkflows } from "utils/workflow";
import { WorkflowDef } from "types/WorkflowDef";
import _isEmpty from "lodash/isEmpty";
import _identity from "lodash/identity";
import _prop from "lodash/fp/prop";
import {
  EVENT_HANDLERS_URL,
  SCHEDULER_DEFINITION_URL,
  TASK_DEF_URL,
  WORKFLOW_DEFINITION_URL,
} from "utils/constants/route";

export interface SearchResultExtractorProps {
  taskDefinitions?: CommonDef[];
  workflowDefinitions?: WorkflowDef[];
  scheduler?: string[];
  events?: string[];
  searchTerm: string;
  maxSearchResults?: number;
}

export const searchFunction = (
  targets: CommonDef[] | string[],
  searchTerm: string,
  maxSearchResults?: number,
  keys?: string[],
) => {
  const fuseInstance = new Fuse<string | CommonDef>(targets, {
    includeScore: false,
    threshold: 0.2, // https://www.fusejs.io/api/options.html#threshold
    ...(keys && { keys: keys }),
  });
  const searchResults = fuseInstance.search(searchTerm ?? "");
  const limitedSearchResults = () => {
    if (maxSearchResults) {
      return searchResults && searchResults.length > maxSearchResults
        ? searchResults.slice(0, maxSearchResults)
        : searchResults;
    }
    return searchResults;
  };
  return limitedSearchResults().map(({ item }) => item);
};

const fromName = _prop("name");

const allWhenSearchTerm =
  (searchTerm: string) =>
  (
    items: Array<CommonDef | string> = [],
    config: {
      routePrefix: string;
      viewAllTitle: string;
      toSuffix?: (a: string | CommonDef) => string;
      toLabel?: (a: string | CommonDef) => string;
    },
  ) => {
    const {
      routePrefix,
      viewAllTitle,
      toSuffix = _identity,
      toLabel = _identity,
    } = config;
    if (!_isEmpty(searchTerm)) {
      return [
        { route: routePrefix, title: viewAllTitle },
        ...items.map((item) => {
          return {
            route: `${routePrefix}/${toSuffix(item)}`,
            title: toLabel(item) as string,
          };
        }),
      ];
    }

    return [];
  };

export const searchResultExtractor = ({
  taskDefinitions,
  workflowDefinitions,
  scheduler,
  events,
  searchTerm,
  maxSearchResults,
}: SearchResultExtractorProps) => {
  let taskSearchResult;
  let wfSearchResult;
  let schedulerSearchResult;
  let eventsSearchResult;

  if (taskDefinitions && taskDefinitions.length > 0) {
    taskSearchResult = searchFunction(
      taskDefinitions,
      searchTerm,
      maxSearchResults,
      ["name", "description"],
    );
  }

  if (workflowDefinitions && workflowDefinitions.length > 0) {
    wfSearchResult = searchFunction(
      getUniqueWorkflows(workflowDefinitions),
      searchTerm,
      maxSearchResults,
      ["name", "description"],
    );
  }

  if (scheduler && scheduler.length > 0) {
    schedulerSearchResult = searchFunction(
      scheduler,
      searchTerm,
      maxSearchResults,
    );
  }

  if (events && events.length > 0) {
    eventsSearchResult = searchFunction(events, searchTerm, maxSearchResults);
  }

  const searchResultsToRoutes = allWhenSearchTerm(searchTerm);

  const taskDefinitionsSub = searchResultsToRoutes(taskSearchResult, {
    routePrefix: TASK_DEF_URL.BASE,
    viewAllTitle: "View all task definitions",
    toSuffix: fromName,
    toLabel: fromName,
  });

  const workflowDefinitionsSub = searchResultsToRoutes(wfSearchResult, {
    routePrefix: WORKFLOW_DEFINITION_URL.BASE,
    viewAllTitle: "View all workflow definitions",
    toSuffix: fromName,
    toLabel: fromName,
  });

  const schedulerSub = searchResultsToRoutes(schedulerSearchResult, {
    routePrefix: SCHEDULER_DEFINITION_URL.BASE,
    viewAllTitle: "View all schedulers",
  });

  const eventsSub = searchResultsToRoutes(eventsSearchResult, {
    routePrefix: EVENT_HANDLERS_URL.BASE,
    viewAllTitle: "View all events",
  });

  const emptyOutput = [
    { title: "Workflows", sub: [], route: WORKFLOW_DEFINITION_URL.BASE },
    { title: "Task Definitions", sub: [], route: TASK_DEF_URL.BASE },
    { title: "Schedules", sub: [], route: SCHEDULER_DEFINITION_URL.BASE },
    { title: "Events", sub: [], route: EVENT_HANDLERS_URL.BASE },
  ];

  const dataOutput = [
    {
      title: "Workflows",
      route: WORKFLOW_DEFINITION_URL.BASE,
      sub: workflowDefinitionsSub ?? [],
    },
    {
      title: "Task Definitions",
      route: TASK_DEF_URL.BASE,
      sub: taskDefinitionsSub ?? [],
    },
    {
      title: "Schedules",
      route: SCHEDULER_DEFINITION_URL.BASE,
      sub: schedulerSub ?? [],
    },
    {
      title: "Events",
      route: EVENT_HANDLERS_URL.BASE,
      sub: eventsSub ?? [],
    },
  ].sort(({ sub: subA }, { sub: subB }) => subB.length - subA.length);

  if (searchTerm === "") {
    return null;
  }

  if (fastDeepEqual(emptyOutput, dataOutput)) {
    return [];
  }

  return dataOutput;
};

export const flattenMenu = (
  menuItems: MenuItemType[],
  parentTitle?: string,
) => {
  const result: { route: string; title: string }[] = [];

  menuItems.forEach(({ title, items, linkTo, hidden }) => {
    if (!hidden) {
      if (items && items.length > 0) {
        result.push(...flattenMenu(items, title));

        return;
      }

      const tempTitle = parentTitle ? `${parentTitle} - ${title}` : title;

      if (linkTo) {
        result.push({ route: linkTo, title: tempTitle });

        return;
      }
    }
  });

  return result;
};
