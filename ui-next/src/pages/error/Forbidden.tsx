import { useAuth } from "components/features/auth"; // TODO missing error page
import Error from "components/ui/Error";
import useInterval from "utils/useInterval";
import { tryToJson } from "utils/utils";
import { ParsedErrorMessage } from "./types";

export interface ForbiddenProps {
  parsedMessage: ParsedErrorMessage;
}

export default function Forbidden({ parsedMessage }: ForbiddenProps) {
  const { solveExpireToken } = useAuth();

  // checking if client id is changed
  useInterval(() => {
    let invalidClientId = false;

    Object.entries(localStorage).forEach(([key, value]) => {
      // checking auth0 key
      const parsedValue = tryToJson(value);
      if (key.startsWith("@@auth0spajs@@") && parsedValue !== undefined) {
        const auth0Value = parsedValue;

        // @ts-ignore
        if (auth0Value.body?.client_id !== window?.authConfig?.clientId) {
          localStorage.removeItem(key);

          // re-fetch token
          solveExpireToken();
          invalidClientId = true;
        }
      }
    });

    if (invalidClientId) {
      window.location.replace("/");
    }
  }, 1000);

  return (
    <Error
      title={parsedMessage.code}
      description={parsedMessage.description}
      buttonText={parsedMessage.buttonText}
      onClick={parsedMessage.onClick}
    />
  );
}
