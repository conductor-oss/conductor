import { AccessRole } from "types/User";
export interface UserInfo {
    roles?: AccessRole[];
    groups?: any[];
}
export declare const accessControl: {
    hasUserManagement: (userInfo?: UserInfo) => boolean;
    hasApplicationManagement: (userInfo?: UserInfo) => boolean;
    hasOnlyReadOnlyAccess: (userInfo?: UserInfo) => boolean;
    hasAnyRole: (userInfo: UserInfo | undefined | null, allowedRoles: string[]) => boolean;
};
export declare enum Role {
    ADMIN = "ADMIN",
    USER = "USER",
    METADATA_MANAGER = "METADATA_MANAGER",
    WORKFLOW_MANAGER = "WORKFLOW_MANAGER",
    HUMAN_TASK_MANAGER = "HUMAN_TASK_MANAGER",
    EVENT_HANDLER_MANAGER = "EVENT_HANDLER_MANAGER",
    SCHEDULE_MANAGER = "SCHEDULE_MANAGER",
    USER_READ_ONLY = "USER_READ_ONLY"
}
