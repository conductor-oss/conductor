import { scanTasksForDependenciesInWorkflow } from "../workflow";

describe("scanTasksForDependenciesInWorkflow", () => {
  it("should return empty dependencies for an empty workflow", () => {
    const workflow = {
      tasks: [],
    };
    const result = scanTasksForDependenciesInWorkflow(workflow as any);
    expect(result).toEqual({
      integrationNames: [],
      promptNames: [],
      userFormsNameVersion: [],
      schemas: [],
      secrets: [],
      env: [],
      workflowName: undefined,
      workflowVersion: undefined,
    });
  });

  it("should extract LLM integration, prompt, secrets, and env from tasks", () => {
    const workflow = {
      tasks: [
        {
          type: "LLM_TEXT_COMPLETE",
          inputParameters: {
            llmProvider: "openai",
            promptName: "myPrompt",
            secretField: "${workflow.secrets.API_KEY}",
            envField: "${workflow.env.MY_ENV}",
          },
        },
      ],
    };
    const result = scanTasksForDependenciesInWorkflow(workflow as any);
    expect(result.integrationNames).toContain("openai");
    expect(result.promptNames).toContain("myPrompt");
    expect(result.secrets).toContain("${workflow.secrets.API_KEY}");
    expect(result.env).toContain("${workflow.env.MY_ENV}");
  });

  it("should extract user form name/version from human tasks", () => {
    const workflow = {
      tasks: [
        {
          type: "HUMAN",
          inputParameters: {
            __humanTaskDefinition: {
              userFormTemplate: { name: "formA", version: 2 },
            },
          },
        },
      ],
    };
    const result = scanTasksForDependenciesInWorkflow(workflow as any);
    expect(result.userFormsNameVersion).toEqual([
      { name: "formA", version: "2" },
    ]);
  });

  it("should extract schemas from task definitions", () => {
    const workflow = {
      tasks: [
        {
          type: "SIMPLE",
          inputParameters: {},
          taskDefinition: {
            inputSchema: { name: "inputSchema", version: 1 },
            outputSchema: { name: "outputSchema", version: 2 },
          },
        },
      ],
    };
    const result = scanTasksForDependenciesInWorkflow(workflow as any);
    expect(result.schemas).toEqual([
      { name: "inputSchema", version: "1" },
      { name: "outputSchema", version: "2" },
    ]);
  });

  it("should extract workflow-level schemas and outputParameters secrets/env", () => {
    const workflow = {
      name: "wf1",
      version: 3,
      tasks: [],
      inputSchema: { name: "wfInput", version: 1 },
      outputSchema: { name: "wfOutput", version: 2 },
      outputParameters: {
        secret: "${workflow.secrets.SECRET1}",
        env: "${workflow.env.ENV1}",
      },
    };
    const result = scanTasksForDependenciesInWorkflow(workflow as any);
    expect(result.schemas).toEqual([
      { name: "wfInput", version: "1" },
      { name: "wfOutput", version: "2" },
    ]);
    expect(result.secrets).toContain("${workflow.secrets.SECRET1}");
    expect(result.env).toContain("${workflow.env.ENV1}");
    expect(result.workflowName).toBe("wf1");
    expect(result.workflowVersion).toBe(3);
  });

  it("should deduplicate user forms and schemas by name/version", () => {
    const workflow = {
      tasks: [
        {
          type: "HUMAN",
          inputParameters: {
            __humanTaskDefinition: {
              userFormTemplate: { name: "formA", version: 1 },
            },
          },
        },
        {
          type: "HUMAN",
          inputParameters: {
            __humanTaskDefinition: {
              userFormTemplate: { name: "formA", version: 1 },
            },
          },
        },
        {
          type: "SIMPLE",
          inputParameters: {},
          taskDefinition: {
            inputSchema: { name: "schemaA", version: 1 },
          },
        },
        {
          type: "SIMPLE",
          inputParameters: {},
          taskDefinition: {
            inputSchema: { name: "schemaA", version: 1 },
          },
        },
      ],
    };
    const result = scanTasksForDependenciesInWorkflow(workflow as any);
    expect(result.userFormsNameVersion).toEqual([
      { name: "formA", version: "1" },
    ]);
    expect(result.schemas).toEqual([{ name: "schemaA", version: "1" }]);
  });
});
