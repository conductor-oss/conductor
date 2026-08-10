import { TagDto } from "./Tag";

export interface EnvironmentVariables {
  name: string;
  value: string;
  tags: TagDto[];
}
