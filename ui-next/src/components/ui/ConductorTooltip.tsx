import TooltipStateless from "components/ui/TooltipStateless";
import { TooltipProps } from "@mui/material";
import { ReactNode, useEffect, useState } from "react";

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
    let timeoutId: any;
    if (showInitial) {
      setOpen(true);
      timeoutId = setTimeout(() => {
        handleClose();
      }, initialTimeout);
    }
    return () => clearTimeout(timeoutId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialTimeout]);

  return (
    <TooltipStateless
      title={title}
      content={content}
      children={children}
      placement={placement}
      open={open}
      handleOpen={handleOpen}
      handleClose={handleClose}
    />
  );
}

export default ConductorTooltip;
export type { ConductorTooltipProps };
