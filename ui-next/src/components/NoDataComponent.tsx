import React from "react";
import EmptyPageIntro, { EmptyPageIntroProps } from "./EmptyPageIntro";
import TagChip from "./TagChip";
import { Box } from "@mui/material";

type NoDataComponentProps = {
  id?: string;
  title?: string;
  titleBg?: string;
  description: string;
  buttonText?: string;
  buttonHandler?: () => void;
  disableButton?: boolean;
  videoUrl?: string;
};

const NoDataComponent = ({
  id,
  title,
  titleBg,
  buttonText,
  buttonHandler,
  description,
  disableButton = false,
  videoUrl,
}: NoDataComponentProps) => {
  const props: Omit<EmptyPageIntroProps, "title"> = {
    id,
    message: description,
    videoUrl,
    ...(buttonText && {
      primaryAction: {
        text: buttonText,
        onClick: buttonHandler || (() => {}),
        disabled: disableButton,
      },
    }),
  };

  return (
    <EmptyPageIntro
      {...props}
      title={
        titleBg ? (
          <TagChip
            label={title || "Empty"}
            style={{
              background: titleBg || "#DDD",
              fontSize: "10px",
              fontWeight: 500,
              padding: "15px 5px",
            }}
          />
        ) : (
          <Box sx={{ fontSize: "16px", fontWeight: 500, color: "#494949" }}>
            {title || "Empty"}
          </Box>
        )
      }
    />
  );
};

export type { NoDataComponentProps };
export default NoDataComponent;
