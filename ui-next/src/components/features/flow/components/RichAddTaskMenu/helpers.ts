import { BaseTaskMenuItem, RichAddMenuTabs } from "./state/types";

export const itemMatchesSelectedTask = (
  item: BaseTaskMenuItem,
  selectedTab: RichAddMenuTabs,
) => selectedTab === RichAddMenuTabs.ALL_TAB || selectedTab === item.category;

export const itemNameIncludesText = (
  item: BaseTaskMenuItem,
  searchQuery: string,
) => {
  const query = searchQuery?.toLowerCase();
  return (
    item?.name?.toLowerCase()?.includes(query) ||
    item?.type?.toLowerCase()?.includes(query)
  );
};

export const itemFilterMatcher =
  (searchQuery: string, selectedTab: RichAddMenuTabs) =>
  (item: BaseTaskMenuItem) =>
    itemMatchesSelectedTask(item, selectedTab) &&
    itemNameIncludesText(item, searchQuery);

interface JSONSchemaProperty {
  type: string;
  properties?: Record<string, JSONSchemaProperty>;
  items?: JSONSchemaProperty;
  required?: string[];
  enum?: any[];
  default?: any;
  minimum?: number;
  maximum?: number;
  description?: string;
  additionalProperties?: boolean;
  $schema?: string;
}

interface JSONSchema extends JSONSchemaProperty {
  type: string;
  properties?: Record<string, JSONSchemaProperty>;
  required?: string[];
}

export const generateObjectFromSchema = (schema: JSONSchema): any => {
  if (!schema?.type) {
    return undefined;
  }

  switch (schema?.type) {
    case "object": {
      if (!schema.properties) {
        return {};
      }

      const obj: Record<string, any> = {};
      Object.entries(schema?.properties || {}).forEach(([key, prop]) => {
        if (prop?.default !== undefined) {
          obj[key] = prop?.default;
        } else if (prop?.enum && prop?.enum?.length > 0) {
          obj[key] = prop?.enum[0];
        } else if (prop?.type === "integer" || prop?.type === "number") {
          // Handle minimum/maximum constraints
          if (prop?.minimum !== undefined) {
            obj[key] = prop?.minimum;
          } else if (prop?.maximum !== undefined) {
            obj[key] = Math.min(
              prop?.maximum,
              prop?.type === "integer" ? 1 : 1.0,
            );
          } else {
            obj[key] = prop?.type === "integer" ? 1 : 1.0;
          }
        } else {
          obj[key] = generateObjectFromSchema(prop as JSONSchema);
        }
      });
      return obj;
    }
    case "array": {
      if (!schema?.items) {
        return [];
      }
      return [generateObjectFromSchema(schema?.items as JSONSchema)];
    }
    case "string": {
      return "";
    }
    case "integer": {
      return schema?.minimum !== undefined ? schema?.minimum : 1;
    }
    case "number": {
      return schema?.minimum !== undefined ? schema?.minimum : 1.0;
    }
    case "boolean": {
      return false;
    }
    case "null": {
      return null;
    }
    default: {
      return undefined;
    }
  }
};
