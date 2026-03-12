import { FunctionComponent, ReactNode, useContext } from "react";
import ConductorBreadcrumbs from "components/v1/ConductorBreadcrumbs";
import {
  Box,
  MenuItem,
  Select,
  Stack,
  StackProps,
  useMediaQuery,
} from "@mui/material";
import { Theme } from "@mui/material/styles";
import Button, { MuiButtonProps } from "components/MuiButton";
import { SidebarContext } from "components/Sidebar/context/SidebarContext";

export interface ActionButton extends MuiButtonProps {
  label?: ReactNode;
  hidden?: boolean;
}

const SIDEBAR_OPEN_BREAKPOINT = 800;
const VALID_WIDTH_BREAKPOINT = 491;

export interface ConductorSectionHeaderProps extends Omit<StackProps, "title"> {
  title: ReactNode;
  id?: string;
  versionSelector?: {
    current: number;
    available: number[];
    onChange: (version: number) => void;
  };
  buttons?: ActionButton[];
  buttonsComponent?: ReactNode;
  breadcrumbItems?: {
    label: string;
    to: string;
    icon?: ReactNode;
  }[];
}

export const ConductorSectionHeader: FunctionComponent<
  ConductorSectionHeaderProps
> = ({
  id = "conductor-header-section-container",
  title,
  buttons,
  buttonsComponent,
  breadcrumbItems,
  versionSelector,
  ...restProps
}) => {
  const { open: isSideBarOpen } = useContext(SidebarContext);
  const isValidOuterWidth = useMediaQuery((theme: Theme) =>
    theme.breakpoints.down(
      isSideBarOpen ? SIDEBAR_OPEN_BREAKPOINT : VALID_WIDTH_BREAKPOINT,
    ),
  );
  const breadcrumbsId = `${id}-breadcrumbs`;
  const titleId = `${id}-title`;
  const buttonsId = `${id}-buttons`;

  const renderButtons = () =>
    buttons?.reduce(
      (
        result,
        { onClick, color, label, disabled, hidden, ...restProps }: ActionButton,
        index: number,
      ) => {
        if (!hidden) {
          result.push(
            <Button
              key={
                typeof label === "string" ? label : `header-buttons-${index}`
              }
              onClick={onClick}
              color={color || "primary"}
              disabled={disabled || false}
              {...restProps}
            >
              {label}
            </Button>,
          );
        }

        return result;
      },
      [] as ReactNode[],
    );

  return (
    <Stack
      id={id}
      direction={["column", isValidOuterWidth ? "column" : "row", "row"]}
      justifyContent="space-between"
      alignItems={["start", "start", "center"]}
      padding={6}
      paddingTop={1.5}
      paddingBottom={2}
      marginTop={0}
      rowGap={2}
      gap={1}
      {...restProps}
    >
      <Stack maxWidth={["100%", "50%"]}>
        {breadcrumbItems && breadcrumbItems.length > 0 ? (
          <ConductorBreadcrumbs id={breadcrumbsId} items={breadcrumbItems} />
        ) : null}

        <Box
          id={titleId}
          sx={{
            margin: 0,
            fontSize: "20px",
            fontWeight: 700,
            letterSpacing: "-0.03em",
            width: "100%",
            color: "text.primary",
            marginBottom: [0, 0],
          }}
        >
          {title}
        </Box>
      </Stack>

      <Stack direction={"row"} gap={1}>
        {versionSelector && (
          <Select
            value={versionSelector.current}
            labelId="version-label"
            onChange={(event) =>
              versionSelector.onChange(event.target.value as number)
            }
            variant="standard"
          >
            <MenuItem value={-1}>Latest Version</MenuItem>
            {versionSelector.available.map((v: number) => (
              <MenuItem value={v} key={v}>
                {`Version ${v}`}
              </MenuItem>
            ))}
          </Select>
        )}

        {buttonsComponent ? buttonsComponent : null}

        {buttons && buttons.length > 0 ? (
          <Stack id={buttonsId} flexDirection={"row"} gap={1} flexWrap="wrap">
            {renderButtons()}
          </Stack>
        ) : null}
      </Stack>
    </Stack>
  );
};
