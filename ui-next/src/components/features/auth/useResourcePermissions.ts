import { ResourcePermissionFlags } from "utils/accessControl";

const ALL_FLAGS: ResourcePermissionFlags = {
  canCreate: true,
  canRead: true,
  canUpdate: true,
  canDelete: true,
  canExecute: true,
};

/**
 * OSS stub — returns full access. Enterprise conductor-ui overrides this module
 * with a real implementation that calls GET /api/resource/capabilities/{resourceKey}.
 */
export function useResourcePermissions(
  _resourceKey: string,
): ResourcePermissionFlags {
  return ALL_FLAGS;
}
