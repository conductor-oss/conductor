import { Box, Paper } from "@mui/material";
import MuiButton from "components/MuiButton";
import MuiTypography from "components/MuiTypography";
import { ErrorProps } from "components/v1/error/Error";
import { sidebarGrey } from "theme/tokens/colors";

export const UserNotFound = ({
  title,
  description,
  buttonText,
  onClick,
  errorLogo,
  secondaryButton,
}: ErrorProps) => {
  return (
    <Box
      sx={{
        flexGrow: 1,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <img src={errorLogo} alt="userNotFound" />
      <MuiTypography fontSize={33} fontWeight={700} textAlign="center" my={5}>
        {title}
      </MuiTypography>

      <Box sx={{ display: "flex", justifyContent: "center" }}>
        <Paper
          sx={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            width: "400px",
            pt: 6,
            pb: 4,
            px: 4,
            gap: 3,
            borderRadius: "6px",
          }}
        >
          <MuiTypography
            textAlign="center"
            fontWeight={300}
            fontSize={12}
            color={sidebarGrey}
          >
            {description}
          </MuiTypography>
          <MuiButton
            id="error-go-back-btn"
            variant="text"
            onClick={onClick}
            sx={{
              fontSize: 12,
              fontWeight: 500,
            }}
          >
            {buttonText}
          </MuiButton>
          {!!secondaryButton && (
            <MuiButton
              id="error-secondary-btn"
              variant="text"
              onClick={secondaryButton.onClick}
              sx={{
                fontSize: 12,
                fontWeight: 500,
              }}
            >
              {secondaryButton.buttonText}
            </MuiButton>
          )}
        </Paper>
      </Box>
    </Box>
  );
};
