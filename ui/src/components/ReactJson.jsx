import React, { useState } from "react";
import RawReactJson from "react-json-view";
import FileCopyIcon from "@material-ui/icons/FileCopy";
import PlaylistAddIcon from "@material-ui/icons/PlaylistAdd";
import ListIcon from "@material-ui/icons/List";
import { Button, Tooltip } from "@material-ui/core";
import { Heading } from "../components";
import { makeStyles } from "@material-ui/styles";
import clsx from "clsx";

const useStyles = makeStyles({
  jsonWrapper: {
    fontSize: 12,
  },
  jsonToolbar: {
    display: "flex",
    flexDirection: "row",
  },
});

const MAX_COLLAPSE = 100;
const MIN_COLLAPSE = 1;

export default function ReactJson({
  title,
  className,
  style,
  initialCollapse = MAX_COLLAPSE,
  ...props
}) {
  const [collapse, setCollapse] = useState(initialCollapse);
  const [clipboardMode, setClipboardMode] = useState(false);
  const classes = useStyles();

  const toggleCollapse = () => {
    if (collapse < MAX_COLLAPSE) {
      setCollapse(MAX_COLLAPSE);
    } else {
      setCollapse(MIN_COLLAPSE);
    }
  };

  return (
    <div className={clsx([classes.wrapper, className])} style={style}>
      <div className={classes.jsonToolbar}>
        <Heading level={1} gutterBottom>
          {title}
        </Heading>
        <Tooltip title="Hover over document to copy snippets to clipboard.">
          <Button
            color={clipboardMode ? "primary" : "default"}
            style={{ marginLeft: "auto" }}
            onClick={() => setClipboardMode(!clipboardMode)}
            startIcon={<FileCopyIcon />}
            size="small"
          >
            Copy Mode {clipboardMode && " ON"}
          </Button>
        </Tooltip>
        <Tooltip
          title={
            collapse === MAX_COLLAPSE
              ? "Collapse JSON object"
              : "Expand JSON object (could be slow for large documents)"
          }
        >
          <Button
            onClick={toggleCollapse}
            style={{ marginLeft: 10 }}
            size="small"
            startIcon={
              collapse === MAX_COLLAPSE ? <ListIcon /> : <PlaylistAddIcon />
            }
          >
            {collapse === MAX_COLLAPSE ? "Collapse All" : "Expand All"}
          </Button>
        </Tooltip>
      </div>
      <RawReactJson
        collapsed={collapse}
        enableClipboard={clipboardMode}
        displayObjectSize={false}
        displayDataTypes={false}
        name={null}
        style={{ fontSize: 12 }}
        {...props}
      />
    </div>
  );
}
