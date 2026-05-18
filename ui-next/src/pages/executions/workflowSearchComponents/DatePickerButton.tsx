import CalendarTodayIcon from "@mui/icons-material/CalendarToday";
import { ButtonBase } from "@mui/material";
import {
  DateControlComponent,
  DateControlComponentProps,
  DateControlHandle,
} from "../DateControlComponent";
import { forwardRef, useImperativeHandle, useRef } from "react";

export interface DatePickerButtonProps extends Omit<
  DateControlComponentProps,
  | "toDisplayTime"
  | "setToDisplayTime"
  | "endTimeStart"
  | "onEndFromChange"
  | "endTime"
  | "onEndToChange"
> {
  /** Called when this picker opens so the sibling picker can be closed. */
  onOpen?: () => void;
}

/** Imperative handle exposed so a sibling picker can close this one. */
export interface DatePickerButtonHandle {
  close: () => void;
}

export const DatePickerButton = forwardRef<
  DatePickerButtonHandle,
  DatePickerButtonProps
>(function DatePickerButton({ onOpen, ...pickerProps }, ref) {
  const innerRef = useRef<DateControlHandle>(null);

  useImperativeHandle(ref, () => ({
    close: () => innerRef.current?.close(),
  }));

  return (
    <ButtonBase
      onClick={() => {
        innerRef.current?.toggle();
        onOpen?.();
      }}
      sx={{
        display: "inline-flex",
        alignItems: "center",
        gap: 0.75,
        height: 36,
        pl: 1.25,
        pr: 1,
        border: "1px solid",
        borderColor: (t) =>
          t.palette.mode === "dark"
            ? "rgba(255,255,255,0.23)"
            : "rgba(0,0,0,0.23)",
        borderRadius: 1,
        transition: "border-color 0.15s",
        "&:hover": { borderColor: "primary.main" },
        ".MuiTypography-root": { lineHeight: 1 },
      }}
    >
      <CalendarTodayIcon
        sx={{
          fontSize: 14,
          color: "text.secondary",
          flexShrink: 0,
          pointerEvents: "none",
        }}
      />
      <DateControlComponent
        ref={innerRef}
        {...pickerProps}
        toDisplayTime=""
        setToDisplayTime={() => {}}
        endTimeStart=""
        onEndFromChange={() => {}}
        endTime=""
        onEndToChange={() => {}}
      />
    </ButtonBase>
  );
});
