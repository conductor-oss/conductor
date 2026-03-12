import {
  roleAdmin,
  roleMetaManager,
  roleReadOnly,
  roleUser,
  roleWfManager,
} from "theme/tokens/colors";
import { AccessRole } from "types/User";
import { Role } from "utils/accessControl";

export const roleLabel: { [key: string]: string } = {
  [Role.ADMIN]: "Admin",
  [Role.USER]: "User",
  [Role.METADATA_MANAGER]: "Metadata manager",
  [Role.WORKFLOW_MANAGER]: "Workflow manager",
  [Role.USER_READ_ONLY]: "Read only user",
};

export const userRoleColorGenerator = (role: string) => {
  let tagColor;
  if (role === Role.ADMIN) {
    tagColor = roleAdmin;
  } else if (role === Role.USER) {
    tagColor = roleUser;
  } else if (role === Role.WORKFLOW_MANAGER) {
    tagColor = roleWfManager;
  } else if (role === Role.METADATA_MANAGER) {
    tagColor = roleMetaManager;
  } else {
    tagColor = roleReadOnly;
  }
  return { backgroundColor: tagColor };
};

export const sortRoles = (roles?: AccessRole[]) =>
  (roles ?? []).sort((a: { name: string }, b: { name: string }) => {
    if (a.name < b.name) {
      return -1;
    }
    if (a.name > b.name) {
      return 1;
    }
    return 0;
  });
