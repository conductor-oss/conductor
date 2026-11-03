import { Box } from "@mui/material";
import MenuItem from "@mui/material/MenuItem";
import ConductorSelect from "components/ui/inputs/ConductorSelect";
import cronstrue from "cronstrue";
import { CRON_COLORS_BY_POSITION, CRON_SAMPLES } from "../constants";

type CronTemplateSelectorProps = {
  /** The sample currently in the field, or "" when the expression was typed by hand. */
  selectedTemplate: string;
  onSelectTemplate: (template: string) => void;
  label?: string;
  id?: string;
};

/**
 * The "choose a template" dropdown. Every cron expression on the page gets one, so it is kept
 * apart from the section it first belonged to.
 */
export function CronTemplateSelector({
  selectedTemplate,
  onSelectTemplate,
  label = "Choose a template to get started",
  id,
}: CronTemplateSelectorProps) {
  return (
    <ConductorSelect
      fullWidth
      id={id}
      label={label}
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
      {CRON_SAMPLES.map((cs, i) => (
        <MenuItem
          key={`key-item-${cs ? cs : i}`}
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
              {cs.split(" ").map((cronExpressionFragment, index) => (
                <Box
                  key={`key-item-${cs}-${index}`}
                  sx={{
                    color:
                      selectedTemplate === cs
                        ? CRON_COLORS_BY_POSITION[index]
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
