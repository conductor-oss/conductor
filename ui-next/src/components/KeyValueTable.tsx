import { Grid } from "@mui/material";
import StatusBadge from "components/StatusBadge";
import _isNil from "lodash/isNil";
import { customTypeRenderers } from "plugins/customTypeRenderers";
import { useEnv } from "plugins/env";
import { HumanTaskState } from "types/HumanTaskTypes";
import { TaskStatus } from "types/TaskStatus";
import { durationRenderer, timestampRenderer } from "utils/index";
import { type ReactNode } from "react";
import Paper from "./ui/Paper";

export type KeyValueTableRow = {
  label: ReactNode;
  value: unknown;
  type?: string;
};

type KeyValueTableProps = {
  data: KeyValueTableRow[];
};

type Env = ReturnType<typeof useEnv>;

type CustomTypeRenderer = (
  value: unknown,
  data: KeyValueTableRow[],
  env: Env,
) => ReactNode;

const typedCustomRenderers = customTypeRenderers as Record<
  string,
  CustomTypeRenderer | undefined
>;

function positiveNumberOrNull(value: unknown): number | null {
  if (typeof value === "number" && !isNaN(value) && value > 0) {
    return value;
  }
  if (typeof value === "string") {
    const n = Number(value);
    if (!isNaN(n) && n > 0) {
      return n;
    }
  }
  return null;
}

export default function KeyValueTable({ data }: KeyValueTableProps) {
  const env = useEnv();
  return (
    <Grid container sx={{ width: "100%" }}>
      {data.map((item, index) => {
        let displayValue: ReactNode;
        const renderer = item.type ? typedCustomRenderers[item.type] : null;
        if (renderer) {
          displayValue = renderer(item.value, data, env);
        } else {
          switch (item.type) {
            case "date": {
              const n = positiveNumberOrNull(item.value);
              displayValue = n != null ? timestampRenderer(n) : "N/A";
              break;
            }
            case "duration": {
              const n = positiveNumberOrNull(item.value);
              displayValue = n != null ? durationRenderer(n) : "N/A";
              break;
            }

            case "status":
              displayValue = (
                <StatusBadge
                  status={item.value as TaskStatus | HumanTaskState}
                />
              );
              break;

            default:
              displayValue = !_isNil(item.value)
                ? (item.value as ReactNode)
                : "N/A";
          }
        }

        return (
          <Grid
            key={index}
            sx={{
              width: "100%",
            }}
          >
            <Grid
              container
              sx={{
                padding: 3,
                paddingTop: 2,
                paddingBottom: 2,
                borderBottom: "1px solid",
                borderColor: "rgba(0,0,0,.15)",
                width: "100%",
              }}
              rowSpacing={2}
            >
              <Grid
                flexGrow={1}
                sx={{
                  opacity: 0.7,
                }}
              >
                {item.label}
              </Grid>
              <Grid
                sx={
                  item.type === "error"
                    ? { maxHeight: "20vh", overflowY: "auto" }
                    : {}
                }
                id={`key-value-table-${String(item.label ?? "").replace(
                  " ",
                  "-",
                )}-row-value`}
                size={{
                  xs: 12,
                  lg: 9,
                }}
              >
                {item.type === "error" ? (
                  <Paper
                    elevation={0}
                    sx={{
                      border: "1px solid #E4E4E7",
                      whiteSpace: "pre-wrap",
                      wordBreak: "break-word",
                      padding: 2.5,
                      fontFamily: "Monaco",
                      background: "#FAFAFA",
                      color: "#18181B",
                      borderRadius: 1,
                      margin: 0,
                    }}
                  >
                    <code>{String(item.value ?? "")}</code>
                  </Paper>
                ) : (
                  displayValue
                )}
              </Grid>
            </Grid>
          </Grid>
        );
      })}
    </Grid>
  );
}
