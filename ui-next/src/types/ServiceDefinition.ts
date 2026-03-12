import { Tag } from "./common";

export type ServiceDefinition = {
  name: string;
  type: string;
  location: string;
  createdBy: string;
  updatedBy: string;
  createTime: number;
  updateTime: number;
  tags: Tag[];
};
