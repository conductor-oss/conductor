import { Box, Grid } from "@mui/material";
import { ConductorAutoComplete } from "components/v1";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import { GetSignedJWTAlgorithmType } from "types";
import { ConductorCacheOutput } from "./ConductorCacheOutputForm";
import { ConductorValueInput } from "./ConductorValueInput";
import { Optional } from "./OptionalFieldForm";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import { useGetSetHandler } from "./useGetSetHandler";

const DEFAULT_VALUES_FOR_ARRAY = { object: [] };
const SUBJECT_PATH = "inputParameters.subject";
const ISSUER_PATH = "inputParameters.issuer";
const PRIVATE_KEY_PATH = "inputParameters.privateKey";
const PRIVATE_KEY_ID_PATH = "inputParameters.privateKeyId";
const AUDIENCE_PATH = "inputParameters.audience";
const TTL_PATH = "inputParameters.ttlInSecond";
const SCOPES_PATH = "inputParameters.scopes";
const ALGORITHM_PATH = "inputParameters.algorithm";

const GetSignedJwtForm = (props: TaskFormProps) => {
  const { task, onChange } = props;

  const [subject, handleSubject] = useGetSetHandler(props, SUBJECT_PATH);
  const [issuer, handleIssuer] = useGetSetHandler(props, ISSUER_PATH);
  const [privateKey, handlePrivateKey] = useGetSetHandler(
    props,
    PRIVATE_KEY_PATH,
  );
  const [privateKeyId, handlePrivateKeyId] = useGetSetHandler(
    props,
    PRIVATE_KEY_ID_PATH,
  );
  const [audience, handleAudience] = useGetSetHandler(props, AUDIENCE_PATH);
  const [ttl, handleTtl] = useGetSetHandler(props, TTL_PATH);
  const [scopes, handleScopes] = useGetSetHandler(props, SCOPES_PATH);
  const [algorithm, handleAlgorithm] = useGetSetHandler(props, ALGORITHM_PATH);

  return (
    <Box width="100%">
      <TaskFormSection
        accordionAdditionalProps={{ defaultExpanded: true }}
        title="JWT Details"
      >
        <Grid container sx={{ width: "100%" }} spacing={3}>
          <Grid size={{ xs: 12, md: 6 }}>
            <ConductorAutocompleteVariables
              value={subject}
              label="Subject:"
              onChange={handleSubject}
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
              onChange={handleIssuer}
              value={issuer}
              label="Issuer:"
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
              onChange={handlePrivateKey}
              value={privateKey}
              label="PrivateKey:"
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
              onChange={handlePrivateKeyId}
              value={privateKeyId}
              label="PrivateKeyId:"
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
              onChange={handleAudience}
              value={audience}
              label="Audience:"
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
              onChange={handleTtl}
              coerceTo="integer"
              value={ttl}
              label="TTL (in seconds):"
            />
          </Grid>
          <Grid
            size={{
              xs: 12,
              md: 7,
              sm: 12,
            }}
          >
            <ConductorValueInput
              valueLabel="Scopes:"
              value={scopes}
              onChangeValue={(val) => {
                handleScopes(val);
              }}
              defaultObjectValue={DEFAULT_VALUES_FOR_ARRAY}
            />
          </Grid>
          <Grid
            size={{
              xs: 12,
              md: 5,
              sm: 12,
            }}
          >
            <ConductorAutoComplete
              label="Algorithm:"
              freeSolo={false}
              fullWidth
              options={[GetSignedJWTAlgorithmType.RS256]}
              onChange={(__evt: any, val: string[]) =>
                val !== null && handleAlgorithm(val)
              }
              value={algorithm}
              clearIcon={false}
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
export default GetSignedJwtForm;
