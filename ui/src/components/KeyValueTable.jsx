import React from "react";
import { makeStyles } from "@material-ui/core/styles";
import List from "@material-ui/core/List";
import ListItem from "@material-ui/core/ListItem";
import ListItemText from "@material-ui/core/ListItemText";
import _ from "lodash";

import { useEnv } from "../plugins/env";
import { timestampRenderer, durationRenderer } from "../utils/helpers";
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
        let displayValue;
        const renderer = item.type ? customTypeRenderers[item.type] : null;
        if(renderer){
          displayValue = renderer(item.value, env)
        }
        else {
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
            <ListItemText className={classes.value} primary={value} />
          </ListItem>
        );
      })}
    </List>
  );
}
