import { AccordionProps } from "@mui/material/Accordion";
import React from "react";
type TaskFormSectionProps = {
    title?: React.ReactNode;
    children: React.ReactNode;
    collapsible?: boolean;
    accordionAdditionalProps?: Partial<AccordionProps>;
};
declare const TaskFormSection: ({ title, children, collapsible, accordionAdditionalProps, }: TaskFormSectionProps) => React.JSX.Element;
export default TaskFormSection;
