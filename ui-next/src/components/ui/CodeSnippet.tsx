import { useState } from "react";
import { Box, Button, Stack, Typography } from "@mui/material";
import Highlight from "react-highlight";

const MONOSPACE_FONT = 'Menlo, Monaco, "Courier New", monospace';

type CodeSnippetProps = {
  code: string;
  className?: string;
  noCopyToClipboard?: boolean;
  variant?: "default" | "guide";
  sx?: any;
};

export const CodeSnippet = ({
  code,
  className,
  noCopyToClipboard,
  variant = "default",
  sx,
}: CodeSnippetProps) => {
  const [buttonText, setButtonText] = useState("Copy");
  const isGuide = variant === "guide";

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
        ...(isGuide
          ? {
              my: 2.5,
              overflow: "hidden",
              border: "1px solid #c9cdd3",
              borderRadius: "10px",
              bgcolor: "#fff",
              boxShadow: "0 6px 18px rgba(15, 23, 42, 0.08)",
              "& > pre": {
                m: 0,
                p: 0,
                minWidth: 0,
                overflow: "hidden",
                bgcolor: "#fff",
                fontFamily: MONOSPACE_FONT,
                fontSize: "14px",
                fontWeight: 400,
                lineHeight: "21px",
              },
              "& > pre > code": {
                display: "block",
                boxSizing: "border-box",
                m: 0,
                p: "18px 20px",
                width: "100%",
                minHeight: "52px",
                overflowX: "auto",
                overflowY: "visible",
                whiteSpace: "pre",
                bgcolor: "#fff",
                color: "#1f2937 !important",
                fontFamily: `${MONOSPACE_FONT} !important`,
                fontSize: "14px !important",
                fontWeight: "400 !important",
                fontVariantLigatures: "none",
                fontFeatureSettings: '"liga" 0, "calt" 0',
                letterSpacing: 0,
                lineHeight: "21px !important",
                textAlign: "left",
                tabSize: 2,
                WebkitFontSmoothing: "antialiased",
              },
              "& > pre > code span": {
                fontFamily: "inherit !important",
                fontSize: "inherit !important",
                fontWeight: "inherit !important",
                fontVariantLigatures: "inherit !important",
                letterSpacing: "inherit !important",
                lineHeight: "inherit !important",
                verticalAlign: "baseline",
              },
              "& .hljs-subst, & .hljs-variable, & .hljs-template-variable": {
                color: "#1f2937 !important",
              },
              "& .hljs-comment, & .hljs-quote": {
                color: "#6b7280 !important",
              },
              "& .hljs-keyword, & .hljs-selector-tag, & .hljs-built_in": {
                color: "#a31515 !important",
              },
              "& .hljs-string, & .hljs-attribute, & .hljs-addition": {
                color: "#0451a5 !important",
              },
              "& .hljs-number, & .hljs-literal, & .hljs-symbol, & .hljs-bullet":
                {
                  color: "#098658 !important",
                },
              "& .hljs-title, & .hljs-section, & .hljs-name, & .hljs-selector-id, & .hljs-selector-class":
                {
                  color: "#795e26 !important",
                },
            }
          : {
              "& > *": {
                whiteSpace: "pre-wrap",
                overflowWrap: "anywhere",
              },
            }),
        ...sx,
      }}
    >
      {isGuide && (
        <Stack
          direction="row"
          alignItems="center"
          justifyContent="space-between"
          sx={{
            minHeight: 44,
            px: 1.5,
            borderBottom: "1px solid #d8dce2",
            bgcolor: "#f5f6f8",
          }}
        >
          <Typography
            component="span"
            sx={{
              pl: 0.5,
              color: "#536273",
              fontFamily: MONOSPACE_FONT,
              fontSize: "12px",
              fontWeight: 700,
              fontVariantLigatures: "none",
              letterSpacing: "0.08em",
              textTransform: "uppercase",
            }}
          >
            {className || "code"}
          </Typography>
          {!noCopyToClipboard && (
            <Button
              variant="contained"
              size="small"
              onClick={handleCopy}
              sx={{
                minWidth: 76,
                height: 30,
                borderRadius: "6px",
                bgcolor: buttonText === "Copied!" ? "#15803d" : "#2563eb",
                color: "#fff",
                fontSize: "12px",
                fontWeight: 700,
                boxShadow: "none",
                "&:hover": {
                  bgcolor: buttonText === "Copied!" ? "#166534" : "#1d4ed8",
                  boxShadow: "none",
                },
              }}
            >
              {buttonText}
            </Button>
          )}
        </Stack>
      )}
      <Highlight className={className}>{code}</Highlight>

      {!isGuide && !noCopyToClipboard && (
        <Stack
          sx={{
            position: "absolute",
            top: "15px",
            right: "2px",
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
