import { AccessRole } from "types/User";

export interface UserInfo {
  roles?: AccessRole[];
  groups?: any[];
}

const hasAnyRole = (
  userInfo: UserInfo | undefined | null,
  allowedRoles: string[],
) => {
  if (!userInfo) {
    return false;
  }

  const hasAllowedRoles = (roles?: any[]) =>
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
  hasUserManagement: (userInfo?: UserInfo) => {
    return hasAnyRole(userInfo, ["ADMIN"]);
  },
  hasApplicationManagement: (userInfo?: UserInfo) => {
    return hasAnyRole(userInfo, ["USER", "ADMIN"]);
  },
  hasOnlyReadOnlyAccess: (userInfo?: UserInfo) => {
    if (
      hasAnyRole(userInfo, [
        "ADMIN",
        "USER",
        "METADATA_MANAGER",
        "WORKFLOW_MANAGER",
      ])
    ) {
      return false;
    }
    return hasAnyRole(userInfo, ["USER_READ_ONLY"]);
  },
  hasAnyRole,
};

export enum Role {
  ADMIN = "ADMIN",
  USER = "USER",
  METADATA_MANAGER = "METADATA_MANAGER",
  WORKFLOW_MANAGER = "WORKFLOW_MANAGER",
  USER_READ_ONLY = "USER_READ_ONLY",
}
