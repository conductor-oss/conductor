import { Box, Grid } from "@mui/material";
import { useSelector } from "@xstate/react";
import { Text } from "components";
import { selectIsOpenedEdge } from "components/flow/state/selectors";
import { HeadBarSelect } from "components/v1";
import ConductorBreadcrumbs from "components/v1/ConductorBreadcrumbs";
import DoubleArrowLeftIcon from "components/v1/icons/DoubleArrowLeftIcon";
import ButtonLinks from "components/v1/layout/header/ButtonLinks";
import _isString from "lodash/isString";
import _isUndefined from "lodash/isUndefined";
import _uniq from "lodash/uniq";
import { FunctionComponent, useMemo } from "react";
import { useContainerQuery } from "react-container-query";
import { colors } from "theme/tokens/variables";
import { ActorRef } from "xstate";
import { HeadActionButtons } from "../EditorPanel/HeadActionButtons";
import {
  isSaveRequestSelector,
  versionSelector,
  versionsSelector,
} from "../EditorPanel/selectors";
import { ConfirmSaveButtonGroup } from "../confirmSave";
import {
  DefinitionMachineEventTypes,
  WorkflowDefinitionEvents,
} from "../state";

const metaBarQuery = {
  small: {
    maxWidth: 699,
  },
  large: {
    minWidth: 700,
  },
};
/* THIS IS NOT THE METABAR THIS IS ACTUALLY THE HEADER. NOT RENAMING SINCE WE ARE MOVING TO VITE. WE WILL CONFUSE IT */
interface WorkflowMetaBarProps {
  leftPanelExpanded: boolean; // We should get rid of this move it to xstate
  setLeftPanelExpanded: (t: boolean) => void;
  definitionActor: ActorRef<WorkflowDefinitionEvents>;
}

export const WorkflowMetaBar: FunctionComponent<WorkflowMetaBarProps> = ({
  leftPanelExpanded,
  setLeftPanelExpanded,
  definitionActor,
}) => {
  const version = useSelector(definitionActor, versionSelector);
  const versions = useSelector(definitionActor, versionsSelector);
  const isSaveRequest = useSelector(definitionActor, isSaveRequestSelector);
  const flowActor = (definitionActor as any)?.children?.get("flowMachine");
  const isNewWorkflow = useSelector(
    definitionActor,
    (state) => state.context?.isNewWorkflow,
  );

  const openedEdge = useSelector(flowActor, selectIsOpenedEdge);

  const handleChangeVersion = (version: string) =>
    definitionActor.send({
      type: DefinitionMachineEventTypes.CHANGE_VERSION_EVT,
      version,
    });

  const name = useSelector(
    definitionActor,
    (state) => state.context?.workflowChanges?.name,
  );
  const [_containerQueryState, containerRef] = useContainerQuery(metaBarQuery, {
    width: 600,
    height: 800,
  });

  const saveChangesActor = (definitionActor as any).children?.get(
    "saveChangesMachine",
  );

  const maybeConfirmSaveButtonGroup = useMemo(
    () =>
      isSaveRequest && saveChangesActor ? (
        <ConfirmSaveButtonGroup saveChangesActor={saveChangesActor} />
      ) : null,
    [saveChangesActor, isSaveRequest],
  );

  const breadcrumbItems = [
    { label: "Workflow Definitions", to: "/workflowDef" },
    { label: name, to: "" },
  ];

  return (
    <>
      <Box
        id="workflow-meta-bar"
        display="flex"
        padding="8px 20px"
        alignItems="start"
        boxShadow="0 0 8px rgba(0,0,0,.5)"
        sx={{
          color: (theme) =>
            theme.palette?.mode === "dark" ? colors.gray14 : undefined,
          backgroundColor: (theme) =>
            theme.palette?.mode === "dark" ? colors.gray00 : colors.gray14,
          position: "relative",
        }}
      >
        <Grid
          container
          sx={{ width: "100%" }}
          ref={containerRef}
          display="flex"
        >
          <Grid
            size={{
              xs: 12,
              md: 5,
            }}
          >
            <ConductorBreadcrumbs items={breadcrumbItems} />
            <Text
              sx={{
                marginBottom: 0,
                fontSize: "14pt",
                fontWeight: "bold",
                wordBreak: "break-all",
              }}
              id="workflow-name-display"
            >
              {_isString(name) ? name : ""}
            </Text>
          </Grid>
          <Grid
            alignSelf="center"
            size={{
              xs: 12,
              md: 7,
            }}
          >
            <Box
              sx={{
                display: "flex",
                gap: 2,
                flexWrap: "wrap",
                justifyContent: ["flex-start", "flex-end"],
              }}
            >
              <ButtonLinks
                isSideBarOpen={true}
                sx={{ gridArea: "links" }}
                showDropdownOnly={true}
              />
              <HeadBarSelect
                label="Version"
                fullWidth={false}
                value={_isUndefined(version) || version === null ? "" : version}
                onChange={(e) => handleChangeVersion(e)}
                items={[
                  ...(_uniq(versions || [])?.sort((a, b) => a - b) || []),
                  ...(!isNewWorkflow
                    ? [{ label: "Latest version", value: "" }]
                    : []),
                ].map((ver) =>
                  typeof ver === "number"
                    ? { label: `Version ${ver}`, value: ver.toString() }
                    : ver,
                )}
                labelOnEmpty="Latest version"
              />
              {maybeConfirmSaveButtonGroup}
              {!(definitionActor as any).children?.get(
                "saveChangesMachine",
              ) && <HeadActionButtons definitionActor={definitionActor} />}
            </Box>
          </Grid>
        </Grid>
        {leftPanelExpanded && !openedEdge && (
          <Box
            sx={{
              display: "flex",
              justifyContent: "flex-end",
              position: "absolute",
              boxShadow: "4px 4px 10px rgba(89, 89, 89, 0.41)",
              background: "#FFFFFF",
              padding: "3px 8px",
              cursor: "pointer",
              zIndex: 2,
              right: 1,
              bottom: -27,
              "&:hover": {
                background: "#F6FAFD",
              },
            }}
          >
            <Box
              onClick={() => {
                setLeftPanelExpanded(false);
              }}
              sx={{
                fontSize: "12px",
                fontWeight: 400,
                display: "flex",
                alignItems: "center",
              }}
            >
              <DoubleArrowLeftIcon /> Open panel
            </Box>
          </Box>
        )}
      </Box>
    </>
  );
};
