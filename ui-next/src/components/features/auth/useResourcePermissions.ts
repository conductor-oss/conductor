import { fetchWithContext, useFetchContext } from "plugins/fetch";
import { useAuthHeaders } from "utils/query";
import { useQuery } from "react-query";
import { ResourcePermissionFlags } from "utils/accessControl";

const NONE_FLAGS: ResourcePermissionFlags = {
  canCreate: false,
  canRead: false,
  canUpdate: false,
  canDelete: false,
  canExecute: false,
};

const STALE_TIME = 5 * 60 * 1000; // 5 minutes — capabilities change only on role/permission edits

/**
 * Fetches the calling user's global capability flags for a resource type from the backend.
 *
 * Calls GET /api/resource/capabilities/{resourceKey} which checks all the user's roles,
 * expands bundled permissions (e.g. PROMPT_MANAGEMENT → canCreate, canRead, …), and
 * returns authoritative flags without requiring the frontend to replicate permission logic.
 *
 * Per-instance write hints ({ update, delete }) are embedded in list/GET responses for
 * individual resources — see {@link InstanceCapabilities} on types such as PromptDef.
 *
 * Result is cached per resource type for 5 minutes.
 *
 * @example
 * const { canCreate, canRead } = useResourcePermissions("PROMPT");
 * <Button disabled={!canCreate}>Add AI Prompt</Button>
 */
export function useResourcePermissions(
  resourceKey: string,
): ResourcePermissionFlags {
  const fetchContext = useFetchContext();
  const authHeaders = useAuthHeaders();

  const { data } = useQuery<ResourcePermissionFlags>(
    [fetchContext.stack, `/resource/capabilities/${resourceKey}`],
    () =>
      fetchWithContext(`/resource/capabilities/${resourceKey}`, fetchContext, {
        headers: authHeaders,
      }),
    {
      enabled: fetchContext.ready,
      staleTime: STALE_TIME,
      // Show no-access while loading to avoid briefly enabling restricted actions
      placeholderData: NONE_FLAGS,
    },
  );

  return data ?? NONE_FLAGS;
}
