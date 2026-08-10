import { Box, DialogActions, SxProps, Theme } from "@mui/material";
import Dialog, { DialogProps } from "@mui/material/Dialog";
import { XCircle } from "@phosphor-icons/react";
import React, { CSSProperties, ReactNode, forwardRef, useState } from "react";
import {
  defaultModalBackdropColor,
  seGrey2,
  blueLight,
} from "theme/tokens/colors";

type UIModalProps = Omit<DialogProps, "title"> & {
  style?: CSSProperties;
  setOpen: (value: boolean) => void;
  title?: string | React.ReactNode;
  description?: string | React.ReactNode;
  icon?: React.ReactNode;
  enableCloseButton?: boolean;
  backdropColor?: string;
  maxWidth?: any;
  footerChildren?: ReactNode;
  footerSx?: SxProps;
  titleSx?: SxProps;
};

const modalStyles = {
  background: "#FFFFFF",
  boxShadow: "4px 4px 10px 0px rgba(89, 89, 89, 0.41)",
  p: 4,
  overflow: "auto",
};

const headerStyles = {
  display: "flex",
  alignItems: "flex-start",
  "& > *": {
    padding: "3px",
    "&:first-of-type": {
      paddingLeft: "0px",
    },
  },
};

const titleStyles: SxProps<Theme> = {
  fontSize: "16px",
  lineHeight: "16px",
  fontWeight: 600,
  textTransform: "uppercase",
};
const descStyles = {
  color: "#858585",
  fontSize: "12px",
  fontWeight: 300,
  lineHeight: "18px",
  paddingTop: "2px",
  display: "-webkit-box",
  WebkitLineClamp: 2,
  WebkitBoxOrient: "vertical",
  overflow: "hidden",
  textOverflow: "ellipsis",
  cursor: "pointer",
};
const contentStyles = {
  padding: "15px 22px 20px 25px",
};

const TruncatedDescription = ({
  description,
}: {
  description: string | ReactNode;
}) => {
  const [isExpanded, setIsExpanded] = useState(false);

  if (typeof description !== "string") {
    return <Box sx={descStyles}>{description}</Box>;
  }

  const maxLength = 330;

  const truncatedText =
    !isExpanded && description.length > maxLength
      ? description.substring(0, maxLength) + "... "
      : description;

  return (
    <Box
      sx={{
        ...descStyles,
        WebkitLineClamp: isExpanded ? "unset" : 2,
        cursor: "pointer",
      }}
      onClick={() => setIsExpanded(!isExpanded)}
    >
      {truncatedText}
      {!isExpanded && description.length > maxLength && (
        <Box
          component="span"
          sx={{
            color: blueLight,
            fontSize: "12px",
            cursor: "pointer",
            display: "inline",
          }}
          onClick={(e) => {
            e.stopPropagation();
            setIsExpanded(true);
          }}
        >
          Read more
        </Box>
      )}
    </Box>
  );
};

const UIModal = forwardRef<HTMLDivElement, UIModalProps>(
  (
    {
      style,
      open,
      setOpen,
      children,
      icon,
      title,
      description,
      enableCloseButton,
      maxWidth,
      backdropColor,
      footerChildren,
      footerSx,
      id,
      titleSx,
      ...props
    },
    ref,
  ) => {
    const backdropStyles = {
      background: backdropColor ? backdropColor : defaultModalBackdropColor,
      opacity: "0.75 !important",
    };
    return (
      <Dialog
        {...props}
        ref={ref}
        sx={{ ...style }}
        fullWidth={true}
        maxWidth={maxWidth}
        open={open}
        onClose={() => setOpen(false)}
        PaperProps={{
          style: { borderRadius: "6px" },
          ...(props.PaperProps !== undefined ? props.PaperProps : {}),
        }}
        slotProps={{
          backdrop: {
            sx: { ...backdropStyles },
          },
        }}
        aria-labelledby={`alert-dialog-${title}`}
      >
        <Box
          id={id}
          sx={[
            modalStyles,
            !!footerChildren && {
              boxShadow: "none",
            },
          ]}
        >
          <Box sx={headerStyles}>
            {icon && (
              <Box
                sx={{
                  display: "flex",
                  alignItems: "center",
                  color: blueLight,
                  "> *": {
                    fontSize: "20px",
                  },
                }}
              >
                {icon}
              </Box>
            )}
            <Box>
              {title && (
                <Box
                  sx={{
                    ...titleStyles,
                    ...titleSx,
                  }}
                >
                  {title}
                </Box>
              )}
              {description && (
                <TruncatedDescription description={description} />
              )}
            </Box>
            {enableCloseButton && (
              <Box
                sx={{
                  display: "flex",
                  alignItems: "center",
                  marginLeft: "auto",
                  cursor: "pointer",
                }}
                onClick={() => setOpen(false)}
              >
                <XCircle size={20} color={seGrey2} />
              </Box>
            )}
          </Box>
          <Box sx={contentStyles}>{children}</Box>
        </Box>
        {footerChildren && (
          <DialogActions sx={{ ...footerSx }}>{footerChildren}</DialogActions>
        )}
      </Dialog>
    );
  },
);

export default UIModal;
export type { UIModalProps };
