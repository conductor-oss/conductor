import { Box, Paper, Typography, Chip } from "@mui/material";
import { ArrowClockwise as RefreshIcon } from "@phosphor-icons/react";
import { DataTable } from "components";
import Button from "components/ui/buttons/MuiButton";
import { useCallback, useMemo, useState } from "react";
import { Helmet } from "react-helmet";
import SectionContainer from "components/ui/layout/SectionContainer";
import SectionHeader from "components/layout/SectionHeader";
import Header from "components/ui/Header";
import { useBatchedTagsData } from "utils/hooks";
import NoDataComponent from "components/ui/NoDataComponent";
import { colors } from "theme/tokens/variables";

interface TagAggregation {
  tag: string;
  workflows: number;
  tasks: number;
  webhooks: number;
  events: number;
  templates: number;
  schedules: number;
  secrets: number;
  prompts: number;
  environments: number;
  integrations: number;
  total: number;
}

export default function TagsDashboard() {
  const [isRefreshing, setIsRefreshing] = useState(false);

  // Use the batched API hook instead of multiple individual calls
  const {
    data: batchedData,
    refetch: refetchBatchedData,
    isLoading: isFetching,
  } = useBatchedTagsData();

  // Resource type to entity type mapping
  const resourceTypeMap: Record<
    string,
    keyof Omit<TagAggregation, "tag" | "total">
  > = {
    WORKFLOW_DEF: "workflows",
    TASK_DEF: "tasks",
    WEBHOOK: "webhooks",
    EVENT_HANDLER: "events",
    USER_FORM_TEMPLATE: "templates",
    WORKFLOW_SCHEDULE: "schedules",
    SECRET_NAME: "secrets",
    PROMPT: "prompts",
    ENV_VARIABLE: "environments",
    INTEGRATION_PROVIDER: "integrations",
  };

  const handleRefresh = useCallback(async () => {
    setIsRefreshing(true);
    try {
      await refetchBatchedData();
    } finally {
      setIsRefreshing(false);
    }
  }, [refetchBatchedData]);

  // Process batched data to create tag aggregations
  const tagAggregations = useMemo((): TagAggregation[] => {
    // Create a map to track tag usage across different entity types
    const tagMap = new Map<string, TagAggregation>();

    // Process the batched data array
    if (Array.isArray(batchedData)) {
      batchedData?.forEach((item) => {
        const { tagKey, tagValue, resourceType, countPerResourceType } = item;
        const tagkeyValue = `${tagKey}:${tagValue}`;

        // Initialize the aggregation if it doesn't exist
        if (!tagMap.has(tagkeyValue)) {
          tagMap.set(tagkeyValue, {
            tag: tagkeyValue,
            workflows: 0,
            tasks: 0,
            webhooks: 0,
            events: 0,
            templates: 0,
            schedules: 0,
            secrets: 0,
            prompts: 0,
            environments: 0,
            integrations: 0,
            total: 0,
          });
        }

        const aggregation = tagMap.get(tagkeyValue)!;
        const entityType = resourceTypeMap[resourceType];

        // If the resource type is mapped, add the count
        if (entityType) {
          aggregation[entityType] += countPerResourceType || 0;
          aggregation.total += countPerResourceType || 0;
        }
      });
    }

    // Convert map to array and sort by total count (descending), then by tag name (ascending)
    return Array.from(tagMap.values()).sort((a, b) => {
      if (b.total !== a.total) {
        return b.total - a.total;
      }
      return a.tag.localeCompare(b.tag);
    });
  }, [batchedData]);

  console.log("tagAggregations", tagAggregations);

  const columns = useMemo(
    () => [
      {
        id: "tag",
        name: "tag",
        label: "Tag",
        grow: 3,
        renderer: (tag: string) => (
          <Chip
            label={tag}
            variant="outlined"
            size="small"
            sx={{
              backgroundColor: "primary.50",
              borderColor: "primary.200",
              color: "primary.700",
              "&:hover": {
                backgroundColor: "primary.100",
              },
            }}
          />
        ),
        tooltip: "Tag name",
      },
      {
        id: "workflows",
        name: "workflows",
        label: "Workflows",
        renderer: (count: number) => (
          <Typography variant="body2" sx={{ textAlign: "right" }}>
            {count}
          </Typography>
        ),
        tooltip: "Number of workflows with this tag",
      },
      {
        id: "tasks",
        name: "tasks",
        label: "Tasks",
        renderer: (count: number) => (
          <Typography variant="body2" sx={{ textAlign: "right" }}>
            {count}
          </Typography>
        ),
        tooltip: "Number of task definitions with this tag",
      },
      {
        id: "webhooks",
        name: "webhooks",
        label: "Webhooks",
        renderer: (count: number) => (
          <Typography variant="body2" sx={{ textAlign: "right" }}>
            {count}
          </Typography>
        ),
        tooltip: "Number of webhooks with this tag",
      },
      {
        id: "events",
        name: "events",
        label: "Events",
        renderer: (count: number) => (
          <Typography variant="body2" sx={{ textAlign: "right" }}>
            {count}
          </Typography>
        ),
        tooltip: "Number of event handlers with this tag",
      },
      {
        id: "templates",
        name: "templates",
        label: "Templates",
        renderer: (count: number) => (
          <Typography variant="body2" sx={{ textAlign: "right" }}>
            {count}
          </Typography>
        ),
        tooltip: "Number of templates with this tag",
      },
      {
        id: "schedules",
        name: "schedules",
        label: "Schedules",
        renderer: (count: number) => (
          <Typography variant="body2" sx={{ textAlign: "right" }}>
            {count}
          </Typography>
        ),
        tooltip: "Number of schedules with this tag",
      },
      {
        id: "secrets",
        name: "secrets",
        label: "Secrets",
        renderer: (count: number) => (
          <Typography variant="body2" sx={{ textAlign: "right" }}>
            {count}
          </Typography>
        ),
        tooltip: "Number of secrets with this tag",
      },
      {
        id: "prompts",
        name: "prompts",
        label: "Prompts",
        renderer: (count: number) => (
          <Typography variant="body2" sx={{ textAlign: "right" }}>
            {count}
          </Typography>
        ),
        tooltip: "Number of prompts with this tag",
      },
      {
        id: "environments",
        name: "environments",
        label: "Environments",
        renderer: (count: number) => (
          <Typography variant="body2" sx={{ textAlign: "right" }}>
            {count}
          </Typography>
        ),
        tooltip: "Number of environment variables with this tag",
      },
      {
        id: "integrations",
        name: "integrations",
        label: "Integrations",
        renderer: (count: number) => (
          <Typography variant="body2" sx={{ textAlign: "right" }}>
            {count}
          </Typography>
        ),
        tooltip: "Number of integrations with this tag",
      },
      {
        id: "total",
        name: "total",
        label: "Total",
        renderer: (count: number) => (
          <Typography
            variant="body2"
            sx={{ textAlign: "right", fontWeight: "medium" }}
          >
            {count}
          </Typography>
        ),
        tooltip: "Total number of definitions with this tag",
      },
    ],
    [],
  );

  return (
    <>
      <Helmet>
        <title>Tags Dashboard</title>
      </Helmet>

      <SectionHeader
        title="Tags overview"
        _deprecate_marginTop={0}
        actions={
          <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
            <Chip
              label={`${tagAggregations.length} tags`}
              variant="outlined"
              size="small"
              sx={{ backgroundColor: "grey.100" }}
            />
            <Button
              variant="contained"
              size="small"
              startIcon={<RefreshIcon />}
              onClick={handleRefresh}
              disabled={isRefreshing}
            >
              Refresh
            </Button>
          </Box>
        }
      />

      <SectionContainer>
        <Paper variant="outlined">
          <Header loading={isFetching} />
          {isFetching ? (
            <Box
              sx={{
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                minHeight: 200,
                color: "text.secondary",
                flexDirection: "column",
                gap: 2,
              }}
            >
              <Typography variant="body1">Loading tags data...</Typography>
              <Box
                sx={{
                  width: 40,
                  height: 40,
                  border: "3px solid #f3f3f3",
                  borderTop: "3px solid #1976d2",
                  borderRadius: "50%",
                  animation: "spin 1s linear infinite",
                  "@keyframes spin": {
                    "0%": { transform: "rotate(0deg)" },
                    "100%": { transform: "rotate(360deg)" },
                  },
                }}
              />
            </Box>
          ) : (
            <DataTable
              localStorageKey="tagsDashboardTable"
              quickSearchEnabled
              quickSearchPlaceholder="Search tags"
              defaultShowColumns={[
                "tag",
                "workflows",
                "tasks",
                "webhooks",
                "events",
                "templates",
                "schedules",
                "secrets",
                "prompts",
                "environments",
                "integrations",
                "total",
              ]}
              keyField="tag"
              data={tagAggregations}
              columns={columns}
              noDataComponent={
                <NoDataComponent
                  title="No tags found"
                  titleBg={colors.warningTag}
                  description="Here you'll see your team's tags across all resources. Tags help you organize and manage your resources more effectively."
                />
              }
            />
          )}
        </Paper>
      </SectionContainer>
    </>
  );
}
