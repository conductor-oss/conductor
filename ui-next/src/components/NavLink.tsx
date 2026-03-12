import { Link } from "@mui/material";
import { ArrowSquareOut } from "@phosphor-icons/react";
import { useEnv } from "plugins/env";
import { CSSProperties, ForwardedRef, ReactNode, forwardRef } from "react";
import { Link as RouterLink } from "react-router";
import Url from "url-parse";

interface NavLinkProps {
  path: string;
  newTab?: boolean;
  children: ReactNode;
  id?: string;
  style?: CSSProperties;
  target?: string;
  color?: string;
}

const NavLink = forwardRef((props: NavLinkProps, ref: ForwardedRef<any>) => {
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
NavLink.displayName = "NavLink";

export default NavLink;
