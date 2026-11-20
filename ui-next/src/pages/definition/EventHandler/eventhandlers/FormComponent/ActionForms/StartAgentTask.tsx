import { Grid } from "@mui/material";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import { ConductorAutoComplete } from "components/ui/inputs";
import ConductorInput from "components/ui/inputs/ConductorInput";
import _isEmpty from "lodash/isEmpty";
import _isUndefined from "lodash/isUndefined";
import { AgentSummary } from "pages/agent/types";
import { FocusEvent, useMemo } from "react";
import { useFetch } from "utils/query";
import { Props } from "./common";
import { usePayloadMapProps } from "../payloadFields";
import PayloadFieldInput from "../PayloadFieldInput";

export const StartAgentActionForm = ({
  index,
  payload,
  handleChangeAction,
}: Props) => {
  const { start_agent } = payload;
  const payloadMapProps = usePayloadMapProps();
  const { data: agentDefinitions } = useFetch<AgentSummary[]>("/agent/list");

  const namesAndVersions = useMemo(() => {
    const map = new Map<string, number[]>();
    (agentDefinitions ?? []).forEach(({ name, version }) => {
      const versions = map.get(name) ?? [];
      versions.push(version);
      map.set(name, versions);
    });
    return map;
  }, [agentDefinitions]);

  const options = useMemo(
    () => Array.from(namesAndVersions.keys()).sort(),
    [namesAndVersions],
  );

  const maybeSelectedAgentName = useMemo(
    () => (_isEmpty(start_agent?.name) ? undefined : start_agent?.name),
    [start_agent?.name],
  );

  const availableVersions: string[] = useMemo(() => {
    const versions: number[] =
      namesAndVersions.get(maybeSelectedAgentName) || [];

    return _isUndefined(maybeSelectedAgentName) && !_isEmpty(options)
      ? []
      : versions.map((val) => val.toString()).sort();
  }, [maybeSelectedAgentName, namesAndVersions, options]);

  const mediaText = (start_agent?.media || []).join("\n");

  return (
    <Grid container spacing={4} my={2} sx={{ width: "100%" }}>
      <Grid
        size={{
          xs: 12,
          sm: 12,
          md: 9,
        }}
      >
        <ConductorAutoComplete
          label="Agent name"
          freeSolo
          fullWidth
          value={start_agent?.name}
          options={options}
          onChange={(_, value) =>
            handleChangeAction(index, {
              ...payload,
              start_agent: {
                ...start_agent,
                name: value,
              },
            })
          }
          onBlur={(event: FocusEvent<HTMLInputElement>) => {
            handleChangeAction(index, {
              ...payload,
              start_agent: {
                ...start_agent,
                name: event.target.value,
              },
            });
          }}
          conductorInputProps={{
            placeholder: "e.g. support_triage",
          }}
        />
      </Grid>
      <Grid
        size={{
          xs: 12,
          sm: 12,
          md: 3,
        }}
      >
        <ConductorAutoComplete
          label="Agent version"
          freeSolo
          fullWidth
          value={start_agent?.version}
          options={availableVersions}
          onChange={(_, value) =>
            handleChangeAction(index, {
              ...payload,
              start_agent: {
                ...start_agent,
                version: value,
              },
            })
          }
          onBlur={(event: FocusEvent<HTMLInputElement>) => {
            handleChangeAction(index, {
              ...payload,
              start_agent: {
                ...start_agent,
                version: event.target.value,
              },
            });
          }}
          conductorInputProps={{
            placeholder: "latest",
          }}
        />
      </Grid>
      <Grid size={12}>
        <PayloadFieldInput
          fullWidth
          multiline
          minRows={3}
          label="Prompt"
          placeholder={`Summarize order \${orderId}`}
          value={start_agent?.prompt}
          onTextInputChange={(value) =>
            handleChangeAction(index, {
              ...payload,
              start_agent: {
                ...start_agent,
                prompt: value,
              },
            })
          }
        />
      </Grid>
      <Grid
        size={{
          xs: 12,
          md: 6,
        }}
      >
        <PayloadFieldInput
          fullWidth
          label="Session ID"
          placeholder={`\${sessionId}`}
          value={start_agent?.sessionId}
          onTextInputChange={(value) =>
            handleChangeAction(index, {
              ...payload,
              start_agent: {
                ...start_agent,
                sessionId: value,
              },
            })
          }
        />
      </Grid>
      <Grid
        size={{
          xs: 12,
          md: 6,
        }}
      >
        <PayloadFieldInput
          fullWidth
          label="Idempotency key"
          placeholder={`\${eventId}`}
          value={start_agent?.idempotencyKey}
          onTextInputChange={(value) =>
            handleChangeAction(index, {
              ...payload,
              start_agent: {
                ...start_agent,
                idempotencyKey: value,
              },
            })
          }
        />
      </Grid>
      <Grid size={12}>
        <ConductorInput
          fullWidth
          multiline
          minRows={2}
          label="Media (one URL per line)"
          placeholder={`\${mediaUrl}`}
          value={mediaText}
          onTextInputChange={(value) =>
            handleChangeAction(index, {
              ...payload,
              start_agent: {
                ...start_agent,
                media: value
                  .split("\n")
                  .map((line) => line.trim())
                  .filter((line) => line.length > 0),
              },
            })
          }
        />
      </Grid>
      <Grid size={12}>
        <ConductorFlatMapFormBase
          onChange={(newValues) => {
            handleChangeAction(index, {
              ...payload,
              start_agent: {
                ...start_agent,
                context: newValues,
              },
            });
          }}
          value={{ ...start_agent?.context }}
          title="Context"
          keyColumnLabel="Key"
          valueColumnLabel="Value"
          addItemLabel="Add parameter"
          showFieldTypes
          {...payloadMapProps}
          autoFocusField={false}
        />
      </Grid>
    </Grid>
  );
};
