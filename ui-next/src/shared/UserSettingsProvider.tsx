import { useInterpret } from "@xstate/react";
import { FunctionComponent, ReactNode, useMemo } from "react";
import { userSettingsMachine } from "./state/userSettingsMachine";
import { UserSettingsContext } from "./UserSettingsContext";

interface UserSettingsProviderProps {
  children: ReactNode;
}

export const UserSettingsProvider: FunctionComponent<
  UserSettingsProviderProps
> = ({ children }) => {
  const service = useInterpret(userSettingsMachine);

  const value = useMemo(() => ({ userSettingsService: service }), [service]);

  return (
    <UserSettingsContext.Provider value={value}>
      {children}
    </UserSettingsContext.Provider>
  );
};
