import { AccessRole } from "types/User";

export interface UserInfo {
  roles?: AccessRole[];
  groups?: { roles?: AccessRole[] }[];
}

/**
 * Base resource-type keys available in conductor-oss.
 * Extended in conductor-ui's pages/access/roles/permissions.ts with additional keys.
 */
export const ResourceKey = {
  WORKFLOW_DEF: "WORKFLOW_DEF",
  TASK_DEF: "TASK_DEF",
  WORKFLOW_SCHEDULE: "WORKFLOW_SCHEDULE",
  EVENT_HANDLER: "EVENT_HANDLER",
} as const;

/** Valid resource-type key — either an OSS base key or an extension from the consuming app. */
export type ResourceKey = string;

/** CRUD+Execute capability flags for a resource type. */
export interface ResourcePermissionFlags {
  canCreate: boolean;
  canRead: boolean;
  canUpdate: boolean;
  canDelete: boolean;
  canExecute: boolean;
}

/**
 * Per-instance write capability hints on list/GET resource responses.
 * Global create/read flags come from GET /api/resource/capabilities/{resourceKey}.
 */
export interface InstanceCapabilities {
  update?: boolean | null;
  delete?: boolean | null;
}

const hasAnyRole = (
  userInfo: UserInfo | undefined | null,
  allowedRoles: string[],
) => {
  if (!userInfo) {
    return false;
  }

  const hasAllowedRoles = (roles?: AccessRole[]) =>
    roles?.find((role) => allowedRoles.includes(role.name));

  if (hasAllowedRoles(userInfo.roles)) {
    return true;
  }

  if (userInfo.groups?.find((group) => hasAllowedRoles(group.roles))) {
    return true;
  }

  return false;
};

export const accessControl = {
  hasAnyRole,
};

export enum Role {
  ADMIN = "ADMIN",
  USER = "USER",
  METADATA_MANAGER = "METADATA_MANAGER",
  WORKFLOW_MANAGER = "WORKFLOW_MANAGER",
  USER_READ_ONLY = "USER_READ_ONLY",
}
