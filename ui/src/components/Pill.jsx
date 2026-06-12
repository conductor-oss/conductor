import { makeStyles } from "@material-ui/styles";
import Chip from "@material-ui/core/Chip";

const COLORS = {
  red: "rgb(229, 9, 20)",
  yellow: "rgb(251, 164, 4)",
  green: "rgb(65, 185, 87)",
};

const useStyles = makeStyles({
  pill: {
    borderColor: (props) => COLORS[props.color],
    color: (props) => COLORS[props.color],
  },
});

export default function Pill({ color, ...props }) {
  const classes = useStyles({ color });

  return (
    <Chip
      color={color && "primary"}
      variant="outlined"
      {...props}
      classes={{ colorPrimary: classes.pill }}
    />
  );
}
