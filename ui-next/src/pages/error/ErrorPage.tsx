import Box from "@mui/material/Box";
import MuiTypography from "components/ui/MuiTypography";
import Error from "components/ui/Error";
import EmailNotVerifiedSvg from "images/svg/email-not-verified.svg";
import UserNotFoundSvg from "images/svg/user-not-found.svg";
import { useCallback, useMemo } from "react";
import { Helmet } from "react-helmet";
import { useQueryState } from "react-router-use-location-state";
import { useAuth } from "components/features/auth";
import {
  silentlyRefreshToken,
  hasRefreshPermanentlyFailed,
} from "components/features/auth/silentRefresh";
import { canRefreshToken } from "components/features/auth/tokenManagerJotai";
import { HttpStatusCode } from "utils/constants/httpStatusCode";
import { useErrorMonitoring } from "utils/monitoring";
import Forbidden from "./Forbidden";
import type { ParsedErrorMessage } from "./types";
import { UserNotFound } from "./UserNotFound";

// @ts-ignore
const isUsingOkta = ["okta", "oidc"].includes(window?.authConfig?.type);

const parseMessage = ({
  code,
  error,
  message,
  onDone,
}: {
  code: string;
  error: string;
  message: string;
  onDone: () => void;
  logOut: () => void;
}): ParsedErrorMessage => {
  if (error === "EMAIL_NOT_VERIFIED") {
    return {
      code: "Please verify your email",
      description: "I have already verified my email.",
      title: "",
      onClick: onDone,
      buttonText: "Back To Login",
      errorLogo: EmailNotVerifiedSvg,
    };
  }

  if (error === "EXPIRED_TOKEN") {
    return {
      code: "EXPIRED TOKEN",
      description: "Your session has expired.",
      title: "EXPIRED TOKEN",
      onClick: onDone,
      buttonText: "REFRESH SESSION",
    };
  }

  if (error === "USER_NOT_FOUND") {
    return {
      code: "Account does not exist",
      description:
        "Please notify your Orkes Cluster Admin to set up an account",
      title: "User not found",
      onClick: onDone,
      buttonText: "Retry Login",
      errorLogo: UserNotFoundSvg,
    };
  }

  switch (Number(code)) {
    case HttpStatusCode.Unauthorized: {
      if (!isUsingOkta) {
        return {
          code,
          description:
            "Please logout and log back in. If the error persists, please clear your cache or force refresh the login page.",
          title: "ERROR",
        };
      }

      return {
        code: "ACCESS DENIED",
        description:
          "This looks like a permission issue. Please contact your administrator for access.",
        title: "ACCESS DENIED",
      };
    }

    case HttpStatusCode.Forbidden:
      return {
        code: "ACCESS DENIED",
        description:
          "0AuthError: User is not assigned to the client application. Please contact your administrator.",
        title: "ACCESS DENIED",
      };

    case HttpStatusCode.NotFound:
      return {
        code,
        description: "We're sorry but we couldn't locate that page.",
        title: "ERROR",
      };

    default: {
      return {
        code,
        description:
          message ||
          "Not sure what happened, but let's try again. If that doesn't work, let's restart the browser.",
        title: "ERROR",
      };
    }
  }
};

export default function ErrorPage() {
  const { solveExpireToken, logOut, oidcConfig } = useAuth();
  // const { useIdToken, acquireToken, setToken } = useAuth() as {
  //   useIdToken: string;
  //   acquireToken: (b: boolean) => void;
  //   setToken: (token?: string | null) => void;
  // };
  const { notifyError } = useErrorMonitoring();

  const [code] = useQueryState("code", `${HttpStatusCode.NotFound}`);
  const [error] = useQueryState("error", "");
  const [message] = useQueryState("message", "");

  notifyError("Unauthorized", { error, message });

  const onDone = useCallback(async () => {
    // if (useIdToken && acquireToken) {
    //   await acquireToken(true);
    // }
    //
    // if (setToken) {
    //   // we set the token to null to force the OIDC flow to get a new token
    //   setToken(null);
    // }

    // For 401 errors, try silent refresh first if possible
    if (Number(code) === HttpStatusCode.Unauthorized) {
      if (canRefreshToken() && !hasRefreshPermanentlyFailed()) {
        const refreshed = await silentlyRefreshToken(oidcConfig);

        if (refreshed) {
          window.location.replace("/");
          return;
        }
      }
    }

    solveExpireToken();
    window.location.replace("/");
  }, [solveExpireToken, code, oidcConfig]);

  const parsedMessage = parseMessage({ code, error, message, onDone, logOut });

  const ErrorComponent = useMemo(() => {
    return () => {
      if (
        error === "EXPIRED_TOKEN" ||
        Number(code) === HttpStatusCode.Forbidden
      ) {
        return <Forbidden parsedMessage={parsedMessage} />;
      } else if (error === "USER_NOT_FOUND" || error === "EMAIL_NOT_VERIFIED") {
        return (
          <UserNotFound
            title={parsedMessage.code}
            description={parsedMessage.description}
            buttonText={parsedMessage.buttonText}
            onClick={logOut}
            errorLogo={parsedMessage.errorLogo}
            secondaryButton={parsedMessage.secondaryButton}
          />
        );
      } else {
        return (
          <Error
            title={parsedMessage.code}
            description={parsedMessage.description}
            buttonText={parsedMessage.buttonText}
            onClick={parsedMessage.onClick}
            error={error}
          />
        );
      }
    };
  }, [code, error, logOut, parsedMessage]);

  const pageTitle = useMemo(() => {
    if (error === "EMAIL_NOT_VERIFIED") {
      return "Email Not Verified";
    }
    if (error === "USER_NOT_FOUND") {
      return "User Not Found";
    }
    return "Error";
  }, [error]);

  return (
    <Box
      id="error-page"
      sx={{
        display: "flex",
        flexDirection: "column",
        height: "100%",
        p: 5,
      }}
    >
      <Helmet>
        <title>{pageTitle}</title>
      </Helmet>
      {error === "USER_NOT_FOUND" ? (
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 8 }}>
          <MuiTypography fontSize={20} fontWeight={700}>
            401:
          </MuiTypography>
          <MuiTypography fontSize={20}>{parsedMessage.title}</MuiTypography>
        </Box>
      ) : (
        <MuiTypography fontSize={20} fontWeight={700} mb={8}>
          {parsedMessage.title}
        </MuiTypography>
      )}

      <ErrorComponent />
    </Box>
  );
}
