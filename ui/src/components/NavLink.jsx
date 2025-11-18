import React from "react";
import { Link as RouterLink, useHistory } from "react-router-dom";
import { Link } from "@material-ui/core";
import LaunchIcon from "@material-ui/icons/Launch";
import Url from "url-parse";
import { useEnv } from "../plugins/env";
import { getBasename } from "../utils/helpers";
import { cleanDuplicateSlash } from "../plugins/fetch";

// 1. Strip `navigate` from props to prevent error
// 2. Preserve stack param

export default React.forwardRef((props, ref) => {
  const { navigate, path, newTab, absolutePath = false, ...rest } = props;
  const { stack, defaultStack } = useEnv();

  const url = new Url(path, {}, true);
  if (stack !== defaultStack) {
    url.query.stack = stack;
  }

  if (!newTab) {
    return (
      <Link ref={ref} component={RouterLink} to={url.toString()} {...rest}>
        {rest.children}
      </Link>
    );
  } else {
    // Note: + '/' + is required here
    const href = absolutePath ? url.toString() : cleanDuplicateSlash(getBasename() + '/' + url.toString());
    return (
      <Link ref={ref} target="_blank" href={href}>
        {rest.children}
        &nbsp;
        <LaunchIcon fontSize="small" style={{ verticalAlign: "middle" }} />
      </Link>
    );
  }
});

export function usePushHistory() {
  const history = useHistory();
  const { stack, defaultStack } = useEnv();

  return (path) => {
    const url = new Url(path, {}, true);
    if (stack !== defaultStack) {
      url.query.stack = stack;
    }

    history.push(url.toString());
  };
}
