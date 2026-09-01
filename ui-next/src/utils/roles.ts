import {
  roleAdmin,
  roleCustom,
  roleEventHandlerManager,
  roleHumanTaskManager,
  roleMetaManager,
  roleReadOnly,
  roleScheduleManager,
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
  [Role.HUMAN_TASK_MANAGER]: "Human task manager",
  [Role.EVENT_HANDLER_MANAGER]: "Event handler manager",
  [Role.SCHEDULE_MANAGER]: "Schedule manager",
  [Role.USER_READ_ONLY]: "Read only user",
};

export const displayRoleName = (role: string) => roleLabel[role] || role;

const roleColor: { [key: string]: string } = {
  [Role.ADMIN]: roleAdmin,
  [Role.USER]: roleUser,
  [Role.WORKFLOW_MANAGER]: roleWfManager,
  [Role.METADATA_MANAGER]: roleMetaManager,
  [Role.USER_READ_ONLY]: roleReadOnly,
  [Role.HUMAN_TASK_MANAGER]: roleHumanTaskManager,
  [Role.EVENT_HANDLER_MANAGER]: roleEventHandlerManager,
  [Role.SCHEDULE_MANAGER]: roleScheduleManager,
};

export const userRoleColorGenerator = (role: string) => ({
  backgroundColor: roleColor[role] ?? roleCustom,
});

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
