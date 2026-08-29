import { FormControlLabel, Grid, Switch, Typography } from "@mui/material";
import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import RadioButtonGroup from "components/ui/inputs/RadioButtonGroup";
import { useMemo, useState } from "react";
import { useSecretNames } from "utils/query";
import {
  AGENT_AUTH_METHODS,
  AgentAuthMethod,
  allAuthKeys,
  detectAuthMethod,
  secretReference,
} from "./agentAuthMethods";

type Props = {
  runtime: string;
  credentials: Record<string, unknown> | undefined;
  /** Replaces the whole credentials map, so switching method cannot leave stale keys behind. */
  onCredentialsChange: (credentials: Record<string, string>) => void;
  useCallerIdentity: boolean;
  onUseCallerIdentityChange: (value: boolean) => void;
};

/**
 * Guided credential entry for a hosted agent.
 *
 * The user picks how to authenticate and, if they keep the credential in Conductor, which secret it
 * is — and the `${workflow.secrets.…}` references are written for them. Conductor substitutes those
 * before the task runs, so this is what the agent actually authenticates with; the generated values
 * stay visible and editable rather than being hidden behind the picker.
 */
export default function AgentCredentialsSection({
  runtime,
  credentials,
  onCredentialsChange,
  useCallerIdentity,
  onUseCallerIdentityChange,
}: Props) {
  const methods = AGENT_AUTH_METHODS[runtime] ?? [];
  const secretNames = useSecretNames();
  const detected = useMemo(
    () => detectAuthMethod(runtime, credentials),
    [runtime, credentials],
  );
  // The chosen method has to outlive its fields being empty: selecting one clears the others, and
  // detection alone would immediately snap back to "the server's own identity" mid-edit.
  // Tagged with the runtime it was made under, so switching runtime discards it during render
  // rather than in an effect that would paint the wrong provider's fields for a frame.
  const [chosen, setChosen] = useState<
    { runtime: string; id: string } | undefined
  >(undefined);
  const chosenId = chosen?.runtime === runtime ? chosen.id : undefined;

  const method =
    methods.find((candidate) => candidate.id === chosenId) ?? detected;

  if (methods.length === 0 || !method) return null;

  const asStrings = (): Record<string, string> => {
    const out: Record<string, string> = {};
    Object.entries(credentials ?? {}).forEach(([key, value]) => {
      if (typeof value === "string") out[key] = value;
    });
    return out;
  };

  /** Switching method drops the other methods' keys — they decide which mode the server picks. */
  const selectMethod = (next: AgentAuthMethod) => {
    setChosen({ runtime, id: next.id });
    const kept = asStrings();
    allAuthKeys(runtime).forEach((key) => delete kept[key]);
    next.fields.forEach((field) => {
      kept[field.key] = "";
    });
    onCredentialsChange(kept);
  };

  const setField = (key: string, value: string) => {
    onCredentialsChange({ ...asStrings(), [key]: value });
  };

  /** Points every field of the method at one stored secret. */
  const applyStoredSecret = (secretName: string) => {
    if (!secretName) return;
    const next = asStrings();
    method.fields.forEach((field) => {
      next[field.key] = secretReference(method, field, secretName);
    });
    onCredentialsChange(next);
  };

  const isSingleField = method.fields.length === 1;

  return (
    <Grid container spacing={2} sx={{ width: "100%" }}>
      <Grid size={12}>
        <Typography variant="subtitle2" gutterBottom>
          How should this agent authenticate?
        </Typography>
        <RadioButtonGroup
          name="agentAuthMethod"
          value={method.id}
          items={methods.map((candidate) => ({
            label: candidate.label,
            value: candidate.id,
          }))}
          onChange={(_evt, value) => {
            const next = methods.find((candidate) => candidate.id === value);
            if (next) selectMethod(next);
          }}
        />
        {method.hint && (
          <Typography variant="caption" color="text.secondary" display="block">
            {method.hint}
          </Typography>
        )}
      </Grid>

      {method.fields.length > 0 && (
        <>
          <Grid size={{ xs: 12, md: 6 }}>
            <ConductorAutocompleteVariables
              label="Fill from Conductor secret store"
              value=""
              onChange={(v: unknown) => applyStoredSecret(String(v ?? ""))}
              otherOptions={secretNames}
              placeholder={
                secretNames.length > 0
                  ? "Choose a stored credential"
                  : "No secrets stored yet"
              }
              openOnFocus
            />
            <Typography
              variant="caption"
              color="text.secondary"
              display="block"
            >
              {isSingleField
                ? "Fills the field below with a reference. Conductor resolves it when the task runs, so the value never lands in the workflow definition."
                : `Fills the fields below with references to its ${method.fields
                    .filter((f) => !f.optional)
                    .map((f) => f.key)
                    .join(
                      ", ",
                    )} keys. Conductor resolves them when the task runs, so the values never land in the workflow definition.`}
            </Typography>
          </Grid>

          {method.fields.map((field) => (
            <Grid key={field.key} size={{ xs: 12, md: 6 }}>
              <ConductorAutocompleteVariables
                label={
                  field.optional ? `${field.label} (optional)` : field.label
                }
                value={(credentials?.[field.key] as string) ?? ""}
                onChange={(v: unknown) => setField(field.key, String(v ?? ""))}
                placeholder={field.placeholder}
              />
            </Grid>
          ))}
        </>
      )}

      {runtime === "azure-foundry" && (
        <Grid size={12}>
          <FormControlLabel
            control={
              <Switch
                checked={useCallerIdentity}
                onChange={(e) => onUseCallerIdentityChange(e.target.checked)}
              />
            }
            label="Run as the person who triggered the workflow"
          />
          <Typography variant="caption" color="text.secondary" display="block">
            Exchanges the caller&apos;s Entra ID token for a Foundry-scoped one,
            so the agent sees only what that person can. Needs the cluster wired
            to Entra ID SSO and a service principal above; otherwise the
            credential above is used.
          </Typography>
        </Grid>
      )}
    </Grid>
  );
}
