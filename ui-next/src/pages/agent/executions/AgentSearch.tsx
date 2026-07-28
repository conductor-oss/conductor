import { Box, FormControlLabel, Switch } from "@mui/material";
import MuiTypography from "components/ui/MuiTypography";
import PlayIcon from "components/icons/PlayIcon";
import _isEqual from "lodash/isEqual";
import { Helmet } from "react-helmet";
import { useQueryState } from "react-router-use-location-state";
import SectionContainer from "components/ui/layout/SectionContainer";
import SectionHeader from "components/layout/SectionHeader";
import SectionHeaderActions from "components/ui/layout/SectionHeaderActions";
import { colors } from "theme/tokens/variables";
import { TaskExecutionResult } from "types/TaskExecution";
import { DoSearchProps } from "types/WorkflowExecution";
import { RUN_AGENT_URL } from "utils/constants/route";
import { pluralizeResults } from "utils/helpers";
import { usePushHistory } from "utils/hooks/usePushHistory";
import AdvancedSearch from "./workflowSearchComponents/AdvancedSearch";
import BasicSearch from "./workflowSearchComponents/BasicSearch";

const SwitchComponent = ({
  asQuery,
  setAsQuery,
}: {
  asQuery: boolean;
  setAsQuery: (value: boolean) => void;
}) => (
  <Box
    sx={{
      display: "flex",
      justifyContent: "flex-end",
      padding: "10px 24px 0 24px",
    }}
  >
    <FormControlLabel
      sx={{
        marginRight: 0,
        "& .MuiTypography-root": {
          fontSize: "12px",
          color: colors.sidebarGreyDark,
        },
      }}
      checked={asQuery}
      control={<Switch color="primary" onChange={() => setAsQuery(!asQuery)} />}
      label="SQL format"
    />
  </Box>
);

export default function AgentPanel() {
  const [asQuery, setAsQuery] = useQueryState("asQuery", false);

  const doSearch = ({
    queryFT,
    buildQuery,
    setQueryFT,
    refetch,
    setPage,
    setRecentTaskSearch,
  }: DoSearchProps) => {
    setPage(1);
    const oldQueryFT = queryFT;
    const newQueryFT = buildQuery();
    setQueryFT(newQueryFT);

    if (_isEqual(oldQueryFT, newQueryFT)) {
      refetch();
    }
    setRecentTaskSearch?.();
  };

  const pushHistory = usePushHistory();

  const getTableTitle = (resultObj: TaskExecutionResult) => {
    const { results, totalHits } = resultObj;
    return (
      <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
        <MuiTypography fontWeight={400} fontSize={14}>
          {pluralizeResults(results.length)}
        </MuiTypography>
        <MuiTypography color={colors.greyText} fontSize={12}>
          of {totalHits}
        </MuiTypography>
      </Box>
    );
  };

  const sharedSearchProps = {
    doSearch,
    SwitchComponent: (
      <SwitchComponent asQuery={asQuery} setAsQuery={setAsQuery} />
    ),
    getTableTitle,
  };

  return (
    <>
      <Helmet>
        <title>Agent Executions</title>
      </Helmet>
      <SectionHeader
        _deprecate_marginTop={0}
        title="Agent Executions"
        actions={
          <SectionHeaderActions
            buttons={[
              {
                label: "Run agent",
                color: "secondary",
                onClick: () => pushHistory(RUN_AGENT_URL),
                startIcon: <PlayIcon />,
              },
            ]}
          />
        }
      />
      <SectionContainer>
        {asQuery ? (
          <AdvancedSearch {...sharedSearchProps} />
        ) : (
          <BasicSearch {...sharedSearchProps} />
        )}
      </SectionContainer>
    </>
  );
}
