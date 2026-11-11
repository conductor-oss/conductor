import { Box, Paper } from "@mui/material";
import { X as CloseIcon } from "@phosphor-icons/react";
import { Heading, IconButton, KeyValueTable } from "components";
import { FunctionComponent, ReactNode } from "react";
import { WorkflowExecution } from "types/Execution";
import { canBeHeld, heldMismatch, heldReasonOfMarker } from "../held";

interface HeldStepPanelProps {
  /** The task the code paused on. */
  pausedOn?: string;
  execution?: WorkflowExecution;
  onClose: () => void;
}

const Section = ({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) => (
  <Box component="section" sx={{ mb: 3 }}>
    <Heading level={2} fontSize={15} fontWeight={700} sx={{ mb: 1 }}>
      {title}
    </Heading>
    <Box sx={{ fontSize: 14, lineHeight: 1.6 }}>{children}</Box>
  </Box>
);

const Code = ({ children }: { children: ReactNode }) => (
  <Box component="code" sx={{ fontFamily: "monospace", fontWeight: 600 }}>
    {children}
  </Box>
);

/** Explains why native workflow code is held: what was expected, and what happens next. */
export const HeldStepPanel: FunctionComponent<HeldStepPanelProps> = ({
  pausedOn,
  execution,
  onClose,
}) => {
  const marker = execution?.tasks
    ?.filter((task) => task.referenceTaskName === pausedOn)
    .at(-1)?.continuation;
  const reason = canBeHeld(execution) ? heldReasonOfMarker(marker) : undefined;
  const mismatch = reason ? heldMismatch(reason) : undefined;
  const workflow = execution?.workflowName ?? execution?.workflowType;
  const version = execution?.workflowVersion;

  return (
    <Paper square elevation={0} id="held-step-panel" sx={{ height: "100%" }}>
      <Box sx={{ display: "flex", gap: 1, pt: 2, pl: 1 }}>
        <IconButton
          color="secondary"
          size="small"
          aria-label="Close button"
          onClick={onClose}
          sx={{ opacity: 0.5, alignSelf: "start" }}
        >
          <CloseIcon />
        </IconButton>
        <Box sx={{ p: 2, pr: 3, minWidth: 0 }}>
          <Heading level={1} fontSize={20} fontWeight={700} sx={{ mb: 1 }}>
            Waiting for compatible code
          </Heading>
          {!reason ? (
            <Box sx={{ fontSize: 14 }}>
              This execution is no longer waiting for compatible code.
            </Box>
          ) : (
            <>
              <Box sx={{ fontSize: 14, lineHeight: 1.6, mb: 3 }}>
                This execution has paused. It has not failed, and its finished
                tasks are safe.
              </Box>

              <Section title="What we expected, and what we found">
                {mismatch ? (
                  <KeyValueTable
                    data={[
                      {
                        label: "This execution did",
                        value: mismatch.recorded ? (
                          <Code>{mismatch.recorded}</Code>
                        ) : (
                          "Nothing further at this point"
                        ),
                      },
                      {
                        label: "The code now calls",
                        value: <Code>{mismatch.calls}</Code>,
                      },
                      {
                        label: "Where in the code",
                        value: <Code>{mismatch.site}</Code>,
                      },
                    ]}
                  />
                ) : (
                  reason
                )}
              </Section>

              <Section title="Why we are waiting">
                To continue after <Code>{pausedOn}</Code>, a worker runs this
                workflow&apos;s code again from the start and matches each call
                against what this execution has already done. The code made a
                different call, so the worker can&apos;t tell what comes next.
                It stopped instead of guessing, because a guess could repeat or
                skip work. The mismatch can be earlier than{" "}
                <Code>{pausedOn}</Code>; the dashed step only marks where the
                execution stopped.
              </Section>

              <Section title="What this means">
                This usually means the code for <Code>{workflow}</Code> version{" "}
                {version} changed without a new workflow version, so new code
                picked up an execution that older code started. The execution
                stays Running and continues by itself once a worker with
                matching code picks it up. It stops waiting only if the workflow
                times out or someone terminates it.
              </Section>

              <Section title="Current state">
                <KeyValueTable
                  data={[
                    { label: "Last finished task", value: pausedOn },
                    {
                      label: "Waiting since",
                      value: marker?.queuedAt
                        ? new Date(marker.queuedAt).toLocaleString()
                        : "Unknown",
                    },
                    { label: "Attempts so far", value: marker?.attempts ?? 0 },
                    {
                      label: "Next attempt",
                      value: "Offered to workers again, up to a minute apart",
                    },
                    {
                      label: "Needs a worker polling",
                      value: <Code>{marker?.queue}</Code>,
                    },
                  ]}
                />
              </Section>

              <Section title="What you can do">
                <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
                  <li>
                    If the change was a mistake, deploy the code this execution
                    started with.
                  </li>
                  <li>
                    If the change was intended, give the new code a new workflow
                    version, and keep the previous code running as version{" "}
                    {version} until its executions finish.
                  </li>
                  <li>
                    If this execution isn&apos;t needed, terminate it from the
                    Actions menu.
                  </li>
                </Box>
              </Section>
            </>
          )}
        </Box>
      </Box>
    </Paper>
  );
};
