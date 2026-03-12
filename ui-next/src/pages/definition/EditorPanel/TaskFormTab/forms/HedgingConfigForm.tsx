import { Box } from "@mui/material";
import MuiTypography from "components/MuiTypography";
import { ConductorAutocompleteVariables } from "components/v1/FlatMapForm/ConductorAutocompleteVariables";
import ConductorTooltip from "components/conductorTooltip/ConductorTooltip";

interface HedgingConfigFormProp {
  hedgingConfig?: { maxAttempts?: number };
  onChange: (value: any) => void;
}

function HedgingConfigForm({
  hedgingConfig = {},
  onChange,
}: HedgingConfigFormProp) {
  return (
    <Box width="100%">
      <MuiTypography
        marginTop="4px"
        marginBottom={4}
        color="#767676"
        fontWeight={600}
      >
        Hedging config
        <ConductorTooltip
          title="Hedging config"
          content={
            <ul style={{ paddingLeft: 12, paddingTop: 0, marginTop: 0 }}>
              <li>
                When enabled, the system will make parallel requests and take
                the response from the first successful call.
              </li>
              <li>
                Hedging allows for normalizing tail latencies in remote
                services.
              </li>
              <li>
                Please note: Hedging makes parallels requests, so make sure to
                only use for services that are idempotent.
              </li>
            </ul>
          }
          placement="top"
          children={
            <img
              alt="info"
              src="/icons/info-icon.svg"
              style={{ paddingLeft: "3px" }}
            />
          }
        />
      </MuiTypography>
      <ConductorAutocompleteVariables
        id="hedging-max-attempts-field"
        fullWidth
        label="Maximum attempts"
        value={hedgingConfig?.maxAttempts ?? ""}
        onChange={(val) => onChange({ ...hedgingConfig, maxAttempts: val })}
        coerceTo="integer"
      />
    </Box>
  );
}

export default HedgingConfigForm;
