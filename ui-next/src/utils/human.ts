import { JsonSchema, ControlElement } from "@jsonforms/core";
import _path from "lodash/fp/path";
import _last from "lodash/last";
import _isEmpty from "lodash/isEmpty";
import { HumanTemplate } from "types/HumanTaskTypes";
import { logger } from "utils";

type TypeAndFieldName = { type: string; fieldName: string; path: string };

export const extractFieldTypeAndName = (
  jsonSchema: JsonSchema,
  uiTemplate: ControlElement,
): TypeAndFieldName | undefined => {
  const path = uiTemplate.scope.substring(2).replaceAll("/", ".");
  const fieldName = _last(path.split("."));
  const type: string = _path(path + ".type", jsonSchema);
  if ([type, fieldName, path].some((a) => a == null)) {
    return undefined;
  }

  return {
    type,
    fieldName: fieldName!,
    path,
  };
};

export const enumValuesForField = (path: string, jsonSchema: JsonSchema) =>
  _path(`${path}.enum`, jsonSchema);

type TemplateByNameRow = Record<string, HumanTemplate[]>;
type TemplateById = Record<string, HumanTemplate>;

export const groupedByTemplates = (
  templates: HumanTemplate[],
): [TemplateByNameRow, TemplateById] => {
  if (_isEmpty(templates)) return [{}, {}];
  const [templateByName, templateByIdAcc]: [TemplateByNameRow, TemplateById] =
    templates.reduce(
      (
        accL: [TemplateByNameRow, TemplateById],
        template: HumanTemplate,
      ): [TemplateByNameRow, TemplateById] => {
        const templateByNameAcc = accL[0];
        const templateByIdAcc = accL[1];
        if (!templateByNameAcc[template.name]) {
          templateByNameAcc[template.name] = [];
        }
        templateByNameAcc[template.name].push(template);
        templateByIdAcc[template.name] = template;
        return [templateByNameAcc, templateByIdAcc];
      },
      [{}, {}],
    );
  return [templateByName, templateByIdAcc];
};

export const templatesToGroupedSingleTemplates = (
  templates: HumanTemplate[],
): [HumanTemplate[], TemplateByNameRow, TemplateById] => {
  if (_isEmpty(templates)) return [[], {}, {}];
  const [templateByName, templateByIdAcc]: [TemplateByNameRow, TemplateById] =
    groupedByTemplates(templates);

  const dataWithLatestVersion = Object.entries(templateByName).map(
    ([, versions]: [string, HumanTemplate[]]) =>
      versions.reduce((acc: HumanTemplate, curr: HumanTemplate) => {
        return acc?.version > curr.version ? acc : curr;
      }),
  );
  return [dataWithLatestVersion, templateByName, templateByIdAcc];
};

const defaultValueForType = (type: string) => {
  switch (type) {
    case "number":
    case "integer":
      return 0;
    case "boolean":
      return false;
    default:
      return "";
  }
};

export const extractTemplatePropertiesSetDefaultValues = (
  humanTemplate: HumanTemplate | undefined,
): Record<string, unknown> => {
  if (humanTemplate?.jsonSchema?.properties !== undefined) {
    const { jsonSchema } = humanTemplate;
    return Object.fromEntries(
      Object.entries(jsonSchema?.properties || {}).map(([key, value]) => [
        key,
        defaultValueForType(value.type),
      ]),
    );
  }
  logger.info("No properties found in template");
  return {};
};
