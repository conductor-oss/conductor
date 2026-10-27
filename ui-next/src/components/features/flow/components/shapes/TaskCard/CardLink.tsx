import { Link, LinkProps } from "@mui/material";
import { MouseEvent } from "react";

/**
 * External link rendered inside a diagram task card. Underlines on hover,
 * shows a focus ring for keyboard users, and stops the click from bubbling
 * so following the link doesn't also select the node.
 */
export const CardLink = ({ sx, onClick, ...props }: LinkProps) => (
  <Link
    target="_blank"
    rel="noreferrer"
    underline="hover"
    onClick={(event: MouseEvent<HTMLAnchorElement>) => {
      event.stopPropagation();
      onClick?.(event);
    }}
    sx={[
      (theme) => ({
        cursor: "pointer",
        borderRadius: "2px",
        transition: "filter 150ms ease",
        textUnderlineOffset: "2px",
        "&:hover": {
          textDecoration: "underline",
          filter:
            theme.palette.mode === "dark"
              ? "brightness(1.25)"
              : "brightness(0.8)",
        },
        "&:focus-visible": {
          outline: "2px solid currentColor",
          outlineOffset: "2px",
        },
      }),
      ...(Array.isArray(sx) ? sx : [sx]),
    ]}
    {...props}
  />
);
