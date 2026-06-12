export interface Auth0User {
  given_name?: string;
  family_name?: string;
  nickname?: string;
  name?: string;
  picture?: string;
  locale?: string;
  updated_at?: string;
  email?: string;
  email_verified?: boolean;
  sub?: string;
}
export interface OktaUser {
  sub: string;
  name: string;
  locale: string;
  email: string;
  preferred_username: string;
  given_name: string;
  family_name: string;
  zoneinfo: string;
  updated_at: number;
  email_verified: boolean;
}

export interface AccessPermission {
  name: string;
}

export interface AccessRole {
  name: string;
  permissions?: AccessPermission[];
}

export interface AccessGroup {
  id: string;
  description: string;
  roles: AccessRole[];
  defaultAccess: unknown; // TODO fixme
}

export interface User {
  applicationUser: boolean;
  groups: AccessGroup[];
  id: string;
  name: string;
  roles: AccessRole[];
  uuid: string;
}

export interface UserContext {
  user: Partial<User>;
  accessToken?: string;
  isAuthenticated: boolean;
}
