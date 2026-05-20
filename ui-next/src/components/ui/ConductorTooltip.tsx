import TooltipStateless from "components/ui/TooltipStateless";
import { TooltipProps } from "@mui/material";
import { ReactNode, useEffect, useRef, useState } from "react";

interface ConductorTooltipProps extends Omit<TooltipProps, "content"> {
  title: string;
  content: ReactNode;
  showInitial?: boolean;
  initialTimeout?: number;
  onClose?: () => void;
}

function ConductorTooltip({
  title,
  content,
  children,
  placement,
  showInitial,
  initialTimeout = 1000,
  onClose,
}: ConductorTooltipProps) {
  const [open, setOpen] = useState(false);

  // A `display: contents` span wraps the entire tooltip so we can call
  // closest("legend") on it. MUI TextField renders the `label` prop twice:
  //   1. Inside the visible <InputLabel> (what the user sees)
  //   2. Inside a hidden <legend> in NotchedOutline (border-notch sizing only)
  // Both copies mount this component. The wrapper span is invisible to layout
  // but exists in the DOM tree, letting us skip auto-show for the legend copy.
  const wrapperRef = useRef<HTMLSpanElement>(null);

  const handleClose = () => {
    setOpen(false);
    if (onClose) {
      onClose();
    }
  };
  const handleOpen = (value: boolean) => {
    setOpen(value);
  };

  useEffect(() => {
    let timeoutId: ReturnType<typeof setTimeout> | undefined;
    if (showInitial) {
      // wrapperRef.current is set during the commit phase, before effects run.
      // If it lives inside a <legend> this is the hidden sizing copy — skip
      // so only the visible label instance opens.
      if (wrapperRef.current?.closest("legend")) {
        return;
      }
      // eslint-disable-next-line @eslint-react/hooks-extra/no-direct-set-state-in-use-effect
      setOpen(true);
      timeoutId = setTimeout(() => {
        handleClose();
      }, initialTimeout);
    }
    return () => clearTimeout(timeoutId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialTimeout]);

  return (
    // display:contents makes this span invisible to layout while keeping it
    // in the DOM tree so wrapperRef can call closest("legend").
    <span ref={wrapperRef} style={{ display: "contents" }}>
      <TooltipStateless
        title={title}
        content={content}
        placement={placement}
        open={open}
        handleOpen={handleOpen}
        handleClose={handleClose}
      >
        {children}
      </TooltipStateless>
    </span>
  );
}

export default ConductorTooltip;
export type { ConductorTooltipProps };
