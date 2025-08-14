import { IconButton, Tooltip } from "@material-ui/core";

export const ZoomControlsButton = ({
  style,
  children,
  tooltip = "",
  ...props
}) => {
  return (
    <Tooltip title={tooltip} arrow>
      <IconButton
        disableRipple
        style={{
          display: "flex",
          border: "none",
          background: "none",
          height: "33px",
          width: "40px",
          alignItems: "center",
          justifyContent: "center",
          cursor: "pointer",
          fontSize: "14px",
          color: "grey",
          borderRadius: "unset",
          ...style,
        }}
        {...props}
      >
        <span style={{ display: "grid" }}>{children}</span>
        {/* {children} */}
      </IconButton>
    </Tooltip>
  );
};
