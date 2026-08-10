import { FunctionComponent } from "react";
import Button from "components/ui/buttons/MuiButton";
import Stack from "@mui/material/Stack";

export interface RangeButtonsProps {
  onChangeRange: (from: number) => void;
  selected: number;
}

const ONE_DAY = 24;
const THREE_DAYS = 3 * ONE_DAY;
const SEVEN_DAYS = 7 * ONE_DAY;

export const RangeButtons: FunctionComponent<RangeButtonsProps> = ({
  onChangeRange,
  selected,
}) => {
  return (
    <Stack flexDirection="row" gap={2} flexWrap="wrap">
      <Button
        color="secondary"
        onClick={() => onChangeRange(ONE_DAY)}
        disabled={selected === ONE_DAY}
      >
        24h
      </Button>
      <Button
        color="secondary"
        disabled={selected === THREE_DAYS}
        onClick={() => onChangeRange(THREE_DAYS)}
      >
        3D
      </Button>
      <Button
        color="secondary"
        disabled={selected === SEVEN_DAYS}
        onClick={() => onChangeRange(SEVEN_DAYS)}
      >
        7D
      </Button>
    </Stack>
  );
};
