import { Typography } from "components";
import { SxProps } from "@mui/system";

type UnderlinedTextProps = {
  text: string;
  underlinedIndexes: number[];
};

const underlinedStyle: SxProps = {
  fontSize: "inherit",
  fontWeight: "inherit",
  textDecoration: "underline",
  textUnderlineOffset: "0.2em",
};

export const UnderlinedText = ({
  text,
  underlinedIndexes,
}: UnderlinedTextProps) => {
  return (
    <Typography
      component="span"
      sx={{ fontSize: "inherit", fontWeight: "inherit" }}
    >
      {text.split("").map((char, index) =>
        underlinedIndexes.includes(index) ? (
          <Typography
            key={`${char}-${index}`}
            component="span"
            sx={underlinedStyle}
          >
            {char}
          </Typography>
        ) : (
          char
        ),
      )}
    </Typography>
  );
};
