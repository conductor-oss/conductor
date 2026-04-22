/**
 * Auth hook. Reads from AuthContext: when authState is provided (e.g. by enterprise),
 * returns it; otherwise returns stub values plus authService from context.
 * Shared components (UserInfo, Sidebar, etc.) use this so OSS and enterprise share one contract.
 */
import { useContext } from "react";
import { AuthContext } from "./context";
import { defaultAuthState } from "./types";

export const useAuth = () => {
  const { authService, authState } = useContext(AuthContext);
  if (authState != null) return authState;
  return { ...defaultAuthState, authService } as const;
};
