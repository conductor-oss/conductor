import { buildSortParam } from "../executionSort";

describe("buildSortParam", () => {
  // The Agent Name column renders `workflowType`, and `workflow_type` is the
  // indexed column behind it. Sending anything else makes the backend drop the
  // ORDER BY, which is issue #1514.
  it("sorts the Agent Name column on workflowType", () => {
    expect(buildSortParam("workflowType", "asc")).toBe("workflowType:ASC");
    expect(buildSortParam("workflowType", "desc")).toBe("workflowType:DESC");
  });

  it("passes other sortable columns through unchanged", () => {
    expect(buildSortParam("startTime", "desc")).toBe("startTime:DESC");
    expect(buildSortParam("workflowId", "asc")).toBe("workflowId:ASC");
    expect(buildSortParam("updateTime", "asc")).toBe("updateTime:ASC");
    expect(buildSortParam("status", "desc")).toBe("status:DESC");
  });

  it("uppercases the direction", () => {
    expect(buildSortParam("startTime", "ASC")).toBe("startTime:ASC");
  });
});
