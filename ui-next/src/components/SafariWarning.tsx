import { SnackbarMessage } from "components/ui/SnackbarMessage";
import { isSafari } from "utils";

interface SafariWarningProps {
  setShowSafariWarning: (show: boolean) => void;
}

export const SafariWarning = ({ setShowSafariWarning }: SafariWarningProps) => {
  if (isSafari) {
    return (
      <SnackbarMessage
        message="For the time being, Safari is unsupported. Please try using Chrome, Edge, or Firefox instead."
        severity="info"
        onDismiss={() => {
          setShowSafariWarning(false);
        }}
      />
    );
  }
};
