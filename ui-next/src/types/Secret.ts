import { TagDto } from "./Tag";

export interface SecretDTO {
  name: string;
  value: string;
  tags?: TagDto[];
}
