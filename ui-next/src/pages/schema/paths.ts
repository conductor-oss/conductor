import { generatePath } from "react-router";

import { SCHEMAS_URL } from "utils/constants/route";

/** The URL segment that stands for "a schema that does not exist yet". */
export const NEW_SCHEMA_URL_PARAM = "schemaDef";

/**
 * The editor's URL. With no version it means "the latest", which is what the
 * version selector shows as its default. A new schema has no version either,
 * and reaches the same route under {@link NEW_SCHEMA_URL_PARAM}.
 */
export const schemaEditPath = (name: string, version?: number): string =>
  generatePath(SCHEMAS_URL.EDIT, {
    schemaName: name,
    version: version?.toString() ?? null,
  });
