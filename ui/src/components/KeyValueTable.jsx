import React from "react";
import { makeStyles } from "@material-ui/styles";
import { List, ListItem, ListItemText, Tooltip } from "@material-ui/core";
import _ from "lodash";

import { useEnv } from "../plugins/env";
import {
  timestampRenderer,
  timestampMsRenderer,
  durationRenderer,
} from "../utils/helpers";
import { customTypeRenderers } from "../plugins/customTypeRenderers";

const useStyles = makeStyles((theme) => ({
  value: {
    flex: 0.7,
  },
  label: {
    flex: 0.3,
    minWidth: "100px",
  },
  labelText: {
    fontWeight: "bold !important",
  },
}));

export default function KeyValueTable({ data }) {
  const classes = useStyles();
  const env = useEnv();
  return (
    <List>
      {data.map((item, index) => {
        let tooltipText = "";
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
              tooltipText = new Date(item.value).toISOString();
              break;
            case "date-ms":
              displayValue =
                !isNaN(item.value) && item.value > 0
                  ? timestampMsRenderer(item.value)
                  : "N/A";
              tooltipText = new Date(item.value).toISOString();
              break;
            case "duration":
              displayValue =
                !isNaN(item.value) && item.value > 0
                  ? durationRenderer(item.value)
                  : "N/A";
              break;
            default:
              displayValue = !_.isNil(item.value) ? item.value : "N/A";
          }
        }

        return (
          <ListItem key={index} divider alignItems="flex-start">
            <ListItemText
              className={classes.label}
              classes={{ primary: classes.labelText }}
              primary={item.label}
            />

            <ListItemText
              className={classes.value}
              primary={
                <Tooltip
                  placement="right"
                  title={tooltipText}
                  open={tooltipText ? undefined : false}
                >
                  <span>{displayValue}</span>
                </Tooltip>
              }
            />
          </ListItem>
        );
      })}
    </List>
  );
}
