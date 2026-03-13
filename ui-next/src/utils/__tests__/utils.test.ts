import { getErrors } from "utils";

const mockedHeaders = new Map([["content-type", "application/json"]]);
const headers = {
  append: (name: string, value: string) => {
    mockedHeaders.set(name, value);
    return mockedHeaders;
  },
  delete: (name: string) => {
    mockedHeaders.delete(name);
  },
  get: (name: string) => mockedHeaders.get(name),
  has: (name: string) => mockedHeaders.has(name),
  set: (name: string, value: string) => mockedHeaders.set(name, value),
  forEach: mockedHeaders.forEach,
};

describe("getErrors", () => {
  it("should return all errors", async () => {
    const response = {
      json: async () => {
        return {
          message: "Bad request",
          validationErrors: [
            {
              path: "registerTaskDef.taskDefinitions[0].ownerEmail",
              message: "ownerEmail cannot be empty",
            },
            {
              path: "registerTaskDef.taskDefinitions[0].name",
              message: "name cannot be empty",
            },
          ],
        };
      },
      headers: headers as unknown as Headers,
      clone: () => ({ ...response }),
      status: 400,
    } as Response;
    const errors = await getErrors(response);
    expect(errors).toMatchObject({
      "registerTaskDef.taskDefinitions[0].ownerEmail":
        "ownerEmail cannot be empty",
      "registerTaskDef.taskDefinitions[0].name": "name cannot be empty",
    });
  });

  it("should return the error message", async () => {
    const response = {
      json: async () => {
        return {
          message: "Bad request",
        };
      },
      headers: headers as unknown as Headers,
      clone: () => ({ ...response }),
      status: 400,
    } as Response;
    const errors = await getErrors(response);
    expect(errors).toMatchObject({
      message: "Bad request",
    });
  });

  it("Should return default error message, if error could not be identified", async () => {
    const response = {
      json: async () => {
        return {
          name: "taskDef1",
        };
      },
      clone: () => ({ ...response }),
      status: 502,
      statusText: "Bad Gateway",
    } as Response;
    const errors = await getErrors(response);
    expect(errors).toMatchObject({
      message: `Error performing action. error number: 502 Bad Gateway`,
    });
  });

  it("Should be able to change error handler to custom function", async () => {
    const response = {
      json: async () => {
        return {
          name: "taskDef1",
        };
      },
      clone: () => ({ ...response }),
      status: 502,
      statusText: "Bad Gateway",
    } as Response;
    const errors = await getErrors(response, () => ({
      message: "Hi im custom error handler",
    }));
    expect(errors).toMatchObject({
      message: "Hi im custom error handler",
    });
  });
});
