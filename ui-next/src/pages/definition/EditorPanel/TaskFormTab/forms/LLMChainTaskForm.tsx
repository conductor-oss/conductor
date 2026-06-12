import { Grid, Box } from "@mui/material";
import JSONField from "./JSONField";
import Input from "components/ui/inputs/Input";
import TaskFormSection from "./TaskFormSection";
import { TaskFormProps } from "./types";
import Dropdown from "components/ui/inputs/Dropdown";
import MuiButton from "components/ui/buttons/MuiButton";
import { Link } from "react-router";

const LLMChainTaskForm = ({ task, onChange }: TaskFormProps) => (
  <Box padding={1} width="100%">
    <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
      <Grid container spacing={2} sx={{ width: "100%" }}>
        <Grid size={6}>
          <JSONField
            path="inputParameters.prompt_name"
            onChange={onChange}
            taskJson={task}
          >
            <Dropdown
              label="Prompt:"
              options={[
                "Generate Movie Synopsis",
                "Book Bus Ticket",
                "Create Story",
              ]}
            />
          </JSONField>
        </Grid>
        <Grid size={6}>
          <Box sx={{ color: "#767676", fontWeight: 400 }}>
            Prompt description:
          </Box>
          <Box sx={{ display: "flex" }}>
            <Box sx={{ width: "80%" }}>
              This prompt generates movie synopsis, pulled from a text, pdf, URL
              or database.View more
            </Box>
            <MuiButton>Test</MuiButton>
          </Box>
        </Grid>
        <Grid size={6}>
          <JSONField
            path="inputParameters.model_endpoint"
            onChange={onChange}
            taskJson={task}
          >
            <Dropdown
              label="Modal enpoint:"
              required
              options={["Huggy Face + Llama 2", "Model 2", "Charm jet 4"]}
            />
          </JSONField>
        </Grid>
      </Grid>
    </TaskFormSection>
    <TaskFormSection accordionAdditionalProps={{ defaultExpanded: true }}>
      <Grid container spacing={2} sx={{ width: "100%", paddingTop: "15px" }}>
        <Grid size={6}>
          <Box
            sx={{
              border: "1px solid grey",
              borderRadius: "6px",
              padding: "10px",
            }}
          >
            <Input
              label="#1 Prompt variables:"
              value={"{sales_data}"}
              disabled
              fullWidth
            />

            <JSONField
              path="inputParameters.prompt_variable_1"
              onChange={onChange}
              taskJson={task}
            >
              <Input label="#1 Prompt variable value:" fullWidth />
            </JSONField>
            <Box sx={{ color: "#767676", fontWeight: 400, marginTop: "15px" }}>
              #1 Prompt variable description:
            </Box>
            <Box>
              This prompt generates movie synopsis, pulled from a text, pdf, URL
              or database.<Link to="">View more</Link>
            </Box>
          </Box>
        </Grid>
        <Grid size={6}>
          <Box
            sx={{
              border: "1px solid grey",
              borderRadius: "6px",
              padding: "10px",
            }}
          >
            <Input
              label="#2 Prompt variables:"
              value={"{sales_data}"}
              disabled
              fullWidth
            />
            <JSONField
              path="inputParameters.prompt_variable_2:"
              onChange={onChange}
              taskJson={task}
            >
              <Input label="#2 Prompt variable value:" fullWidth />
            </JSONField>
            <Box sx={{ color: "#767676", fontWeight: 400, marginTop: "15px" }}>
              #2 Prompt variable description:
            </Box>
            <Box>
              This prompt generates movie synopsis, pulled from a text, pdf, URL
              or database.<Link to="">View more</Link>
            </Box>
          </Box>
        </Grid>
      </Grid>
    </TaskFormSection>
  </Box>
);
export default LLMChainTaskForm;
