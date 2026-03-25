import { Box, MenuItem } from "@mui/material";
import ConductorSelect from "components/v1/ConductorSelect";
import cronstrue from "cronstrue";
import { CRON_COLORS_BY_POSITION } from "../constants";
import { cronTemplateSamples } from "./cronTemplateSamples";

type CronTemplateSelectorProps = {
  selectedTemplate: string;
  onSelectTemplate: (template: string) => void;
};

export function CronTemplateSelector({
  selectedTemplate,
  onSelectTemplate,
}: CronTemplateSelectorProps) {
  return (
    <ConductorSelect
      fullWidth
      label="Choose a template to get started"
      SelectProps={{
        displayEmpty: true,
      }}
      onChange={(e) => onSelectTemplate(e.target.value)}
      value={selectedTemplate}
      sx={{
        ".MuiInputBase-root": {
          ".MuiSelect-select": {
            minHeight: "2.7em",
          },
        },
      }}
    >
      {cronTemplateSamples.map((cs) => (
        <MenuItem
          key={`key-item-${cs}`}
          value={cs}
          sx={{
            borderBottom: "1px solid rgba(0,0,0,.25)",
          }}
        >
          <Box
            sx={{
              display: "column",
              alignItems: "center",
            }}
          >
            <Box
              sx={{
                paddingRight: 2,
                fontWeight: "bold",
                fontSize: "1rem",
                display: "flex",
              }}
            >
              {cs.split(" ").map((cronExpressionFragment) => (
                <Box
                  key={`key-item-${cs}-${cronExpressionFragment}`}
                  sx={{
                    color:
                      selectedTemplate === cs
                        ? CRON_COLORS_BY_POSITION[
                            cs.split(" ").indexOf(cronExpressionFragment)
                          ]
                        : "gray.800",
                    paddingRight: 2,
                  }}
                >
                  {cronExpressionFragment}
                </Box>
              ))}
            </Box>
            <Box
              sx={{
                overflow: "hidden",
                whiteSpace: "pre-wrap",
                textOverflow: "ellipsis",
                opacity: 0.7,
              }}
            >
              {cronstrue.toString(cs)}
            </Box>
          </Box>
        </MenuItem>
      ))}
    </ConductorSelect>
  );
}
