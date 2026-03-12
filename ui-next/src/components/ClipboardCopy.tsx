import { CSSProperties, ReactNode, useState } from "react";
import { Copy } from "@phosphor-icons/react";
import { Box, Snackbar, SxProps } from "@mui/material";
import MuiAlert from "components/MuiAlert";
import { black } from "theme/tokens/colors";
import { logger } from "utils";
import IconButton from "components/MuiIconButton";

export interface ClipboardCopyProps {
  children?: ReactNode;
  value: string;
  buttonId?: string;
  sx?: SxProps;
  linkStyle?: CSSProperties;
  iconPlacement?: "start" | "end";
}

export default function ClipboardCopy({
  children,
  value,
  buttonId = "",
  sx,
  linkStyle,
  iconPlacement = "end",
}: ClipboardCopyProps) {
  const [isToastOpen, setIsToastOpen] = useState(false);

  function copyContent() {
    setIsToastOpen(true);
    navigator.clipboard.writeText(value).catch((e) => {
      logger.error("Unable to copy to clipboard!", e);
    });
  }

  return (
    <>
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          width: "100%",
          flexDirection: iconPlacement === "end" ? "row" : "row-reverse",
          ...sx,
        }}
      >
        <Box
          style={{
            textAlign: "center",
            flexShrink: 1,
            overflow: "hidden",
            whiteSpace: "nowrap",
            ...linkStyle,
          }}
        >
          {children}
        </Box>
        <Box
          sx={{
            flexShrink: 0,
            marginLeft: 1,
            height: "20px",
            width: "20px",
          }}
        >
          <IconButton
            id={buttonId}
            sx={{
              padding: 0,
              "& svg": {
                fontSize: 15,
                color: black,
              },
              "&:active": {
                "& svg": {
                  color: "#5bb8d4",
                },
              },
            }}
            onClick={copyContent}
            size={"small"}
            color="primary"
          >
            <Copy />
          </IconButton>
        </Box>
      </Box>
      <Snackbar
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "right",
        }}
        open={isToastOpen}
        autoHideDuration={10000}
        onClose={() => setIsToastOpen(false)}
        id="copied-to-clipboard-popup"
      >
        <MuiAlert
          variant="filled"
          elevation={6}
          severity="success"
          onClose={() => setIsToastOpen(false)}
        >
          Copied to Clipboard
        </MuiAlert>
      </Snackbar>
    </>
  );
}
