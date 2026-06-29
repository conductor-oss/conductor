import ClickAwayListener from "@mui/material/ClickAwayListener";
import Divider from "@mui/material/Divider";
import Grow from "@mui/material/Grow";
import MenuItem from "@mui/material/MenuItem";
import MenuList from "@mui/material/MenuList";
import Popper from "@mui/material/Popper";
import { CaretDown } from "@phosphor-icons/react";
import Paper from "components/ui/Paper"; // TODO check where this is used seems like specific
import {
  Fragment,
  MouseEvent,
  ReactNode,
  UIEvent,
  useCallback,
  useRef,
  useState,
} from "react";
import Button, { MuiButtonProps } from "./MuiButton";

const RENDER_BATCH_SIZE = 100;

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
  const [renderCount, setRenderCount] = useState(RENDER_BATCH_SIZE);

  const handleToggle = (e: MouseEvent<HTMLButtonElement>) => {
    setOpen((prevOpen) => {
      if (prevOpen) {
        setRenderCount(RENDER_BATCH_SIZE);
      }
      return !prevOpen;
    });

    if (onClick) {
      onClick(e, !isOpenMenu);
    }
  };

  const handleClose = (event: any) => {
    if (anchorRef.current && anchorRef.current.contains(event.target)) {
      return;
    }

    setOpen(false);
    setRenderCount(RENDER_BATCH_SIZE);

    if (onClickAway) {
      onClickAway(event);
    }
  };

  const handleScroll = useCallback(
    (e: UIEvent<HTMLUListElement>) => {
      if (renderCount >= options.length) return;
      const el = e.currentTarget;
      if (el.scrollHeight - el.scrollTop - el.clientHeight < 200) {
        setRenderCount((c) => Math.min(c + RENDER_BATCH_SIZE, options.length));
      }
    },
    [renderCount, options.length],
  );

  const visibleOptions = options.slice(0, renderCount);

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
                  onScroll={handleScroll}
                >
                  {visibleOptions.map(
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
