import { Grid } from "@mui/material";
import { ConductorFlatMapFormBase } from "components/FlatMapForm/ConductorFlatMapForm";
import XCloseIcon from "components/icons/XCloseIcon";
import IconButton from "components/ui/buttons/MuiIconButton";
import { ConductorAutoComplete } from "components/ui/inputs";
import ConductorInput from "components/ui/inputs/ConductorInput";
import MuiTypography from "components/ui/MuiTypography";
import _isEmpty from "lodash/isEmpty";
import _isUndefined from "lodash/isUndefined";
import { AgentSummary } from "pages/agent/types";
import { FocusEvent, useMemo } from "react";
import { useFetch } from "utils/query";
import { Props } from "./common";

export const StartAgentActionForm = ({
  onRemove,
  index,
  payload,
  handleChangeAction,
}: Props) => {
  const { start_agent } = payload;
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
    <Grid
      container
      spacing={4}
      my={2}
      sx={{ width: "100%", position: "relative" }}
    >
      <Grid size={12}>
        <MuiTypography fontWeight={800} fontSize={16}>
          Start Agent
        </MuiTypography>
      </Grid>
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
            placeholder: `\${event.payload.agent_name}`,
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
        <ConductorInput
          fullWidth
          multiline
          minRows={3}
          label="Prompt"
          placeholder={`\${event.payload.prompt}`}
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
        <ConductorInput
          fullWidth
          label="Session ID"
          placeholder={`\${event.payload.session_id}`}
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
        <ConductorInput
          fullWidth
          label="Idempotency key"
          placeholder={`\${event.payload.event_id}`}
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
          placeholder={`\${event.payload.media_url}`}
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
          enableAutocomplete={false}
          autoFocusField={false}
        />
      </Grid>
      <IconButton onClick={onRemove} sx={{ position: "absolute", right: 0 }}>
        <XCloseIcon size={26} />
      </IconButton>
    </Grid>
  );
};
