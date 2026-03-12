import { Box } from "@mui/material";
import CircularProgress from "@mui/material/CircularProgress";
import Button, { MuiButtonProps } from "components/MuiButton";

const style = {
  root: {
    display: "inline-flex",
    flexDirection: "column",
    alignItems: "flex-start",
  },
  wrapper: {
    position: "relative",
    width: "100%",
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
