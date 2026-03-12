import {
  Fragment,
  FunctionComponent,
  ReactElement,
  ReactNode,
  useMemo,
} from "react";
import { IconButton, IconButtonProps, Tooltip } from "@mui/material";
import Button, { MuiButtonProps } from "components/MuiButton";

export interface ButtonTooltipProps extends MuiButtonProps {
  tooltip: NonNullable<ReactNode>;
  variant?: "contained" | "text" | "outlined";
  disabled?: boolean;
  onClick: () => void;
  "data-testid"?: string;
  displayChildren?: boolean;
}

export const ButtonTooltip: FunctionComponent<ButtonTooltipProps> = ({
  tooltip,
  disabled = false,
  onClick,
  children,
  variant = "contained",
  displayChildren = true,
  ...otherButtonProps
}) => {
  const Container = useMemo(
    () =>
      ({ children }: { children: ReactElement }) =>
        !tooltip && children != null ? (
          <Fragment>{children}</Fragment>
        ) : (
          <Tooltip title={tooltip} arrow>
            {children}
          </Tooltip>
        ),
    [tooltip],
  );
  return (
    <Container>
      {displayChildren ? (
        <Button
          onClick={onClick}
          disabled={disabled}
          variant={variant}
          {...otherButtonProps}
          component="span"
        >
          {displayChildren ? children : null}
        </Button>
      ) : (
        <IconButton
          onClick={onClick}
          disabled={disabled}
          size="small"
          sx={{ color: (theme) => theme.palette.primary.main }}
          {...(otherButtonProps as IconButtonProps)}
        >
          {otherButtonProps.startIcon}
        </IconButton>
      )}
    </Container>
  );
};
