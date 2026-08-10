import { keyframes, styled } from "@mui/system";
import { IconProps } from "@phosphor-icons/react";
import { ForwardRefExoticComponent, RefAttributes } from "react";

const spin = keyframes`
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
`;
const iconStyle = ({ loading }: { loading: boolean }) => ({
  animation: loading ? `${spin} 1s forwards` : "none",
});

export const SpinningIcon = (
  Icon: ForwardRefExoticComponent<IconProps & RefAttributes<SVGSVGElement>>,
) => styled(Icon)(iconStyle);
