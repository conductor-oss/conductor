import { Box } from "@mui/material";
import Button from "components/ui/buttons/MuiButton";
import MuiTypography from "components/ui/MuiTypography";

export default function ThemeSampler() {
  const variants = ["contained", "solid", "outlined"];
  const colors = ["primary", "secondary", "success", "warning", "error"];
  const sizes = ["small", "medium", "large"];

  const variantColorsSizes = variants.reduce((acc, variant) => {
    colors.forEach((color) => {
      sizes.forEach((size) => {
        acc.push({ variant, color, size });
      });
    });
    return acc;
  }, []);

  return (
    <Box
      sx={{
        display: "flex",
        gap: 2,
        padding: 8,
        flexDirection: "column",
      }}
    >
      <Box>
        <MuiTypography variant="h2" marginBottom="24px">
          Buttons
        </MuiTypography>
        <Box
          sx={{
            display: "flex",
            gap: 2,
            flexDirection: "column",
            flexGrow: 0,
            width: "fit-content",
          }}
        >
          <Box marginBottom={6}>
            <Button>Default</Button>
            <MuiTypography marginTop="4px" color="#555555">
              Default (no props) is: color = primary, variant = contained, size
              = medium
            </MuiTypography>
          </Box>
          {variantColorsSizes.map((variantColorSize) => {
            return (
              <Box
                key={`${variantColorSize.variant}-${variantColorSize.color}-${variantColorSize.size}`}
              >
                <Button
                  variant={variantColorSize.variant}
                  color={variantColorSize.color}
                  size={variantColorSize.size}
                  sx={{
                    textTransform: "capitalize",
                  }}
                >
                  {`${variantColorSize.variant} ${variantColorSize.color} ${variantColorSize.size}`}
                </Button>
              </Box>
            );
          })}

          {/* <Box>
            <Button color="secondary">Secondary</Button>
            <Typography marginTop={1} color="#555555">
              color="secondary"
            </Typography>
          </Box>
          <Box>
            <Button color="success">Success</Button>
            <Typography marginTop={1} color="#555555">
              color="secondary"
            </Typography>
          </Box> */}
        </Box>
      </Box>
    </Box>
  );
}
