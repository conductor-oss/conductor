import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import {
  Accordion,
  AccordionDetails,
  AccordionProps,
  AccordionSummary,
  SxProps,
  Theme,
  Typography,
  alpha,
} from "@mui/material";
import { ReactNode, useState } from "react";

const ACCORDION_HEIGHT = 51;

export const PanelAccordion = ({
  children,
  sx = {},
  title,
  defaultExpanded = false,
  ...rest
}: {
  children: ReactNode;
  sx?: SxProps<Theme>;
  title: ReactNode;
  defaultExpanded?: boolean;
} & AccordionProps) => {
  const [isExpanded, setIsExpanded] = useState(defaultExpanded);

  return (
    <Accordion
      expanded={isExpanded}
      onChange={() => setIsExpanded(!isExpanded)}
      sx={{
        "&.Mui-expanded": {
          margin: 0,
        },
        ...sx,
      }}
      {...rest}
    >
      <AccordionSummary
        expandIcon={<ExpandMoreIcon sx={{ color: alpha("#4C4E64", 0.54) }} />}
        sx={{
          px: 5,
          minHeight: ACCORDION_HEIGHT,
          "&.Mui-expanded": {
            minHeight: ACCORDION_HEIGHT,
            width: "100%",
            bgcolor: "#F3FBFF",
          },
          "&:hover": {
            bgcolor: "#F3FBFF",
          },
          "& .MuiAccordionSummary-content": {
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            width: "100%",
          },
          "& .MuiAccordionSummary-content.Mui-expanded": {
            margin: 0,
          },
        }}
      >
        <Typography
          sx={{
            color: "#1A1A1A",
            fontWeight: 600,
            fontSize: 16,
            lineHeight: "24px",
          }}
        >
          {title}
        </Typography>
      </AccordionSummary>
      <AccordionDetails
        sx={{
          p: 0,
        }}
      >
        {children}
      </AccordionDetails>
    </Accordion>
  );
};
