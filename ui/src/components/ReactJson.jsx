import React, { useState } from "react";
import RawReactJson from "react-json-view";
import FileCopyIcon from "@material-ui/icons/FileCopy";
import { Button, Tooltip } from "@material-ui/core";
import { Heading } from "../components";
import { makeStyles } from "@material-ui/styles";
import clsx from "clsx";

const useStyles = makeStyles({
  wrapper: {
    fontSize: 12,
  },
});

export default function ReactJson({ title, className, style, ...props }) {
  const [clipboardMode, setClipboardMode] = useState(false);
  const classes = useStyles();
  return (
    <div className={clsx([classes.wrapper, className])} style={style}>
      <div style={{ display: "flex", flexDirection: "row" }}>
        <Heading level={1} gutterBottom>
          {title}
        </Heading>
        <Tooltip title="Hover over JSON to copy snippets to clipboard.">
          <Button
            color={clipboardMode ? "primary" : "default"}
            style={{ marginLeft: "auto" }}
            onClick={() => setClipboardMode(!clipboardMode)}
            startIcon={<FileCopyIcon />}
          >
            Copy Mode {clipboardMode && " ON"}
          </Button>
        </Tooltip>
      </div>
      <RawReactJson
        enableClipboard={clipboardMode}
        displayObjectSize={false}
        displayDataTypes={false}
        name={null}
        {...props}
      />
    </div>
  );
}
