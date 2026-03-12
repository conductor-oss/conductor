import React from "react";
import Grid from "@mui/material/Grid";
import ButtonGroup from "@mui/material/ButtonGroup";
import { CaretDown } from "@phosphor-icons/react";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import Grow from "@mui/material/Grow";
import Paper from "@mui/material/Paper";
import Popper from "@mui/material/Popper";
import MenuItem from "@mui/material/MenuItem";
import MenuList from "@mui/material/MenuList";
import Button from "components/MuiButton";

export default function SplitButton({ children, options, onPrimaryClick }) {
  const [open, setOpen] = React.useState(false);
  const anchorRef = React.useRef(null);

  const handleToggle = () => {
    setOpen((prevOpen) => !prevOpen);
  };

  const handleClose = (event) => {
    if (anchorRef.current && anchorRef.current.contains(event.target)) {
      return;
    }

    setOpen(false);
  };

  return (
    <Grid
      container
      sx={{ width: "100%" }}
      direction="column"
      alignItems="center"
    >
      <Grid size={12}>
        <ButtonGroup ref={anchorRef}>
          <Button onClick={onPrimaryClick} color="primary" variant="contained">
            {children}
          </Button>
          <Button color="primary" variant="contained" onClick={handleToggle}>
            <CaretDown />
          </Button>
        </ButtonGroup>
        <Popper
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
                  <MenuList id="split-button-menu">
                    {options.map(({ label, handler }, index) => (
                      <MenuItem
                        key={index}
                        onClick={(event) => {
                          handler(event, index);
                          setOpen(false);
                        }}
                      >
                        {label}
                      </MenuItem>
                    ))}
                  </MenuList>
                </ClickAwayListener>
              </Paper>
            </Grow>
          )}
        </Popper>
      </Grid>
    </Grid>
  );
}
