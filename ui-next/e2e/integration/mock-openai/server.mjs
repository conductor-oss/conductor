/**
 * Minimal OpenAI-compatible stub for the UI integration stack.
 *
 * The Conductor server talks to OpenAI through the Responses API
 * (POST {baseURL}/responses — see ai/.../openai/api/OpenAIResponsesApi.java),
 * both for LLM_* tasks and for agents. The e2e compose files point the server
 * here so LLM execution tests are deterministic and need no API key.
 *
 * Replies are chosen from REPLY_RULES by matching the request body, so each
 * test gets the answer it asserts on. Unknown endpoints return 404 and are
 * logged, which makes a new, unstubbed call easy to spot in `docker logs`.
 *
 * No dependencies: runs with `node server.mjs` on any Node 18+ image.
 */

import { createServer } from "node:http";
import { randomUUID } from "node:crypto";

const PORT = Number(process.env.PORT ?? 8090);

/** First matching rule wins. Matched against the raw JSON request body. */
const REPLY_RULES = [
  { match: /2 \+ 2/, reply: "4" },
  { match: /\bHELLO\b/, reply: "HELLO" },
  { match: /\bok\b/i, reply: "ok" },
];
const DEFAULT_REPLY = "mock response";

function pickReply(body) {
  return (
    REPLY_RULES.find((rule) => rule.match.test(body))?.reply ?? DEFAULT_REPLY
  );
}

function responsesResult(model, text) {
  return {
    id: `resp_${randomUUID()}`,
    object: "response",
    model,
    status: "completed",
    output: [
      {
        type: "message",
        id: `msg_${randomUUID()}`,
        role: "assistant",
        status: "completed",
        content: [{ type: "output_text", text, annotations: [] }],
      },
    ],
    output_text: text,
    usage: { input_tokens: 1, output_tokens: 1, total_tokens: 2 },
  };
}

function sendJson(res, status, payload) {
  res.writeHead(status, { "Content-Type": "application/json" });
  res.end(JSON.stringify(payload));
}

const server = createServer((req, res) => {
  const path = (req.url ?? "").split("?")[0];

  if (req.method === "GET" && path === "/health") {
    return sendJson(res, 200, { status: "ok" });
  }

  let body = "";
  req.on("data", (chunk) => (body += chunk));
  req.on("end", () => {
    if (req.method === "POST" && /\/responses$/.test(path)) {
      let model = "mock-model";
      try {
        model = JSON.parse(body).model ?? model;
      } catch {
        return sendJson(res, 400, { error: { message: "invalid JSON body" } });
      }
      const reply = pickReply(body);
      console.log(`POST ${path} model=${model} -> ${JSON.stringify(reply)}`);
      return sendJson(res, 200, responsesResult(model, reply));
    }

    console.warn(`unstubbed ${req.method} ${path}`);
    return sendJson(res, 404, {
      error: { message: `mock-openai: no stub for ${req.method} ${path}` },
    });
  });
});

server.listen(PORT, () => {
  console.log(`mock-openai listening on :${PORT}`);
});
