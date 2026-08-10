import { User } from "types";

export const displayUserSubject = (user: User): string =>
  `${user.id} (${user.name})`;
