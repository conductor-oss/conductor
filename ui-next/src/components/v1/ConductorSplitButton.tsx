import {
  Fragment,
  ReactNode,
  useMemo,
  useRef,
  useState,
  MouseEvent as ReactMouseEvent,
  ReactElement,
} from "react";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import Grow from "@mui/material/Grow";
import Paper from "@mui/material/Paper";
import Popper from "@mui/material/Popper";
import MenuItem from "@mui/material/MenuItem";
import MenuList from "@mui/material/MenuList";
import MuiButton, { MuiButtonProps } from "components/MuiButton";
import MuiButtonGroup, { MuiButtonGroupProps } from "components/MuiButtonGroup";
import DropdownIcon from "./icons/DropdownIcon";
import { colors } from "theme/tokens/variables";
import { blueLight } from "theme/tokens/colors";
import { Tooltip } from "@mui/material";

type ConductorSplitButtonProps = MuiButtonGroupProps &
  MuiButtonProps & {
    options: {
      label: ReactNode;
      onClick: () => void;
      id?: string;
      disabled?: boolean;
    }[];
    primaryOnClick: () => void;
    children: ReactNode;
    tooltip?: string;
    "data-testid"?: string;
  };

const groupStyle = {
  ".MuiButtonGroup-grouped": {
    minWidth: "27px",
    ":not(:last-of-type)": {
      borderRight: "1px solid rgba(255, 255, 255, 0.5)",
    },
    "&:hover:not(:last-of-type)": {
      borderRight: "1px solid rgba(255, 255, 255, 0.5)",
    },
  },
};

export default function SplitButton({
  options,
  primaryOnClick,
  children,
  startIcon,
  tooltip,
  id,
  "data-testid": dataTestId,
  ...props
}: ConductorSplitButtonProps) {
  const [open, setOpen] = useState(false);
  const anchorRef = useRef<HTMLDivElement>(null);
  const handleClick = () => {
    primaryOnClick();
  };

  const handleMenuItemClick = (
    _event: ReactMouseEvent<HTMLLIElement, MouseEvent>,
    onClick: () => void,
  ) => {
    onClick();
    setOpen(false);
  };

  const handleToggle = () => {
    setOpen((prevOpen) => !prevOpen);
  };

  const handleClose = (event: Event) => {
    if (
      anchorRef.current &&
      anchorRef.current.contains(event.target as HTMLElement)
    ) {
      return;
    }

    setOpen(false);
  };

  const Container = useMemo(
    () =>
      ({ children }: { children: ReactElement }) =>
        props?.disabled ? (
          <Fragment>{children}</Fragment>
        ) : (
          <Tooltip title={tooltip} arrow>
            {children}
          </Tooltip>
        ),
    [tooltip, props?.disabled],
  );

  return (
    <Fragment>
      <MuiButtonGroup
        ref={anchorRef}
        aria-label="split button"
        variant="contained"
        color="primary"
        size="medium"
        {...props}
        sx={groupStyle}
      >
        <Container>
          <MuiButton
            {...(startIcon && { startIcon: startIcon })}
            id={id}
            onClick={handleClick}
            data-testid={dataTestId}
            fullWidth
          >
            {children}
          </MuiButton>
        </Container>
        <MuiButton
          aria-controls={open ? "split-button-menu" : undefined}
          aria-expanded={open ? "true" : undefined}
          aria-label="select merge strategy"
          aria-haspopup="menu"
          onClick={handleToggle}
          className="conductor-split-button-dropdown-btn"
          sx={{ padding: "0px" }}
        >
          <DropdownIcon />
        </MuiButton>
      </MuiButtonGroup>
      <Popper
        sx={{
          boxShadow: "4px 4px 10px 0px rgba(89, 89, 89, 0.41)",
          border: `1px solid ${blueLight}`,
          borderRadius: "6px",
          width: "inherit",
          minWidth: anchorRef.current?.offsetWidth ?? "100px",
        }}
        open={open}
        anchorEl={anchorRef.current}
        role={undefined}
        transition
        disablePortal
      >
        {({ TransitionProps, placement }) => (
          <Grow
            {...TransitionProps}
            style={{
              transformOrigin:
                placement === "bottom" ? "center top" : "center bottom",
            }}
          >
            <Paper>
              <ClickAwayListener onClickAway={handleClose}>
                <MenuList id="split-button-menu" autoFocusItem>
                  {options.map((option, index) => (
                    <MenuItem
                      sx={{
                        color: colors.black,
                        fontWeight: 500,
                        fontSize: "14px",
                      }}
                      key={
                        typeof option.label === "string"
                          ? option.label
                          : `conductor-split-button-${index}`
                      }
                      // selected={index === selectedIndex}
                      id={option?.id}
                      disabled={option?.disabled}
                      onClick={(event) =>
                        handleMenuItemClick(event, option.onClick)
                      }
                    >
                      {option.label}
                    </MenuItem>
                  ))}
                </MenuList>
              </ClickAwayListener>
            </Paper>
          </Grow>
        )}
      </Popper>
    </Fragment>
  );
}

export type { ConductorSplitButtonProps };
