import { useState } from "react";
import { Box, Button, Stack } from "@mui/material";
import Highlight from "react-highlight";

export const CodeSnippet = ({
  code,
  className,
  noCopyToClipboard,
}: {
  code: string;
  className?: string;
  noCopyToClipboard?: boolean;
}) => {
  const [buttonText, setButtonText] = useState("Copy");

  const handleCopy = () => {
    navigator.clipboard.writeText(code);
    setButtonText("Copied!");
    setTimeout(() => {
      setButtonText("Copy");
    }, 1000);
  };

  return (
    <Box
      sx={{
        position: "relative",
        "& .hljs": {
          padding: "12px",
          borderRadius: "4px",
        },
      }}
    >
      <Highlight className={className}>{code}</Highlight>
      {!noCopyToClipboard && (
        <Stack
          sx={{
            position: "absolute",
            top: "15px",
            right: "12px",
            zIndex: 10,
          }}
          gap={1}
          flexDirection="row"
        >
          <Button
            variant="outlined"
            color="success"
            size="small"
            onClick={handleCopy}
          >
            {buttonText}
          </Button>
        </Stack>
      )}
    </Box>
  );
};
