import { memorySearchResponse } from "./McpMemorySearchOutput";

describe("memorySearchResponse", () => {
  it("prefers the parsed MCP response over its duplicate text envelope", () => {
    const response = memorySearchResponse({
      content: [
        {
          type: "text",
          text: JSON.stringify({ query: "old", results: [] }),
          parsed: {
            query: "health checks",
            total: 1,
            results: [{ key: "run/execution-1", relevance_score: 0.9 }],
          },
        },
      ],
    });

    expect(response).toEqual({
      query: "health checks",
      total: 1,
      results: [{ key: "run/execution-1", relevance_score: 0.9 }],
    });
  });

  it("parses an MCP text-only response and ignores ordinary task output", () => {
    expect(
      memorySearchResponse({
        content: [
          { type: "text", text: JSON.stringify({ query: "q", results: [] }) },
        ],
      }),
    ).toEqual({ query: "q", results: [] });
    expect(memorySearchResponse({ result: "ordinary output" })).toBeNull();
  });
});
