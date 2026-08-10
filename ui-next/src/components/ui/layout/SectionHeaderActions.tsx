import { Box } from "@mui/material";
import useMediaQuery from "@mui/material/useMediaQuery";
import { Theme } from "@mui/material/styles";
import Button, { MuiButtonProps } from "components/ui/buttons/MuiButton";
import { useLocation } from "react-router";
import { checkPathFlag } from "utils/checkPathFlag";
import { Fragment, ReactNode } from "react";
import { useAuth } from "components/features/auth";

interface IActionButton extends MuiButtonProps {
  label?: string;
  customButtonElement?: ReactNode;
}
const VALID_WIDTH_BREAKPOINT = 491;

const SectionHeaderActions = ({ buttons }: { buttons: IActionButton[] }) => {
  const { pathname } = useLocation();
  const featureFlagEnabled = checkPathFlag(pathname);
  const { isTrialExpired } = useAuth();
  // Checking responsive width
  const isValidWidth = useMediaQuery((theme: Theme) =>
    theme.breakpoints.down(VALID_WIDTH_BREAKPOINT),
  );

  const renderButtons = () =>
    buttons.map(
      (
        {
          onClick,
          color,
          label,
          disabled,
          customButtonElement,
          ...restProps
        }: IActionButton,
        index: number,
      ) => (
        <Fragment key={index}>
          {customButtonElement ? (
            customButtonElement
          ) : (
            <Button
              // sx={buttonStyle}
              onClick={onClick}
              color={color}
              disabled={disabled || !featureFlagEnabled || isTrialExpired}
              {...restProps}
            >
              {label}
            </Button>
          )}
        </Fragment>
      ),
    );

  return (
    <Box
      display="flex"
      gap={3}
      flexDirection={[isValidWidth ? "column-reverse" : "row", "row"]}
      alignItems="center"
    >
      {renderButtons()}
    </Box>
  );
};

export default SectionHeaderActions;
