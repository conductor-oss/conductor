import { extractVariablesFromJSON, downgradeSchemaToDraft7 } from "utils/json";
import { isJSONSchemaValid } from "utils/jsonSchema";

const json = {
  uri: "${workflow.input.pepe}",
  method: "GET",
  connectionTimeOut: 3000,
  readTimeOut: "3000",
  accept: "application/json",
  contentType: "application/json",
};

const emptyJson = {
  uri: "https://orkes-api-tester.orkesconductor.com/api",
  method: "GET",
  connectionTimeOut: 3000,
  readTimeOut: "3000",
  accept: "application/json",
  contentType: "application/json",
};

const nestedJson = {
  uri: "${workflow.input.pepe}",
  method: "GET",
  headers: {
    accept: "${workflow.input.pepe1}",
  },
  connectionTimeOut: 3000,
  readTimeOut: "3000",
  accept: "application/json",
  contentType: "application/json",
};

describe("Extract json variables", () => {
  it("should return all variables from json", () => {
    const expected = { uri: "workflow.input.pepe" };
    expect(extractVariablesFromJSON(json)).toEqual(expected);
  });

  it("should return empty object if no variables present in the json", () => {
    expect(extractVariablesFromJSON(emptyJson)).toEqual({});
  });

  it("should return all variables from json with headers.accept", () => {
    const expected = {
      uri: "workflow.input.pepe",
      "headers.accept": "workflow.input.pepe1",
    };
    expect(extractVariablesFromJSON(nestedJson)).toEqual(expected);
  });
});

