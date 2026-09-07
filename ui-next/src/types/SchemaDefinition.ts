import { JsonSchema } from "@jsonforms/core";

/**
 * The three types the registry stores. The wire carries all three, so this has
 * to name all three — but only JSON is validated, and the schema editor only
 * offers JSON.
 */
export type SchemaType = "JSON" | "AVRO" | "PROTOBUF";

export type SchemaDefinition = {
  name: string;
  version: number;
  data: JsonSchema;
  type: SchemaType;
  /**
   * A schema held outside Conductor. Stored and returned as submitted; nothing
   * dereferences it.
   */
  externalRef?: string;
  /**
   * Audit fields are optional: a server with no authenticated principal never
   * sets createdBy or updatedBy, and the shortened listing a picker asks for
   * omits the timestamps.
   */
  createdBy?: string;
  updatedBy?: string;
  createTime?: number;
  updateTime?: number;
};
