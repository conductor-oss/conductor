import { Link } from "@mui/material";
import { ArrowSquareOut } from "@phosphor-icons/react";
import { useEnv } from "plugins/env";
import { forwardRef } from "react";
import { Link as RouterLink } from "react-router";
import Url from "url-parse";

// 1. Strip `navigate` from props to prevent error
// 2. Preserve stack param
const NavLink = forwardRef((props, ref) => {
  const { path, newTab, ...rest } = props;
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
    return (
      <Link
        ref={ref}
        target="_blank"
        href={url.toString()}
        style={{ display: "flex", alignItems: "center" }}
      >
        {rest.children}
        &nbsp;
        <ArrowSquareOut />
      </Link>
    );
  }
});

export default NavLink;
