import Button, { MuiButtonProps } from "components/MuiButton";

interface CustomButtonProps extends MuiButtonProps {
  customVariant?: string;
  height?: string;
}

const CustomButton = ({
  customVariant,
  height,
  ...props
}: CustomButtonProps) => {
  const commonStyle = {
    border: `1px solid`,
    color: "#060606",
    borderRadius: "6px",
    ...(height ? { height: height } : {}),
  };
  const primaryStyles = {
    backgroundColor: "#C8ABFF",
    borderColor: "#9157FF",
    ":hover": {
      backgroundColor: "#C8ABFF",
    },
  };
  const secondaryStyles = {
    backgroundColor: "transparent",
    borderColor: "#161616",
    ":hover": {
      backgroundColor: "transparent",
    },
  };
  const variantStyle = () => {
    switch (customVariant) {
      case "primary":
        return primaryStyles;
      case "secondary":
        return secondaryStyles;
      default:
        return primaryStyles;
    }
  };

  return <Button {...props} sx={{ ...commonStyle, ...variantStyle() }} />;
};

export type { CustomButtonProps };
export default CustomButton;
