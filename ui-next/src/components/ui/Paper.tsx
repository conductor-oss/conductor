import MuiPaper, { PaperProps } from "@mui/material/Paper";
import { forwardRef, Ref } from "react";

const Paper = forwardRef(function (
  { elevation, ...props }: PaperProps,
  ref: Ref<HTMLDivElement>,
) {
  return (
    <MuiPaper
      ref={ref}
      elevation={elevation && elevation > -1 ? elevation : 0}
      style={{ borderRadius: 4 }}
      {...props}
    />
  );
});
Paper.displayName = "Paper";
export default Paper;
