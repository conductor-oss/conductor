import { FunctionComponent } from "react";
import AccordionSummary from "@mui/material/AccordionSummary";
import { Box, Chip, Stack } from "@mui/material";
import { CaretRight, CaretDown } from "@phosphor-icons/react";
import MuiTypography from "components/MuiTypography";

interface AccordionErrorSummaryProps {
  title: string;
  expanded: boolean;
  count?: number;
}

export const AccordionErrorSummary: FunctionComponent<
  AccordionErrorSummaryProps
> = ({ title, expanded, count }) => (
  <AccordionSummary
    sx={{
      backgroundColor: "rgba(255, 255, 255, 0.05)",
      border: "1px solid rgba(255, 255, 255, 0.1)",
      borderTop: "1px solid rgba(255, 255, 255, 0.25)",
      borderRadius: "4px 4px 0 0",
      minHeight: "48px",
      transition: "all 0.2s ease-in-out",
      "&:hover": {
        backgroundColor: "rgba(255, 255, 255, 0.08)",
        borderColor: "rgba(255, 255, 255, 0.2)",
        transform: "translateY(-1px)",
        boxShadow: "0 2px 8px rgba(0, 0, 0, 0.2)",
      },
      "&:active": {
        transform: "translateY(0)",
      },
      "& .MuiAccordionSummary-content": {
        margin: "12px 0",
      },
    }}
    aria-controls="error-inspector-content"
    id="error-inspector-header"
  >
    <Stack
      direction="row"
      spacing={2}
      sx={{
        alignItems: "center",
        justifyContent: "flex-start",
        width: "100%",
      }}
    >
      {expanded ? (
        <CaretDown size={16} color="#ffffff" />
      ) : (
        <CaretRight size={16} color="#ffffff" />
      )}
      <Box sx={{ display: "flex", alignItems: "center" }}>
        <MuiTypography
          variant="subtitle2"
          width="100%"
          sx={{
            color: "#ffffff",
            fontWeight: 600,
            fontSize: "0.95rem",
            mr: 2,
          }}
        >
          {title}
        </MuiTypography>
        {count !== undefined && (
          <Chip
            label={`${count} issue${count > 1 ? "s" : ""}`}
            size="small"
            sx={{
              backgroundColor:
                title === "Workflow errors" ? "#f44336" : "#ff9800",
              color: "white",
              fontSize: "0.7rem",
              height: 20,
              fontWeight: 500,
            }}
          />
        )}
      </Box>
    </Stack>
  </AccordionSummary>
);
