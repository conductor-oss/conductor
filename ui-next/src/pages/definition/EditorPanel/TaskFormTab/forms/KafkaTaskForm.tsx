import { Grid, Box } from "@mui/material";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";

import { MaybeVariable } from "./MaybeVariable";
import { useGetSetHandler } from "./useGetSetHandler";
import { TaskType } from "types";
import { Optional } from "./OptionalFieldForm";
import { ConductorFlatMapForm } from "components/FlatMapForm/ConductorFlatMapForm";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import { ConductorKeyValueInput } from "components/FlatMapForm/ConductorKeyValueInput";

const KAFKA_REQUEST = "inputParameters.kafka_request";
const TOPIC_PATH = `${KAFKA_REQUEST}.topic`;
const TOPIC_VALUE = `${KAFKA_REQUEST}.value`;
const BOOTSTRAP_SERVER_PATH = `${KAFKA_REQUEST}.bootStrapServers`;
const HEADERS_PATH = `${KAFKA_REQUEST}.headers`;
const KEY_PATH = `${KAFKA_REQUEST}.key`;
const KEY_SERIALIZER_PATH = `${KAFKA_REQUEST}.keySerializer`;

export const KafkaTaskForm = (props: TaskFormProps) => {
  const { task, onChange } = props;
  const [kafkaRequest, handleKafkaRequest] = useGetSetHandler(
    props,
    KAFKA_REQUEST,
  );
  const [topic, handleTopicChange] = useGetSetHandler(props, TOPIC_PATH);
  const [topicValue, handleTopicValue] = useGetSetHandler(props, TOPIC_VALUE);
  const [bootstrapServer, handlerBootstrapServerPath] = useGetSetHandler(
    props,
    BOOTSTRAP_SERVER_PATH,
  );
  const [headers, handlerHeadersPath] = useGetSetHandler(props, HEADERS_PATH);
  const [key, handlerKey] = useGetSetHandler(props, KEY_PATH);
  const [keySerializer, handlerKeySerializer] = useGetSetHandler(
    props,
    KEY_SERIALIZER_PATH,
  );

  return (
    <Box width="100%">
      <MaybeVariable
        value={kafkaRequest}
        onChange={handleKafkaRequest}
        taskType={TaskType.KAFKA_PUBLISH}
        path={KAFKA_REQUEST}
      >
        <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
          <Grid container sx={{ width: "100%" }} spacing={2}>
            <ConductorKeyValueInput
              showFieldTypes={true}
              mKey={topic}
              hideValue={false}
              existingKeys={[""]}
              onDeleteItem={() => {}}
              value={topicValue}
              onChangeKey={(newKey) => {
                handleTopicChange(newKey);
              }}
              onChangeValue={(newValue: any) => {
                handleTopicValue(newValue);
              }}
              hideButtons={true}
              keyColumnLabel={"Topic:"}
              valueColumnLabel={"Value:"}
              enableAutocomplete={true}
            />
            <Grid size={12}>
              <ConductorAutocompleteVariables
                onChange={handlerBootstrapServerPath}
                value={bootstrapServer}
                label="Server:"
              />
            </Grid>
          </Grid>
        </TaskFormSection>

        <TaskFormSection title="Headers:">
          <Grid container sx={{ width: "100%" }} spacing={1}>
            <Grid size={12}>
              <ConductorFlatMapForm
                keyColumnLabel="Key"
                valueColumnLabel="Value"
                addItemLabel="Add header"
                onChange={handlerHeadersPath}
                value={headers}
                taskType={TaskType.KAFKA_PUBLISH}
                path={HEADERS_PATH}
              />
            </Grid>
          </Grid>
        </TaskFormSection>
        <TaskFormSection title="">
          <Grid container sx={{ width: "100%" }} spacing={2}>
            <Grid sx={{ mb: 6 }} size={12}>
              <ConductorAutocompleteVariables
                onChange={handlerKey}
                value={key}
                label="Key:"
              />
            </Grid>
            <Grid size={12}>
              <Box>
                <ConductorAutocompleteVariables
                  onChange={handlerKeySerializer}
                  value={keySerializer}
                  otherOptions={[
                    "org.apache.kafka.common.serialization.IntegerSerializer",
                    "Integer",
                    "String",
                  ]}
                  label="Key serializer:"
                />
              </Box>
            </Grid>
          </Grid>
        </TaskFormSection>
      </MaybeVariable>

      <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
        <Box mt={3}>
          <Optional onChange={onChange} taskJson={task} />
        </Box>
      </TaskFormSection>
    </Box>
  );
};
