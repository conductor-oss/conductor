import MoreVertIcon from "@mui/icons-material/MoreVert";
import Box, { BoxProps } from "@mui/material/Box";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import Grow from "@mui/material/Grow";
import ListItemIcon from "@mui/material/ListItemIcon";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import MenuList from "@mui/material/MenuList";
import Paper from "@mui/material/Paper";
import Popper from "@mui/material/Popper";
import Stack from "@mui/material/Stack";
import { Theme } from "@mui/material/styles";
import useMediaQuery from "@mui/material/useMediaQuery";
import MuiButton from "components/ui/buttons/MuiButton";
import MuiButtonGroup from "components/ui/buttons/MuiButtonGroup";
import DocsIcon from "components/icons/DocsIcon";
import SlackIcon from "images/svg/slack-logo-transparent.svg?react";
import { useRef, useState } from "react";
import { colors } from "theme/tokens/variables";
import { featureFlags, FEATURES } from "utils/flags";
import { openInNewTab } from "utils/helpers";

const linkButtons = [
  {
    text: "Docs",
    icon: <DocsIcon />,
    linkTo: "https://orkes.io/content/",
    hidden: !featureFlags.isEnabled(FEATURES.SHOW_DOCUMENTATION),
  },

  {
    text: "Join our Slack",
    icon: <SlackIcon />,
    linkTo:
      "https://join.slack.com/t/orkes-conductor/shared_invite/zt-xyxqyseb-YZ3hwwAgHJH97bsrYRnSZg",
    hidden: !featureFlags.isEnabled(FEATURES.SHOW_JOIN_SLACK_COMMUNITY),
  },
];

const SMALL_SCREEN_BREAKPOINT_WHEN_SIDEBAR_OPEN = 1200;

export interface ButtonLinksProps extends BoxProps {
  showDropdownOnly: boolean;
  isSideBarOpen: boolean;
}

const buttonsToRender = linkButtons.filter((button) => !button.hidden);

export default function ButtonLinks({
  showDropdownOnly,
  isSideBarOpen,
  ...rest
}: ButtonLinksProps) {
  const [open, setOpen] = useState(false);
  const anchorRef = useRef<HTMLDivElement>(null);
  // Checking responsive width (Mobile)
  const isSmallScreen = useMediaQuery((theme: Theme) =>
    theme.breakpoints.down(
      isSideBarOpen ? SMALL_SCREEN_BREAKPOINT_WHEN_SIDEBAR_OPEN : "md",
    ),
  );

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
  return buttonsToRender.length !== 0 ? (
    <Box {...rest}>
      <Stack
        direction="row"
        flexWrap="wrap"
        gap={2}
        sx={{
          py: 0.5,
          alignItems: "center",
          justifyContent: "flex-end",
        }}
      >
        {!isSmallScreen &&
          !showDropdownOnly &&
          buttonsToRender.map(({ text, linkTo, icon }) => (
            <MuiButton
              variant="text"
              key={text}
              startIcon={icon}
              onClick={() => openInNewTab(linkTo)}
            >
              {text}
            </MuiButton>
          ))}
        {(isSmallScreen || showDropdownOnly) && (
          <MuiButtonGroup
            aria-label="more-button"
            variant="text"
            ref={anchorRef}
          >
            <MuiButton startIcon={<MoreVertIcon />} onClick={handleToggle}>
              More
            </MuiButton>
          </MuiButtonGroup>
        )}
        <Popper
          sx={{
            boxShadow: "4px 4px 10px 0px rgba(89, 89, 89, 0.41)",
            border: `1px solid ${colors.blueLight}`,
            borderRadius: "6px",
            width: "inherit",
          }}
          open={open}
          role={undefined}
          anchorEl={anchorRef.current}
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
                    {buttonsToRender.map((option) => (
                      <MenuItem
                        sx={{
                          "& .MuiTypography-root": {
                            color: colors.blueLightMode,
                            fontWeight: 500,
                            fontSize: "14px !important",
                          },
                        }}
                        key={option.text}
                        // selected={index === selectedIndex}
                        onClick={() => {
                          openInNewTab(option.linkTo);
                          setOpen(false);
                        }}
                      >
                        <ListItemIcon sx={{ color: colors.blueLightMode }}>
                          {option.icon}
                        </ListItemIcon>
                        <ListItemText>{option.text}</ListItemText>
                      </MenuItem>
                    ))}
                  </MenuList>
                </ClickAwayListener>
              </Paper>
            </Grow>
          )}
        </Popper>
      </Stack>
    </Box>
  ) : null;
}
