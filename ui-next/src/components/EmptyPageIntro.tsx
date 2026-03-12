import { Box, Button, Link, colors, Stack } from "@mui/material";
import React from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import ReactPlayer from "react-player";
import TagChip from "./TagChip";
import { logrocketTrackIfEnabled } from "utils/logrocket";

export interface EmptyPageIntroProps {
  id?: string;
  image?: string;
  videoUrl?: string;
  title: React.ReactNode;
  message: string;
  variant?: "featureDisabled" | "default";
  primaryAction?: {
    text: string;
    onClick: () => void;
    disabled?: boolean;
    startIcon?: React.ReactNode;
  };
  secondaryAction?: {
    text: string;
    onClick: () => void;
    disabled?: boolean;
    startIcon?: React.ReactNode;
  };
  footer?: string;
}

const EmptyPageIntro = ({
  id,
  image,
  videoUrl,
  title,
  message,
  primaryAction,
  secondaryAction,
  footer,
  variant = "default",
}: EmptyPageIntroProps) => {
  let visualHeaderType = null;

  // Video takes precedence
  if (videoUrl) {
    visualHeaderType = "video";
  } else if (image) {
    visualHeaderType = "image";
  } else {
    visualHeaderType = null;
  }

  return (
    <Box
      id={id}
      sx={{
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        height: "fit-content",
        marginBottom: "30px",
      }}
    >
      <Stack
        sx={{
          backgroundColor:
            variant === "featureDisabled" ? "#ffffff" : "#F3F3F3",
          borderRadius: "6px",
          padding: "18px 27px",
          textAlign: "center",
          width: "600px",
          justifyContent: "center",
          alignItems: "center",
          gap: "10px",
          boxShadow:
            variant === "featureDisabled"
              ? "0px 0px 10px 0px rgba(0, 0, 0, 0.1)"
              : "none",
        }}
      >
        {variant === "featureDisabled" ? (
          <TagChip
            label="Enterprise Add-On"
            style={{
              background: "#FBB4C6",
              fontSize: "10px",
              fontWeight: 500,
              padding: "15px 5px",
            }}
          />
        ) : null}

        {visualHeaderType === "video" ? (
          <Box sx={{ width: "100%", aspectRatio: "16/9" }}>
            <ReactPlayer url={videoUrl} width="100%" height="100%" controls />
          </Box>
        ) : null}

        {visualHeaderType === "image" ? (
          <img src={image} style={{ maxWidth: "100%", height: "auto" }} />
        ) : null}

        <Box
          sx={{
            fontSize: "14px",
            fontWeight: 500,
            color: "#494949",
          }}
        >
          {title}
        </Box>

        <Box
          sx={{
            fontSize: "12px",
            fontWeight: 300,
            lineHeight: "18px",
            color: "#494949",
          }}
        >
          <ReactMarkdown
            remarkPlugins={[remarkGfm]}
            components={{
              ul: ({ children }) => (
                <ul
                  style={{
                    paddingLeft: "20px",
                    textAlign: "left",
                    margin: "auto",
                    width: "fit-content",
                  }}
                >
                  {children}
                </ul>
              ),
              li: ({ children }) => (
                <li style={{ marginBottom: "5px" }}>{children}</li>
              ),
              a: ({ children, href }) => (
                <Link
                  href={href}
                  onClick={() => {
                    logrocketTrackIfEnabled("blank_slate_docs_link_clicked", {
                      link: href,
                    });

                    return true;
                  }}
                  target="_blank"
                  rel="noopener noreferrer"
                  style={{ color: colors.blue[700], textDecoration: "none" }}
                >
                  {children}
                </Link>
              ),
            }}
          >
            {message}
          </ReactMarkdown>
        </Box>

        {(primaryAction || secondaryAction) && (
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: "10px",
              padding: "10px 0",
              ".MuiButtonBase-root": {
                whiteSpace: "nowrap",
              },
            }}
          >
            {primaryAction && (
              <Button
                variant="contained"
                onClick={primaryAction.onClick}
                disabled={primaryAction.disabled}
                startIcon={primaryAction.startIcon}
                size="small"
              >
                {primaryAction.text}
              </Button>
            )}
            {secondaryAction && (
              <Button
                variant="outlined"
                onClick={secondaryAction.onClick}
                disabled={secondaryAction.disabled}
                startIcon={secondaryAction.startIcon}
                size="small"
              >
                {secondaryAction.text}
              </Button>
            )}
          </Box>
        )}

        {footer && (
          <Box
            sx={{
              fontSize: "12px",
              fontWeight: 300,
              color: "#494949",
              borderTop: "1px solid #DDD",
              paddingTop: "10px",
              marginTop: "10px",
            }}
          >
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{footer}</ReactMarkdown>
          </Box>
        )}
      </Stack>
    </Box>
  );
};

export default EmptyPageIntro;
