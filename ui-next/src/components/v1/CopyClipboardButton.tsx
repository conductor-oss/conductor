import IconButton, { IconButtonProps } from "@mui/material/IconButton";
import { useContext } from "react";

import CopyIcon from "components/v1/icons/CopyIcon";
import { MessageContext } from "components/v1/layout/MessageContext";

export type CopyClipboardButtonProps = IconButtonProps & {
  text: string;
  message?: string;
};

export const CopyClipboardButton = ({
  text,
  message = "Copied to clipboard!",
  onClick,
}: CopyClipboardButtonProps) => {
  const { setMessage } = useContext(MessageContext);

  return (
    <IconButton
      disableRipple
      onClick={(event) => {
        navigator.clipboard.writeText(text);
        setMessage({
          text: message,
          severity: "success",
        });

        if (onClick) {
          onClick(event);
        }
      }}
      sx={{
        color: (theme) => theme.palette.primary.main,
        "&:hover": {
          backgroundColor: "transparent",
        },
        padding: 0,
        marginLeft: 1,
      }}
    >
      <CopyIcon size={16} />
    </IconButton>
  );
};
