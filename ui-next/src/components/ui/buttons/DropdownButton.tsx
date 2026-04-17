import { Fragment, MouseEvent, ReactNode, useRef, useState } from "react";
import { CaretDown } from "@phosphor-icons/react";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import Grow from "@mui/material/Grow";
import Popper from "@mui/material/Popper";
import MenuItem from "@mui/material/MenuItem";
import MenuList from "@mui/material/MenuList";
import Paper from "components/ui/Paper"; // TODO check where this is used seems like specific
import Button, { MuiButtonProps } from "./MuiButton";
import Divider from "@mui/material/Divider";

export type DropdownButtonProps = {
  buttonProps?: MuiButtonProps;
  children?: ReactNode;
  options: any[];
  isOpen?: boolean;
  onClick?: (e: MouseEvent<HTMLButtonElement>, open: boolean) => void;
  onClickAway?: (e: any) => void;
};

export default function DropdownButton({
  children,
  options,
  buttonProps,
  isOpen,
  onClick,
  onClickAway,
}: DropdownButtonProps) {
  const [open, setOpen] = useState(false);
  const isOpenMenu = typeof isOpen === "boolean" ? isOpen : open;
  const anchorRef = useRef<HTMLButtonElement>(null);

  const handleToggle = (e: MouseEvent<HTMLButtonElement>) => {
    setOpen((prevOpen) => !prevOpen);

    if (onClick) {
      onClick(e, !isOpenMenu);
    }
  };

  const handleClose = (event: any) => {
    if (anchorRef.current && anchorRef.current.contains(event.target)) {
      return;
    }

    setOpen(false);

    if (onClickAway) {
      onClickAway(event);
    }
  };

  return (
    <Fragment>
      <Button
        color="primary"
        variant="contained"
        ref={anchorRef}
        onClick={handleToggle}
        size="small"
        sx={{
          justifyContent: "space-around",
          minWidth: "100px",
        }}
        {...buttonProps}
      >
        {children} <CaretDown />
      </Button>

      <Popper
        open={isOpenMenu}
        anchorEl={anchorRef.current}
        role={undefined}
        transition
        disablePortal
        sx={{ maxHeight: "90vh" }}
      >
        {({ TransitionProps, placement }) => (
          <Grow
            {...TransitionProps}
            style={{
              transformOrigin:
                placement === "bottom" ? "center top" : "center bottom",
            }}
          >
            <Paper elevation={3} id="dropdown-button-menu-wrapper">
              <ClickAwayListener onClickAway={handleClose}>
                <MenuList
                  id="split-button-menu"
                  sx={{
                    overflowY: "auto",
                    maxHeight: "75vh",
                  }}
                >
                  {options.map(
                    ({ label, handler, disabled, isDivider }, index) => {
                      const itemId = `${label}-${index}`;

                      return isDivider ? (
                        <Divider key={itemId} sx={{ my: 0.5 }} />
                      ) : (
                        <MenuItem
                          key={itemId}
                          onClick={(event) => {
                            handler(event, index);
                            setOpen(false);
                          }}
                          disabled={disabled}
                        >
                          {label}
                        </MenuItem>
                      );
                    },
                  )}
                </MenuList>
              </ClickAwayListener>
            </Paper>
          </Grow>
        )}
      </Popper>
    </Fragment>
  );
}
