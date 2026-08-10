import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import { Theme } from "@mui/material/styles";
import MuiButton from "components/ui/buttons/MuiButton";
import MuiTypography from "components/ui/MuiTypography";
import { useNavigate } from "react-router";
import { HttpStatusCode } from "utils/constants/httpStatusCode";
import { clear as clearTokens } from "components/features/auth/tokenManagerJotai";

export interface ErrorProps {
  title: string;
  description: string;
  buttonText?: string;
  onClick?: () => void;
  errorLogo?: string;
  error?: string;
  secondaryButton?: {
    buttonText?: string;
    onClick?: () => void;
  };
}

export default function Error({
  title,
  description,
  buttonText = "GO BACK",
  onClick,
  error,
}: ErrorProps) {
  const navigate = useNavigate();

  const handleClick = () => {
    if (onClick) {
      onClick();
      return;
    }

    if (error === "INVALID_TOKEN") {
      // Clear all auth-related storage using token manager
      clearTokens(); // Clears tokens from memory and localStorage
      sessionStorage.clear(); // Clear OIDC state to prevent auth loop

      // Force reload to login page to ensure clean state
      window.location.href = "/";
      return;
    }

    // If there's no previous history, go to home
    if (navigate.length <= 1) {
      navigate("/");
    } else {
      navigate(-1);
    }
  };

  return (
    <Box
      sx={{
        flexGrow: 1,
      }}
    >
      <MuiTypography
        fontSize={Number(title) > HttpStatusCode.BadRequest ? 200 : 50}
        fontWeight={700}
        textAlign="center"
        mt={20}
        mb={5}
      >
        {title}
      </MuiTypography>

      <Box sx={{ display: "flex", justifyContent: "center" }}>
        <Box
          sx={(theme: Theme) => ({
            background: theme.palette.background.paper,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            minHeight: "150px",
            width: "340px",
            py: 2,
            px: 4,
            gap: 3,
            borderRadius: "6px",
          })}
        >
          <Chip
            sx={{
              background: (theme) => theme.palette.pink.main,
              padding: "7px",
              borderRadius: "100px",
              fontWeight: 500,
              fontSize: "12px",
            }}
            label={"Error"}
          />

          <MuiTypography textAlign="center" fontWeight={300} fontSize={12}>
            {description}
          </MuiTypography>
          <MuiButton
            id="error-go-back-btn"
            variant="text"
            onClick={handleClick}
            sx={{
              fontSize: 12,
              fontWeight: 700,
              color: (theme) => theme.palette.primary.main,
            }}
          >
            {buttonText}
          </MuiButton>
        </Box>
      </Box>
    </Box>
  );
}
