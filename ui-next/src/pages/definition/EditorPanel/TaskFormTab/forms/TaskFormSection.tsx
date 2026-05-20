import { Box } from "@mui/material";
import Accordion, { AccordionProps } from "@mui/material/Accordion";
import AccordionDetails from "@mui/material/AccordionDetails";
import AccordionSummary from "@mui/material/AccordionSummary";
import { CaretDown } from "@phosphor-icons/react";
import MuiTypography from "components/ui/MuiTypography";
import _isString from "lodash/isString";
import React, { useContext } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";

type TaskFormSectionProps = {
  title?: React.ReactNode;
  children: React.ReactNode;
  collapsible?: boolean;
  accordionAdditionalProps?: Partial<AccordionProps>;
};

type TaskFormSectionAccordionProps = {
  title?: React.ReactNode;
  collapsible?: boolean;
  accordionAdditionalProps?: Partial<AccordionProps>;
  children?: React.ReactNode;
};

const MaybeWrappedTitle = ({ title }: { title: React.ReactNode }) =>
  _isString(title) ? (
    <MuiTypography
      marginTop={3}
      marginBottom={3}
      opacity={0.6}
      fontWeight={600}
    >
      {title}
    </MuiTypography>
  ) : (
    <>{title}</>
  );

const TaskFormAccordion = ({
  title,
  accordionAdditionalProps,
  children,
}: TaskFormSectionAccordionProps) => {
  const { mode } = useContext(ColorModeContext);

  const ACCORDION_HEIGHT = 40;

  const keyTitle = _isString(title) ? title : "";

  return (
    <Accordion
      sx={{
        borderRadius: 0,
        border: "none",
        borderTop: "1px solid",
        borderBottom: "1px solid",
        borderColor: mode === "light" ? "#cccccc" : "#444444",
        boxShadow: "none",
        background: "none",
        "&:not(:last-child)": {
          borderBottom: 0,
        },
        "&:before": {
          display: "none",
        },
        "&.Mui-expanded": {
          margin: "auto",
        },
      }}
      {...accordionAdditionalProps}
    >
      <AccordionSummary
        sx={{
          minHeight: ACCORDION_HEIGHT,
          fontSize: ".8rem",
          paddingLeft: 6,
          paddingRight: 6,
          opacity: 0.75,
          "&.Mui-expanded": {
            minHeight: ACCORDION_HEIGHT,
            opacity: 1,
            fontWeight: 600,
          },
          "& .MuiAccordionSummary-content.Mui-expanded": {
            margin: "12px 0",
          },
          "& .MuiAccordionSummary-content": {
            opacity: 1,
          },
          "&:hover": {
            background:
              mode === "light"
                ? "rgba(0, 0, 0, .07)"
                : "rgba(255, 255, 255, .2)",
          },
        }}
        expandIcon={
          <CaretDown
            size={18}
            color={mode === "light" ? "#111111" : "#f0f0f0"}
          />
        }
        aria-controls={`${keyTitle}-content`}
        id={`${keyTitle}-header`}
      >
        {title ? <MaybeWrappedTitle title={title} /> : null}
      </AccordionSummary>
      <AccordionDetails
        sx={{
          paddingX: 6,
          paddingY: 1,
          minHeight: `${ACCORDION_HEIGHT}px`,
        }}
        id={`${keyTitle}-content`}
      >
        {children}
      </AccordionDetails>
    </Accordion>
  );
};

const TaskFormSection = ({
  title,
  children,
  collapsible = false,
  accordionAdditionalProps = {},
}: TaskFormSectionProps) => {
  return collapsible ? (
    <TaskFormAccordion
      title={title}
      accordionAdditionalProps={accordionAdditionalProps}
    >
      <Box
        sx={{
          paddingY: 6,
        }}
      >
        {children}
      </Box>
    </TaskFormAccordion>
  ) : (
    <Box
      sx={{
        paddingX: 6,
        paddingTop: 1,
        paddingBottom: 6,
        // border: "1px solid red",
        transition: "all 0.2s ease-in-out",
        // borderTop: "1px solid transparent",
        // borderRadius: 3,
        backgroundImage:
          "linear-gradient(to bottom, rgba(0, 0, 0, .02), rgba(0, 0, 0, 0))",
        borderTop: "1px solid rgba(0, 0, 0, 0.12)",
        // border: "1px solid red",

        // on hover
        "&:hover": {
          // backgroundImage: "none",
        },
      }}
    >
      {title ? <MaybeWrappedTitle title={title} /> : null}
      {children}
    </Box>
  );
};

export default TaskFormSection;
