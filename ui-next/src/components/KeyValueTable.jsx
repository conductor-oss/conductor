import { Grid } from "@mui/material";
import _isNil from "lodash/isNil";

import { useEnv } from "plugins/env";
import { durationRenderer, timestampRenderer } from "utils/index";
import { customTypeRenderers } from "plugins/customTypeRenderers";
import StatusBadge from "components/StatusBadge";
import Paper from "./Paper";

export default function KeyValueTable({ data }) {
  const env = useEnv();
  return (
    <Grid container sx={{ width: "100%" }}>
      {data.map((item, index) => {
        let displayValue;
        const renderer = item.type ? customTypeRenderers[item.type] : null;
        if (renderer) {
          displayValue = renderer(item.value, data, env);
        } else {
          switch (item.type) {
            case "date":
              displayValue =
                !isNaN(item.value) && item.value > 0
                  ? timestampRenderer(item.value)
                  : "N/A";
              break;
            case "duration":
              displayValue =
                !isNaN(item.value) && item.value > 0
                  ? durationRenderer(item.value)
                  : "N/A";
              break;

            case "status":
              displayValue = <StatusBadge status={item.value} />;
              break;

            default:
              displayValue = !_isNil(item.value) ? item.value : "N/A";
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
                id={`key-value-table-${item.label?.replace(
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
                    <code>{item.value}</code>
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
