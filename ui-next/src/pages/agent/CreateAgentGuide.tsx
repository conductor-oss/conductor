import {
  Box,
  Button,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Typography,
} from "@mui/material";
import { Lightning } from "@phosphor-icons/react";
import { Tab, Tabs } from "components";
import SectionHeader from "components/layout/SectionHeader";
import SectionContainer from "components/ui/layout/SectionContainer";
import { Helmet } from "react-helmet";
import { useNavigate, useSearchParams } from "react-router";
import { AGENT_DEFINITION_URL } from "utils/constants/route";
import AgentGuideMarkdown from "./AgentGuideMarkdown";
import {
  AGENT_GUIDE_LANGUAGES,
  DEFAULT_AGENT_GUIDE_FRAMEWORK,
  getAgentGuide,
  getAgentGuideLanguage,
} from "./guides/manifest";

export default function CreateAgentGuide() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const language = getAgentGuideLanguage(searchParams.get("language"));
  const guide = getAgentGuide(language, searchParams.get("framework"));

  const selectGuide = (nextLanguage: string, nextFramework: string) => {
    setSearchParams({ language: nextLanguage, framework: nextFramework });
  };

  return (
    <>
      <Helmet>
        <title>Create an Agent</title>
      </Helmet>
      <SectionHeader
        title="Create an Agent"
        _deprecate_marginTop={0}
        actions={
          <Button
            variant="outlined"
            onClick={() => navigate(AGENT_DEFINITION_URL.BASE)}
          >
            Back to Agents
          </Button>
        }
      />
      <SectionContainer>
        <Paper
          variant="outlined"
          sx={{
            mb: 4,
            overflow: "hidden",
            borderColor: "#dbe2ea",
            borderRadius: 2,
            boxShadow: "0 8px 28px rgba(15, 23, 42, 0.06)",
          }}
        >
          <Box
            sx={{
              px: { xs: 3, md: 5 },
              pt: { xs: 3, md: 4 },
              pb: { xs: 2.5, md: 3 },
              borderBottom: "1px solid",
              borderColor: "#dce6f2",
              background:
                "linear-gradient(120deg, #f7fbff 0%, #eef6ff 55%, #f8faff 100%)",
            }}
          >
            <Stack
              direction={{ xs: "column", sm: "row" }}
              alignItems={{ xs: "flex-start", sm: "center" }}
              justifyContent="space-between"
              gap={2}
            >
              <Box>
                <Stack direction="row" alignItems="center" gap={1} mb={1}>
                  <Lightning size={18} weight="fill" color="#1976d2" />
                  <Typography
                    sx={{
                      color: "primary.main",
                      fontSize: "12px",
                      fontWeight: 700,
                      letterSpacing: "0.08em",
                      textTransform: "uppercase",
                    }}
                  >
                    Agent SDK quickstart
                  </Typography>
                </Stack>
                <Typography
                  component="h2"
                  sx={{
                    color: "#142033",
                    fontSize: { xs: 22, md: 27 },
                    fontWeight: 700,
                    letterSpacing: "-0.025em",
                    lineHeight: 1.25,
                  }}
                >
                  Durable agents in under 60 seconds
                </Typography>
              </Box>
            </Stack>
          </Box>
          <Box
            sx={{
              px: { xs: 1, md: 3 },
              pt: { xs: 4, md: 5 },
              pb: 1,
              borderBottom: 1,
              borderColor: "divider",
              bgcolor: "#fff",
            }}
          >
            <Stack
              component="nav"
              aria-label="Agent quickstart options"
              direction="row"
              alignItems="center"
              gap={{ xs: 1, sm: 2 }}
            >
              <Box
                sx={{
                  flex: { xs: "1 1 auto", sm: "0 1 auto" },
                  width: { xs: "auto", sm: "fit-content" },
                  minWidth: 0,
                  overflow: "hidden",
                }}
              >
                <Tabs
                  value={language.id}
                  variant="scrollable"
                  scrollButtons="auto"
                  aria-label="Agent SDK language"
                  onChange={(_, nextLanguage: string) =>
                    selectGuide(nextLanguage, DEFAULT_AGENT_GUIDE_FRAMEWORK)
                  }
                  sx={{
                    minHeight: 48,
                    "& .MuiTab-root": {
                      minWidth: { xs: 76, sm: 104 },
                      minHeight: 48,
                      px: { xs: 1, sm: 2 },
                      textTransform: "none",
                      fontSize: { xs: "13px", sm: "14px" },
                      fontWeight: 600,
                    },
                  }}
                >
                  {AGENT_GUIDE_LANGUAGES.map((candidate) => (
                    <Tab
                      key={candidate.id}
                      value={candidate.id}
                      label={candidate.label}
                    />
                  ))}
                </Tabs>
              </Box>
              <FormControl
                size="small"
                sx={{
                  flex: "0 0 auto",
                  width: { xs: 145, sm: 220 },
                  minWidth: 0,
                }}
              >
                <InputLabel
                  id="agent-guide-framework-label"
                  sx={{ color: "#1976d2", fontWeight: 600 }}
                >
                  Framework
                </InputLabel>
                <Select
                  labelId="agent-guide-framework-label"
                  id="agent-guide-framework"
                  value={guide.id}
                  label="Framework"
                  onChange={(event) =>
                    selectGuide(language.id, event.target.value)
                  }
                  sx={{
                    bgcolor: "#eef6ff",
                    color: "#145b9e",
                    fontWeight: 700,
                    "& .MuiOutlinedInput-notchedOutline": {
                      borderColor: "#72afe3",
                      borderWidth: "1.5px",
                    },
                    "&:hover .MuiOutlinedInput-notchedOutline": {
                      borderColor: "#1976d2",
                    },
                    "&.Mui-focused .MuiOutlinedInput-notchedOutline": {
                      borderColor: "#1976d2",
                    },
                  }}
                >
                  {language.guides.map((candidate) => (
                    <MenuItem key={candidate.id} value={candidate.id}>
                      {candidate.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Stack>
          </Box>
          <Box sx={{ bgcolor: "#fff" }}>
            <AgentGuideMarkdown markdown={guide.markdown} />
          </Box>
        </Paper>
      </SectionContainer>
    </>
  );
}
