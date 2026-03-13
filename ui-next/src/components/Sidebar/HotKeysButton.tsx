import Chip from "@mui/material/Chip";
import Stack from "@mui/material/Stack";
import { ReactNode, useMemo } from "react";

// Detect if the user is on Windows
const isWindows = () => {
  return (
    /Win/i.test(navigator.platform) || /Windows/i.test(navigator.userAgent)
  );
};

// Convert shortcut display based on OS
const formatShortcut = (
  shortcut: ReactNode,
  isWindowsOS: boolean,
): ReactNode => {
  if (typeof shortcut === "string") {
    // Replace ⌘ with Ctrl on Windows
    if (isWindowsOS) {
      return shortcut.replace(/⌘/g, "Ctrl");
    }
    return shortcut;
  }
  return shortcut;
};

export default function HotKeysButton({
  shortcuts,
}: {
  shortcuts: ReactNode[];
}) {
  const isWindowsOS = useMemo(() => isWindows(), []);

  const formattedShortcuts = useMemo(
    () => shortcuts.map((shortcut) => formatShortcut(shortcut, isWindowsOS)),
    [shortcuts, isWindowsOS],
  );

  return (
    <Stack
      direction="row"
      spacing={1}
      justifyContent="center"
      alignItems="center"
      px={1}
      py={0}
    >
      {formattedShortcuts.map((item, index) => (
        <Chip
          key={`hotkeys_${index}`}
          sx={{
            color: "#494949",
            fontSize: 8,
            boxShadow: "0px 2px 0px #D3D3D3",
            background: "#FFFFFF",
            opacity: 0.7,
            p: 0,
            width: "20px",
            height: "20px",

            ".MuiChip-label": {
              width: "18px",
              height: "18px",
              lineHeight: "unset",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              borderRadius: "1px",
              p: 0,
              background:
                "linear-gradient(180deg, rgba(171, 171, 171, 0.092) 7.47%, rgba(189, 189, 189, 0.4) 81.52%, rgba(189, 189, 189, 0) 97.83%)",
            },
          }}
          label={item}
        />
      ))}
    </Stack>
  );
}
