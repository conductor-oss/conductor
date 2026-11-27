import { useCallback, useEffect, useState } from "react";
import { Box, Chip, CircularProgress, Typography } from "@mui/material";
import { Button } from "components";
import { DelegationRequirement } from "types/WorkflowDef";

interface AuthState {
  authorized: boolean;
  loading: boolean;
}

interface Props {
  delegations: DelegationRequirement[];
  onAuthStateChange: (allAuthorized: boolean) => void;
}

async function checkSecretExists(secretRef: string): Promise<boolean> {
  try {
    const res = await fetch(`/api/secrets/${encodeURIComponent(secretRef)}/exists`);
    if (!res.ok) return false;
    return await res.json();
  } catch {
    return false;
  }
}

async function fetchAuthorizationUrl(delegation: DelegationRequirement): Promise<string> {
  const params = new URLSearchParams({
    key: delegation.key,
    secretRef: delegation.secretRef,
    scopes: delegation.scopes.join(" "),
  });
  const res = await fetch(`/api/oauth/authorize?${params}`);
  if (!res.ok) throw new Error("Failed to get authorization URL");
  return res.text();
}

export function DelegatedAuthSection({ delegations, onAuthStateChange }: Props) {
  const [authStates, setAuthStates] = useState<Record<string, AuthState>>(() =>
    Object.fromEntries(delegations.map((d) => [d.key, { authorized: false, loading: true }])),
  );

  const checkAll = useCallback(async () => {
    const results = await Promise.all(
      delegations.map(async (d) => {
        const authorized = await checkSecretExists(d.secretRef);
        return { key: d.key, authorized };
      }),
    );
    const next: Record<string, AuthState> = {};
    for (const r of results) {
      next[r.key] = { authorized: r.authorized, loading: false };
    }
    setAuthStates(next);
    onAuthStateChange(results.every((r) => r.authorized));
  }, [delegations, onAuthStateChange]);

  useEffect(() => {
    checkAll();
  }, [checkAll]);

  useEffect(() => {
    const handler = (event: MessageEvent) => {
      if (event.data?.type !== "oauth-complete") return;
      if (event.data.success) {
        checkAll();
      }
    };
    window.addEventListener("message", handler);
    return () => window.removeEventListener("message", handler);
  }, [checkAll]);

  const handleAuthorize = async (delegation: DelegationRequirement) => {
    setAuthStates((prev) => ({
      ...prev,
      [delegation.key]: { ...prev[delegation.key], loading: true },
    }));
    try {
      const url = await fetchAuthorizationUrl(delegation);
      const popup = window.open(url, "oauth-popup", "width=600,height=700,noopener");
      if (!popup) {
        alert("Popup was blocked. Please allow popups for this site and try again.");
      }
    } catch {
      setAuthStates((prev) => ({
        ...prev,
        [delegation.key]: { authorized: false, loading: false },
      }));
    }
  };

  const handleDisconnect = async (delegation: DelegationRequirement) => {
    await fetch(`/api/secrets/${encodeURIComponent(delegation.secretRef)}`, { method: "DELETE" });
    setAuthStates((prev) => ({
      ...prev,
      [delegation.key]: { authorized: false, loading: false },
    }));
    onAuthStateChange(false);
  };

  if (!delegations.length) return null;

  return (
    <Box>
      <Typography
        variant="caption"
        sx={{ display: "block", fontWeight: 600, color: "#767676", mb: 1 }}
      >
        Required authorization
      </Typography>
      <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
        {delegations.map((delegation) => {
          const state = authStates[delegation.key] ?? { authorized: false, loading: false };
          const label = delegation.label ?? delegation.provider;

          return (
            <Box
              key={delegation.key}
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 1.5,
                padding: "8px 12px",
                border: "1px solid",
                borderColor: state.authorized ? "success.light" : "divider",
                borderRadius: "4px",
                backgroundColor: state.authorized ? "success.50" : "background.paper",
              }}
            >
              {state.loading ? (
                <CircularProgress size={16} />
              ) : state.authorized ? (
                <Chip label="Connected" color="success" size="small" />
              ) : (
                <Chip label="Not authorized" size="small" />
              )}

              <Typography variant="body2" sx={{ flex: 1 }}>
                {label}
              </Typography>

              {!state.loading && (
                state.authorized ? (
                  <Button
                    variant="text"
                    size="small"
                    onClick={() => handleDisconnect(delegation)}
                  >
                    Disconnect
                  </Button>
                ) : (
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={() => handleAuthorize(delegation)}
                  >
                    Authorize ▸
                  </Button>
                )
              )}
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}
