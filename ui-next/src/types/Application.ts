import { Tag } from "./common";
export interface Application {
  id: string;
  name: string;
  createdBy: string;
  updatedBy: string;
  createTime: number;
  updateTime: number;
  tags: Tag[];
}
