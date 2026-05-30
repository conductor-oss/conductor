import { Box, Grid } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { UseQueryResult } from "react-query";
import { IntegrationCategory, IntegrationDef } from "types";
import { EMAIL_CONTENT_TYPE_SUGGESTIONS } from "utils/constants/emailContentTypeSuggestions";
import { useIntegrationProviders } from "utils/useIntegrationProviders";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import { useGetSetHandler } from "./useGetSetHandler";

const FROM = "inputParameters.from";
const TO = "inputParameters.to";
const SUBJECT = "inputParameters.subject";
const CONTENT_TYPE = "inputParameters.contentType";
const CONTENT = "inputParameters.content";
const SENDGRID_CONFIGURATION = "inputParameters.sendgridConfiguration";

export const SendgridForm = (props: TaskFormProps) => {
  const { task, onChange } = props;
  const [from, handlerFrom] = useGetSetHandler(props, FROM);

  const [to, handlerTo] = useGetSetHandler(props, TO);

  const [subject, handlerSubject] = useGetSetHandler(props, SUBJECT);

  const [contentType, handlerContentType] = useGetSetHandler(
    props,
    CONTENT_TYPE,
  );

  const [content, handlerContent] = useGetSetHandler(props, CONTENT);

  const [sendgridConfiguration, handlerSendgridConfiguration] =
    useGetSetHandler(props, SENDGRID_CONFIGURATION);

  const { data: sendgridIntegrations }: UseQueryResult<IntegrationDef[]> =
    useIntegrationProviders({
      category: IntegrationCategory.EMAIL, // May need better filters if we add more email type
      activeOnly: false,
    });

  return (
    <Box width="100%">
      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Grid container sx={{ width: "100%" }} spacing={2}>
          <Grid
            size={{
              xs: 12,
              md: 6,
              sm: 12,
            }}
          >
            <ConductorAutocompleteVariables
              onChange={handlerFrom}
              value={from}
              label="From"
              helperText="Sender email must be verified"
            />
          </Grid>
          <Grid
            size={{
              xs: 12,
              md: 6,
              sm: 12,
            }}
          >
            <ConductorAutocompleteVariables
              onChange={handlerTo}
              value={to}
              label="To"
            />
          </Grid>
        </Grid>
        <br />
        <Grid container sx={{ width: "100%" }} spacing={2}>
          <Grid
            size={{
              xs: 24,
              md: 12,
              sm: 24,
            }}
          >
            <ConductorAutocompleteVariables
              onChange={handlerSubject}
              value={subject}
              label="Subject"
            />
          </Grid>
        </Grid>
        <br />
        <Grid container sx={{ width: "100%" }} spacing={2}>
          <Grid
            size={{
              xs: 24,
              md: 12,
              sm: 24,
            }}
          >
            <ConductorAutocompleteVariables
              onChange={handlerContentType}
              value={contentType}
              otherOptions={EMAIL_CONTENT_TYPE_SUGGESTIONS}
              label="Content Type"
            />
          </Grid>
        </Grid>
        <br />
        <Grid container sx={{ width: "100%" }} spacing={2}>
          <Grid
            size={{
              xs: 24,
              md: 12,
              sm: 24,
            }}
          >
            <ConductorAutocompleteVariables
              onChange={handlerContent}
              value={content}
              label="Content"
            />
          </Grid>
        </Grid>
        <br />
        <Grid container sx={{ width: "100%" }} spacing={2}>
          <Grid
            size={{
              xs: 24,
              md: 12,
              sm: 24,
            }}
          >
            <ConductorAutocompleteVariables
              onChange={handlerSendgridConfiguration}
              value={sendgridConfiguration}
              otherOptions={
                sendgridIntegrations?.map((item) => item.name) || []
              }
              label="SendGrid Configuration"
            />
          </Grid>
        </Grid>
      </TaskFormSection>

      <TaskFormSection>
        <Box display="flex" flexDirection="column" gap={3}>
          <ConductorCacheOutput onChange={onChange} taskJson={task} />
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
