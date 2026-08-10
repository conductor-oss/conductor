import { describe, it, expect, vi } from "vitest";

vi.mock("utils/flags", () => ({
  FEATURES: {
    SHOW_GET_STARTED_PAGE: "SHOW_GET_STARTED_PAGE",
    SCHEDULER: "SCHEDULER",
    SECRETS: "SECRETS",
    HUMAN_TASK: "HUMAN_TASK",
    WEBHOOKS: "WEBHOOKS",
    RBAC: "RBAC",
    INTEGRATIONS: "INTEGRATIONS",
    REMOTE_SERVICES: "REMOTE_SERVICES",
    SKU_ENABLED: "SKU_ENABLED",
  },
  featureFlags: {
    isEnabled: vi.fn(() => false),
  },
}));

import { checkPathFlag } from "../checkPathFlag";

describe("checkPathFlag", () => {
  describe("scheduler paths — gated by FEATURES.SCHEDULER flag", () => {
    it("blocks /scheduleDef when SCHEDULER flag is disabled", () => {
      expect(checkPathFlag("/scheduleDef")).toBe(false);
    });

    it("blocks /scheduleDef/:name when SCHEDULER flag is disabled", () => {
      expect(checkPathFlag("/scheduleDef/my-schedule")).toBe(false);
    });

    it("blocks /schedulerExecs when SCHEDULER flag is disabled", () => {
      expect(checkPathFlag("/schedulerExecs")).toBe(false);
    });

    it("blocks /newScheduleDef when SCHEDULER flag is disabled", () => {
      expect(checkPathFlag("/newScheduleDef")).toBe(false);
    });
  });

  describe("core paths — always enabled regardless of flags", () => {
    it("allows /workflowDef", () => {
      expect(checkPathFlag("/workflowDef")).toBe(true);
    });

    it("allows /taskDef", () => {
      expect(checkPathFlag("/taskDef")).toBe(true);
    });

    it("allows / (root catch-all)", () => {
      expect(checkPathFlag("/")).toBe(true);
    });
  });

  describe("unknown paths fall through to / catch-all and are allowed", () => {
    it("allows /executions", () => {
      expect(checkPathFlag("/executions")).toBe(true);
    });

    it("allows /event-monitor", () => {
      expect(checkPathFlag("/event-monitor")).toBe(true);
    });

    it("allows /execution/:id", () => {
      expect(checkPathFlag("/execution/abc123")).toBe(true);
    });
  });

  describe("feature-gated paths — behavior depends on SKU_ENABLED flag", () => {
    // When SKU_ENABLED is false (default), pathFlagMapWithoutSKU is used. Paths
    // not explicitly listed there fall through to the "/" catch-all → true.
    // This means /secrets, /configure-webhook etc. are accessible in non-SKU mode.
    it("/secrets is accessible when SKU_ENABLED is false (falls through to catch-all)", () => {
      expect(checkPathFlag("/secrets")).toBe(true);
    });

    // /human IS listed in pathFlagMapWithoutSKU, gated by HUMAN_TASK flag (false in mock)
    it("/human is blocked when HUMAN_TASK flag is disabled (even without SKU)", () => {
      expect(checkPathFlag("/human")).toBe(false);
    });

    // /integrations IS listed in pathFlagMapWithoutSKU, gated by INTEGRATIONS flag (false)
    it("/integrations is blocked when INTEGRATIONS flag is disabled (even without SKU)", () => {
      expect(checkPathFlag("/integrations")).toBe(false);
    });
  });
});