describe("downgradeSchemaToDraft7", () => {
  describe("input validation", () => {
    it("should return empty object for null input", () => {
      expect(downgradeSchemaToDraft7(null as any)).toEqual({});
    });

    it("should return empty object for undefined input", () => {
      expect(downgradeSchemaToDraft7(undefined as any)).toEqual({});
    });

    it("should return empty object for non-object input", () => {
      expect(downgradeSchemaToDraft7("string" as any)).toEqual({});
      expect(downgradeSchemaToDraft7(123 as any)).toEqual({});
      expect(downgradeSchemaToDraft7(true as any)).toEqual({});
    });

    it("should return array as-is for array input", () => {
      const array = [{ type: "string" }];
      expect(downgradeSchemaToDraft7(array as any)).toEqual(array);
    });
  });

  describe("schema version conversion", () => {
    it("should convert Draft 2019-09 schema to Draft 7", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2019-09/schema",
        type: "object",
        properties: {
          name: { type: "string" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
      expect(result.type).toBe("object");
      expect(result.properties).toEqual(schema.properties);
    });

    it("should convert Draft 2020-12 schema to Draft 7", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "string",
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
      expect(result.type).toBe("string");
    });

    it("should return Draft 7 schema as-is", () => {
      const schema = {
        $schema: "http://json-schema.org/draft-07/schema#",
        type: "object",
        properties: {
          name: { type: "string" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result).toEqual(schema);
      // Function returns original schema when no changes are needed (performance optimization)
      expect(result).toBe(schema);
    });

    it("should convert Draft 6 schema to Draft 7", () => {
      const schema = {
        $schema: "http://json-schema.org/draft-06/schema#",
        type: "string",
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
      expect(result.type).toBe("string");
      // Should be a copy, not the original
      expect(result).not.toBe(schema);
    });

    it("should convert Draft 4 schema to Draft 7", () => {
      const schema = {
        $schema: "http://json-schema.org/draft-04/schema#",
        type: "object",
        properties: {
          name: { type: "string" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
      expect(result.type).toBe("object");
      expect(result.properties).toEqual(schema.properties);
      // Should be a copy, not the original
      expect(result).not.toBe(schema);
    });

    it("should handle schema without $schema field but with newer keywords", () => {
      const schema = {
        type: "object",
        properties: {
          name: { type: "string" },
        },
        $defs: {
          address: { type: "object" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
      expect(result.type).toBe("object");
      expect(result.definitions).toBeDefined();
    });

    it("should add $schema to Draft 7 when missing, even with no newer keywords", () => {
      const schema = {
        type: "object",
        properties: {
          name: { type: "string" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      // Should add $schema for JsonForms compatibility even if no newer keywords
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
      expect(result.type).toBe("object");
      expect(result.properties).toEqual(schema.properties);
      // Should be a copy, not the original
      expect(result).not.toBe(schema);
    });
  });

  describe("$defs to definitions conversion", () => {
    it("should convert $defs to definitions", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        $defs: {
          address: {
            type: "object",
            properties: {
              street: { type: "string" },
            },
          },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.$defs).toBeUndefined();
      expect(result.definitions).toBeDefined();
      expect(result.definitions.address).toEqual(schema.$defs.address);
    });

    it("should merge $defs into existing definitions", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        definitions: {
          existing: { type: "string" },
        },
        $defs: {
          newDef: { type: "number" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.definitions.existing).toBeDefined();
      expect(result.definitions.newDef).toBeDefined();
      expect(result.$defs).toBeUndefined();
    });

    it("should process nested $defs recursively", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        $defs: {
          address: {
            type: "object",
            $defs: {
              nested: { type: "string" },
            },
          },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.definitions.address.definitions).toBeDefined();
      expect(result.definitions.address.definitions.nested).toBeDefined();
      expect(result.definitions.address.$defs).toBeUndefined();
    });
  });

  describe("$ref updates", () => {
    it("should update $ref from $defs to definitions", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        properties: {
          address: { $ref: "#/$defs/address" },
        },
        $defs: {
          address: { type: "object" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.properties.address.$ref).toBe("#/definitions/address");
      expect(result.definitions.address).toBeDefined();
    });

    it("should not modify $ref that doesn't reference $defs", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        properties: {
          address: { $ref: "#/definitions/address" },
        },
        definitions: {
          address: { type: "object" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.properties.address.$ref).toBe("#/definitions/address");
    });
  });

  describe("unsupported keywords removal", () => {
    it("should remove unevaluatedProperties", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        unevaluatedProperties: false,
        properties: {
          name: { type: "string" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.unevaluatedProperties).toBeUndefined();
      expect(result.properties).toBeDefined();
    });

    it("should remove unevaluatedItems", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "array",
        unevaluatedItems: false,
        items: { type: "string" },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.unevaluatedItems).toBeUndefined();
      expect(result.items).toBeDefined();
    });

    it("should remove dependentRequired", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        dependentRequired: {
          credit_card: ["billing_address"],
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.dependentRequired).toBeUndefined();
    });

    it("should remove dependentSchemas", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        dependentSchemas: {
          credit_card: { type: "object" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.dependentSchemas).toBeUndefined();
    });

    it("should remove $anchor, $dynamicAnchor, and $dynamicRef", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        $anchor: "myAnchor",
        $dynamicAnchor: "myDynamicAnchor",
        $dynamicRef: "#myDynamicAnchor",
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.$anchor).toBeUndefined();
      expect(result.$dynamicAnchor).toBeUndefined();
      expect(result.$dynamicRef).toBeUndefined();
    });

    it("should remove minContains and maxContains", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "array",
        contains: { type: "string" },
        minContains: 2,
        maxContains: 5,
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.minContains).toBeUndefined();
      expect(result.maxContains).toBeUndefined();
      expect(result.contains).toBeDefined();
    });
  });

  describe("nested schema processing", () => {
    it("should process nested properties", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        properties: {
          user: {
            type: "object",
            properties: {
              name: { type: "string" },
              address: { $ref: "#/$defs/address" },
            },
          },
        },
        $defs: {
          address: { type: "object" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.properties.user.properties.address.$ref).toBe(
        "#/definitions/address",
      );
      expect(result.definitions.address).toBeDefined();
    });

    it("should process items (single schema)", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "array",
        items: {
          type: "object",
          $defs: {
            nested: { type: "string" },
          },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.items.definitions).toBeDefined();
      expect(result.items.$defs).toBeUndefined();
    });

    it("should process items (array of schemas)", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "array",
        items: [
          {
            type: "object",
            $defs: { nested: { type: "string" } },
          },
          { type: "string" },
        ],
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.items[0].definitions).toBeDefined();
      expect(result.items[0].$defs).toBeUndefined();
      expect(result.items[1].type).toBe("string");
    });

    it("should process allOf", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        allOf: [
          {
            type: "object",
            $defs: { nested: { type: "string" } },
          },
          { type: "object", properties: { name: { type: "string" } } },
        ],
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.allOf[0].definitions).toBeDefined();
      expect(result.allOf[0].$defs).toBeUndefined();
      expect(result.allOf[1].properties).toBeDefined();
    });

    it("should process anyOf", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        anyOf: [
          { type: "string" },
          {
            type: "object",
            unevaluatedProperties: false,
          },
        ],
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.anyOf[0].type).toBe("string");
      expect(result.anyOf[1].unevaluatedProperties).toBeUndefined();
    });

    it("should process oneOf", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        oneOf: [{ type: "string" }, { type: "number" }],
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.oneOf).toHaveLength(2);
      expect(result.oneOf[0].type).toBe("string");
      expect(result.oneOf[1].type).toBe("number");
    });

    it("should process not", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        not: {
          type: "object",
          $defs: { nested: { type: "string" } },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.not.definitions).toBeDefined();
      expect(result.not.$defs).toBeUndefined();
    });

    it("should process if/then/else", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        if: {
          type: "object",
          properties: { type: { const: "user" } },
        },
        then: {
          type: "object",
          $defs: { userSchema: { type: "object" } },
        },
        else: {
          type: "object",
          unevaluatedProperties: false,
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.then.definitions).toBeDefined();
      expect(result.then.$defs).toBeUndefined();
      expect(result.else.unevaluatedProperties).toBeUndefined();
    });

    it("should process patternProperties", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        patternProperties: {
          "^S_": {
            type: "string",
            $defs: { nested: { type: "string" } },
          },
          "^I_": { type: "integer" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.patternProperties["^S_"].definitions).toBeDefined();
      expect(result.patternProperties["^I_"].type).toBe("integer");
    });

    it("should process additionalProperties (schema object)", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        additionalProperties: {
          type: "string",
          $defs: { nested: { type: "string" } },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.additionalProperties.definitions).toBeDefined();
    });
  });

  describe("complex nested scenarios", () => {
    it("should handle deeply nested schemas", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        properties: {
          user: {
            type: "object",
            properties: {
              profile: {
                type: "object",
                allOf: [
                  {
                    type: "object",
                    $defs: {
                      deep: {
                        type: "object",
                        properties: {
                          nested: {
                            $ref: "#/$defs/deep",
                            unevaluatedProperties: false,
                          },
                        },
                      },
                    },
                  },
                ],
              },
            },
          },
        },
        $defs: {
          topLevel: { type: "string" },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.definitions.topLevel).toBeDefined();
      expect(
        result.properties.user.properties.profile.allOf[0].definitions,
      ).toBeDefined();
      expect(
        result.properties.user.properties.profile.allOf[0].definitions.deep
          .properties.nested.unevaluatedProperties,
      ).toBeUndefined();
    });

    it("should handle schema with all major features", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        properties: {
          name: { type: "string" },
          items: {
            type: "array",
            items: { $ref: "#/$defs/item" },
            unevaluatedItems: false,
          },
        },
        allOf: [
          { type: "object" },
          {
            anyOf: [{ type: "object", properties: { x: { type: "number" } } }],
          },
        ],
        $defs: {
          item: {
            type: "object",
            properties: {
              value: { type: "string" },
            },
            unevaluatedProperties: false,
          },
        },
        unevaluatedProperties: false,
        dependentRequired: { x: ["y"] },
        minContains: 1,
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
      expect(result.definitions.item).toBeDefined();
      expect(result.$defs).toBeUndefined();
      expect(result.properties.items.items.$ref).toBe("#/definitions/item");
      expect(result.unevaluatedProperties).toBeUndefined();
      expect(result.unevaluatedItems).toBeUndefined();
      expect(result.dependentRequired).toBeUndefined();
      expect(result.minContains).toBeUndefined();
      expect(result.definitions.item.unevaluatedProperties).toBeUndefined();
    });
  });

  describe("edge cases", () => {
    it("should handle empty objects", () => {
      const schema = {};
      const result = downgradeSchemaToDraft7(schema);
      // Empty object with no newer keywords should be returned as-is
      expect(result).toEqual({
        $schema: "http://json-schema.org/draft-07/schema#",
      });
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
    });

    it("should handle empty objects with newer keywords", () => {
      const schema = {
        $defs: {
          test: { type: "string" },
        },
      };
      const result = downgradeSchemaToDraft7(schema);
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
      expect(result.definitions).toBeDefined();
    });

    it("should handle empty arrays in nested keys", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        allOf: [],
        anyOf: [],
        oneOf: [],
        items: [],
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.allOf).toEqual([]);
      expect(result.anyOf).toEqual([]);
      expect(result.oneOf).toEqual([]);
      expect(result.items).toEqual([]);
    });

    it("should handle null values in nested keys", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        properties: null,
        items: null,
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.properties).toBeNull();
      expect(result.items).toBeNull();
    });

    it("should not mutate original schema", () => {
      const schema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        $defs: {
          test: { type: "string" },
        },
        unevaluatedProperties: false,
      };

      const originalSchema = JSON.parse(JSON.stringify(schema));
      downgradeSchemaToDraft7(schema);

      // Original should be unchanged
      expect(schema.$defs).toBeDefined();
      expect(schema.unevaluatedProperties).toBe(false);
      expect(schema).toEqual(originalSchema);
    });
  });

  describe("real-world example schemas", () => {
    it("should downgrade schema with teamId and first properties", () => {
      const schema = {
        additionalProperties: true,
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        properties: {
          teamId: {
            type: "string",
            description: "Team ID to filter projects by",
          },
          first: {
            type: "integer",
            format: "int32",
            description: "Maximum number of results (default 50)",
          },
        },
        required: [],
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
      expect(result.type).toBe("object");
      expect(result.additionalProperties).toBe(true);
      expect(result.properties.teamId).toEqual(schema.properties.teamId);
      expect(result.properties.first).toEqual(schema.properties.first);
      expect(result.required).toEqual([]);
    });

    it("should downgrade schema with merge request properties", () => {
      const schema = {
        additionalProperties: true,
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        properties: {
          shouldRemoveSourceBranch: {
            type: "boolean",
            description: "Should remove source branch after merge",
          },
          mrIid: {
            type: "integer",
            format: "int32",
            description: "Merge request IID",
          },
          mergeCommitMessage: {
            type: "string",
            description: "Merge commit message",
          },
          projectId: {
            type: "string",
            description: "Project ID or path",
          },
        },
        required: ["projectId", "mrIid"],
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
      expect(result.type).toBe("object");
      expect(result.additionalProperties).toBe(true);
      expect(result.properties.shouldRemoveSourceBranch).toEqual(
        schema.properties.shouldRemoveSourceBranch,
      );
      expect(result.properties.mrIid).toEqual(schema.properties.mrIid);
      expect(result.properties.mergeCommitMessage).toEqual(
        schema.properties.mergeCommitMessage,
      );
      expect(result.properties.projectId).toEqual(schema.properties.projectId);
      expect(result.required).toEqual(["projectId", "mrIid"]);
    });

    it("should return Draft 7 schema as-is when already Draft 7", () => {
      const schema = {
        additionalProperties: true,
        type: "object",
        $schema: "http://json-schema.org/draft-07/schema#",
        properties: {
          limit: {
            maximum: 100,
            description:
              "A limit on the number of objects to be returned. Limit can range between 1 and 100, and the default is 10.",
            type: "integer",
            minimum: 1,
          },
        },
      };

      const result = downgradeSchemaToDraft7(schema);
      // Already Draft 7, should return as-is
      expect(result).toBe(schema);
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
      expect(result.properties.limit).toEqual(schema.properties.limit);
    });

    it("should convert empty $defs to definitions and add Draft 7 schema", () => {
      const schema = {
        $defs: {},
        additionalProperties: true,
        type: "object",
        properties: {
          start_cursor: {
            type: "string",
            description:
              "If supplied, this endpoint will return a page of results starting after the cursor provided. If not supplied, this endpoint will return the first page of results.",
          },
          block_id: {
            type: "string",
            description: "Identifier for a [block](ref:block)",
          },
          page_size: {
            format: "int32",
            description:
              "The number of items from the full list desired in the response. Maximum: 100",
            default: 100,
            type: "integer",
          },
        },
        required: ["block_id"],
      };

      const result = downgradeSchemaToDraft7(schema);
      expect(result.$schema).toBe("http://json-schema.org/draft-07/schema#");
      expect(result.$defs).toBeUndefined();
      expect(result.definitions).toBeDefined();
      expect(result.definitions).toEqual({});
      expect(result.type).toBe("object");
      expect(result.additionalProperties).toBe(true);
      expect(result.properties.start_cursor).toEqual(
        schema.properties.start_cursor,
      );
      expect(result.properties.block_id).toEqual(schema.properties.block_id);
      expect(result.properties.page_size).toEqual(schema.properties.page_size);
      expect(result.required).toEqual(["block_id"]);
    });
  });

  describe("JsonForms compatibility", () => {
    it("should produce valid Draft 7 schemas that pass isJSONSchemaValid", () => {
      const schemas = [
        {
          $schema: "https://json-schema.org/draft/2020-12/schema",
          type: "object",
          properties: {
            name: { type: "string" },
          },
        },
        {
          $schema: "https://json-schema.org/draft/2019-09/schema",
          type: "object",
          $defs: {
            address: { type: "object" },
          },
        },
        {
          type: "object",
          $defs: {
            test: { type: "string" },
          },
        },
        {
          $schema: "https://json-schema.org/draft/2020-12/schema",
          type: "object",
          properties: {
            teamId: { type: "string" },
            first: { type: "integer" },
          },
          unevaluatedProperties: false,
        },
        {
          $schema: "http://json-schema.org/draft-04/schema#",
          type: "object",
          properties: {
            name: { type: "string" },
          },
        },
        {
          $schema: "http://json-schema.org/draft-06/schema#",
          type: "object",
          properties: {
            value: { type: "number" },
          },
        },
      ];

      schemas.forEach((schema) => {
        const downgraded = downgradeSchemaToDraft7(schema);
        const isValid = isJSONSchemaValid(downgraded);
        expect(isValid).toBe(true);
      });
    });

    it("should convert Draft 04 and Draft 06 schemas to Draft 7 for JsonForms compatibility", () => {
      const draft04Schema = {
        $schema: "http://json-schema.org/draft-04/schema#",
        type: "object",
        properties: {
          name: { type: "string" },
          age: { type: "integer" },
        },
        required: ["name"],
      };

      const draft06Schema = {
        $schema: "http://json-schema.org/draft-06/schema#",
        type: "object",
        properties: {
          email: { type: "string", format: "email" },
        },
      };

      const downgraded04 = downgradeSchemaToDraft7(draft04Schema);
      const downgraded06 = downgradeSchemaToDraft7(draft06Schema);

      // Both should be Draft 7
      expect(downgraded04.$schema).toBe(
        "http://json-schema.org/draft-07/schema#",
      );
      expect(downgraded06.$schema).toBe(
        "http://json-schema.org/draft-07/schema#",
      );

      // Both should pass JsonForms validation
      expect(isJSONSchemaValid(downgraded04)).toBe(true);
      expect(isJSONSchemaValid(downgraded06)).toBe(true);
    });

    it("should validate all real-world example schemas with JsonForms validator", () => {
      const exampleSchemas = [
        {
          additionalProperties: true,
          $schema: "https://json-schema.org/draft/2020-12/schema",
          type: "object",
          properties: {
            teamId: {
              type: "string",
              description: "Team ID to filter projects by",
            },
            first: {
              type: "integer",
              format: "int32",
              description: "Maximum number of results (default 50)",
            },
          },
          required: [],
        },
        {
          additionalProperties: true,
          $schema: "https://json-schema.org/draft/2020-12/schema",
          type: "object",
          properties: {
            shouldRemoveSourceBranch: {
              type: "boolean",
              description: "Should remove source branch after merge",
            },
            mrIid: {
              type: "integer",
              format: "int32",
              description: "Merge request IID",
            },
            mergeCommitMessage: {
              type: "string",
              description: "Merge commit message",
            },
            projectId: {
              type: "string",
              description: "Project ID or path",
            },
          },
          required: ["projectId", "mrIid"],
        },
        {
          $defs: {},
          additionalProperties: true,
          type: "object",
          properties: {
            start_cursor: {
              type: "string",
              description:
                "If supplied, this endpoint will return a page of results starting after the cursor provided. If not supplied, this endpoint will return the first page of results.",
            },
            block_id: {
              type: "string",
              description: "Identifier for a [block](ref:block)",
            },
            page_size: {
              format: "int32",
              description:
                "The number of items from the full list desired in the response. Maximum: 100",
              default: 100,
              type: "integer",
            },
          },
          required: ["block_id"],
        },
      ];

      exampleSchemas.forEach((schema) => {
        const downgraded = downgradeSchemaToDraft7(schema);
        const isValid = isJSONSchemaValid(downgraded);
        expect(isValid).toBe(true);
      });
    });

    it("should handle complex nested schemas and validate with JsonForms", () => {
      const complexSchema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        properties: {
          user: {
            type: "object",
            properties: {
              name: { type: "string" },
              address: { $ref: "#/$defs/address" },
            },
          },
          items: {
            type: "array",
            items: { $ref: "#/$defs/item" },
            unevaluatedItems: false,
          },
        },
        allOf: [
          { type: "object" },
          {
            anyOf: [{ type: "object", properties: { x: { type: "number" } } }],
          },
        ],
        $defs: {
          address: {
            type: "object",
            properties: {
              street: { type: "string" },
            },
            unevaluatedProperties: false,
          },
          item: {
            type: "object",
            properties: {
              value: { type: "string" },
            },
          },
        },
        unevaluatedProperties: false,
        dependentRequired: { x: ["y"] },
        minContains: 1,
      };

      const downgraded = downgradeSchemaToDraft7(complexSchema);
      const isValid = isJSONSchemaValid(downgraded);
      expect(isValid).toBe(true);
    });

    it("should validate schemas with all supported Draft 7 features", () => {
      const draft7CompatibleSchema = {
        $schema: "https://json-schema.org/draft/2020-12/schema",
        type: "object",
        properties: {
          stringProp: { type: "string", minLength: 1, maxLength: 100 },
          numberProp: { type: "number", minimum: 0, maximum: 100 },
          integerProp: { type: "integer", multipleOf: 2 },
          booleanProp: { type: "boolean" },
          arrayProp: {
            type: "array",
            items: { type: "string" },
            minItems: 1,
            maxItems: 10,
            uniqueItems: true,
          },
          objectProp: {
            type: "object",
            properties: {
              nested: { type: "string" },
            },
            required: ["nested"],
          },
        },
        required: ["stringProp"],
        allOf: [{ type: "object" }],
        anyOf: [{ type: "object" }],
        oneOf: [{ type: "object" }],
        not: { type: "null" },
        if: { properties: { type: { const: "conditional" } } },
        then: { properties: { value: { type: "string" } } },
        else: { properties: { value: { type: "number" } } },
        $defs: {
          reusable: { type: "string" },
        },
      };

      const downgraded = downgradeSchemaToDraft7(draft7CompatibleSchema);
      const isValid = isJSONSchemaValid(downgraded);
      expect(isValid).toBe(true);
    });
  });
});
