import { Box, Button, Grid, Stack } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import React, { ReactElement, useState } from "react";
import { useServiceMethodsDefinition } from "./state/hook";
import { ActorRef } from "xstate";
import { ServiceMethodsMachineEvents } from "./state/types";
import UIModal from "components/ui/dialogs/UIModal";
import { Edit } from "@mui/icons-material";
import ConductorInputNumber from "components/ui/inputs/ConductorInputNumber";
import MuiTypography from "components/ui/MuiTypography";

import XCloseIcon from "components/icons/XCloseIcon";
import SaveIcon from "components/icons/SaveIcon";
import { NotePencil } from "@phosphor-icons/react";

const EditTaskDefConfigModal = ({
  actor,
  hedgingComponent,
}: {
  actor: ActorRef<ServiceMethodsMachineEvents>;
  hedgingComponent: ReactElement;
}) => {
  const [show, setShow] = useState(false);
  const [
    { currentTaskDefinition },
    {
      handleUpdateTaskConfig,
      handleChangeTaskConfig,
      handleResetModifiedTaskConfig,
    },
  ] = useServiceMethodsDefinition(actor);

  const handleShow = (val: boolean) => {
    handleResetModifiedTaskConfig();
    setShow(val);
  };

  return (
    <>
      <Box py={1} width="100%">
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={6}>{hedgingComponent}</Grid>
          <Grid size={6}>
            <Box display="flex" alignItems={"center"}>
              <MuiTypography marginTop="4px" color="#767676" fontWeight={600}>
                Retry settings:
              </MuiTypography>

              <NotePencil
                onClick={() => handleShow(true)}
                size={16}
                cursor={"pointer"}
                style={{ marginLeft: "5px", color: "#767676" }}
              />
            </Box>
            <Box mt={4}>
              <Grid container sx={{ width: "100%" }}>
                <Grid size={12}>
                  <ConductorAutocompleteVariables
                    disabled
                    onChange={() => {}}
                    value={currentTaskDefinition?.retryCount}
                    label={"Retry count"}
                  />
                </Grid>
              </Grid>
            </Box>
          </Grid>
        </Grid>
      </Box>
      <Box py={1} width="100%">
        <Box display="flex" alignItems={"center"}>
          <MuiTypography marginTop="4px" color="#767676" fontWeight={600}>
            Rate limit settings:
          </MuiTypography>
          <NotePencil
            onClick={() => handleShow(true)}
            size={16}
            cursor={"pointer"}
            style={{ marginLeft: "5px", color: "#767676" }}
          />
        </Box>
        <Box mt={4}>
          <Grid container sx={{ width: "100%" }} spacing={3}>
            <Grid size={12}>
              <Grid container sx={{ width: "100%" }} spacing={3}>
                <Grid size={6}>
                  <ConductorAutocompleteVariables
                    disabled
                    onChange={() => {}}
                    value={currentTaskDefinition?.rateLimitPerFrequency}
                    label={"Rate limit per frequency"}
                  />
                </Grid>
                <Grid size={6}>
                  <ConductorAutocompleteVariables
                    disabled
                    onChange={() => {}}
                    value={currentTaskDefinition?.rateLimitFrequencyInSeconds}
                    label={"Frequency seconds"}
                  />
                </Grid>
              </Grid>
            </Grid>
          </Grid>
        </Box>
      </Box>
      <UIModal
        open={show}
        setOpen={handleShow}
        title="Update Retry and Rate limit settings"
        icon={<Edit />}
        description={`Edit retry and rate limit settings for '${currentTaskDefinition?.name}' task`}
        enableCloseButton
      >
        <Box>
          <Grid container sx={{ width: "100%" }} spacing={3}>
            <Grid size={12}>
              <ConductorAutocompleteVariables
                disabled
                onChange={() => {}}
                value={currentTaskDefinition?.name}
                label={"Task name"}
              />
            </Grid>
            {/* rate limit settings */}
            <Grid size={12}>
              <MuiTypography fontWeight={500} fontSize={15}>
                Rate limit settings
              </MuiTypography>
            </Grid>
            <Grid size={12}>
              <Grid container sx={{ width: "100%" }} spacing={3}>
                <Grid size={6}>
                  <ConductorInputNumber
                    fullWidth
                    onChange={(value) =>
                      handleChangeTaskConfig("rateLimitPerFrequency", value)
                    }
                    value={currentTaskDefinition?.rateLimitPerFrequency}
                    label={"Rate limit per frequency"}
                    inputProps={{
                      allowNegative: false,
                    }}
                    tooltip={{
                      title: "Rate limit per frequency",
                      content:
                        "The number of task executions given to workers per frequency window.",
                    }}
                  />
                </Grid>
                <Grid size={6}>
                  <ConductorInputNumber
                    fullWidth
                    onChange={(value) =>
                      handleChangeTaskConfig(
                        "rateLimitFrequencyInSeconds",
                        value,
                      )
                    }
                    value={currentTaskDefinition?.rateLimitFrequencyInSeconds}
                    label={"Frequency seconds"}
                    inputProps={{
                      allowNegative: false,
                    }}
                    tooltip={{
                      title: "Frequency seconds",
                      content:
                        "The duration of the frequency window in seconds.",
                    }}
                  />
                </Grid>
              </Grid>
            </Grid>

            {/* retry settings */}
            <Grid size={12}>
              <MuiTypography fontWeight={500} fontSize={15}>
                Retry settings
              </MuiTypography>
            </Grid>
            <Grid size={6}>
              <ConductorInputNumber
                fullWidth
                onChange={(value) =>
                  handleChangeTaskConfig("retryCount", value)
                }
                value={currentTaskDefinition?.retryCount}
                label={"Retry count"}
                inputProps={{
                  allowNegative: false,
                }}
              />
            </Grid>
          </Grid>
        </Box>
        <Stack
          flexDirection="row"
          gap={2}
          flexWrap="wrap"
          justifyContent={"flex-end"}
        >
          <Button
            color="secondary"
            startIcon={<XCloseIcon />}
            onClick={() => handleShow(false)}
          >
            Cancel
          </Button>
          <Button
            startIcon={<SaveIcon />}
            onClick={() => {
              handleUpdateTaskConfig();
              handleShow(false);
            }}
          >
            Update
          </Button>
        </Stack>
      </UIModal>
    </>
  );
};

export default EditTaskDefConfigModal;
