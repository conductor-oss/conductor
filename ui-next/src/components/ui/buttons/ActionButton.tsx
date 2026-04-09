import { Box } from "@mui/material";
import CircularProgress from "@mui/material/CircularProgress";
import Button, { MuiButtonProps } from "./MuiButton";

const style = {
  // Let DialogActions / flex parents lay out like a plain Button sibling (one flex item).
  root: {
    display: "contents",
  },
  wrapper: {
    position: "relative",
    width: "fit-content",
    maxWidth: "100%",
    flexShrink: 0,
  },
  buttonProgress: {
    position: "absolute",
    top: "50%",
    left: "50%",
    marginTop: "-12px",
    marginLeft: "-12px",
  },
};

export interface IActionButtonProps extends MuiButtonProps {
  progress?: boolean;
}

const ActionButton = ({
  children,
  disabled,
  onClick,
  progress,
  ...props
}: IActionButtonProps) => {
  return (
    <Box sx={style.root}>
      <Box sx={style.wrapper}>
        <Button disabled={disabled || progress} onClick={onClick} {...props}>
          {children}
        </Button>
        {progress && (
          <CircularProgress
            size={24}
            sx={style.buttonProgress}
            color="secondary"
          />
        )}
      </Box>
    </Box>
  );
};

export default ActionButton;
