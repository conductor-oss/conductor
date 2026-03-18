import { JsonSchema } from "@jsonforms/core";
import { Tag } from "./common";

export type SchemaDefinition = {
  name: string;
  version: number;
  data: JsonSchema;
  type: "JSON";
  createdBy: string;
  updatedBy: string;
  createTime: number;
  updateTime: number;
  tags: Tag[];
};
