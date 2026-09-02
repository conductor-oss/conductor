import { JsonSchema, ControlElement } from "@jsonforms/core";
import { HumanTemplate } from "types/HumanTaskTypes";
type TypeAndFieldName = {
    type: string;
    fieldName: string;
    path: string;
};
export declare const extractFieldTypeAndName: (jsonSchema: JsonSchema, uiTemplate: ControlElement) => TypeAndFieldName | undefined;
export declare const enumValuesForField: (path: string, jsonSchema: JsonSchema) => any;
type TemplateByNameRow = Record<string, HumanTemplate[]>;
type TemplateById = Record<string, HumanTemplate>;
export declare const groupedByTemplates: (templates: HumanTemplate[]) => [TemplateByNameRow, TemplateById];
export declare const templatesToGroupedSingleTemplates: (templates: HumanTemplate[]) => [HumanTemplate[], TemplateByNameRow, TemplateById];
export declare const extractTemplatePropertiesSetDefaultValues: (humanTemplate: HumanTemplate | undefined) => Record<string, unknown>;
export {};
