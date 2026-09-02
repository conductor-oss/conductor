import { AccessRole } from "types/User";
export declare const roleLabel: {
    [key: string]: string;
};
export declare const displayRoleName: (role: string) => string;
export declare const userRoleColorGenerator: (role: string) => {
    backgroundColor: string;
};
export declare const sortRoles: (roles?: AccessRole[]) => AccessRole[];
