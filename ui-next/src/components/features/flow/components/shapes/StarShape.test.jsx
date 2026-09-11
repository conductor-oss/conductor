import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import StarShape from "./StarShape";

describe("StarShape", () => {
  // dom-to-image (used by the diagram's "export to image") copies each element's
  // computed style onto its clone. A path inside <defs> has no fill of its own, so
  // its computed fill is the initial value, black. Once that is inlined it overrides
  // the fill the path used to inherit from <use>, and the switch node exports as a
  // solid black diamond.
  it("paints the diamond with an explicit fill rather than inheriting one", () => {
    const { container } = render(<StarShape />);
    const paths = container.querySelectorAll("path");

    expect(paths).toHaveLength(1);
    expect(paths[0].getAttribute("fill")).toBe("#FFFFFF");
  });

  it("does not reference the shape or its shadow through document fragments", () => {
    const { container } = render(<StarShape />);

    expect(container.querySelector("use")).toBeNull();
    expect(container.querySelector("filter")).toBeNull();
    expect(container.innerHTML).not.toContain("url(#");
  });
});
