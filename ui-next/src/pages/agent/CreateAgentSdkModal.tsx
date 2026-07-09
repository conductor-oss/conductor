import { Box, Button, Typography } from "@mui/material";
import UIModal from "components/ui/dialogs/UIModal";

const AGENTSPAN_GITHUB_URL = "https://github.com/agentspan-ai/agentspan";

/**
 * Temporary bridge modal: agents can't be created directly in this UI yet —
 * they're built with the AgentSpan SDK/CLI and show up here once deployed.
 * Remove/replace once in-UI agent creation ships.
 */
export default function CreateAgentSdkModal({
  open,
  setOpen,
}: {
  open: boolean;
  setOpen: (open: boolean) => void;
}) {
  return (
    <UIModal
      open={open}
      setOpen={setOpen}
      title="Build an agent with the AgentSpan SDK"
      maxWidth="sm"
      enableCloseButton
      footerChildren={
        <>
          <Button onClick={() => setOpen(false)} color="inherit">
            Close
          </Button>
          <Button
            component="a"
            href={AGENTSPAN_GITHUB_URL}
            target="_blank"
            rel="noopener noreferrer"
            variant="contained"
          >
            View AgentSpan on GitHub
          </Button>
        </>
      }
    >
      <Box>
        <Typography sx={{ fontSize: "14px", color: "#494949" }}>
          Agents aren&apos;t created directly in this UI yet — they&apos;re
          built with the AgentSpan SDK or CLI, then compiled and deployed as
          native Conductor workflows. Once deployed, they&apos;ll show up here
          automatically.
        </Typography>
      </Box>
    </UIModal>
  );
}
