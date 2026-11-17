import { expect, test, type Page } from "@playwright/test";
import { mockCommonApis } from "./helpers/mockApi";

const GUIDE_URL = "/agents/new?language=java&framework=conductor";

/**
 * The guide's scroll container — found by geometry rather than a brittle class,
 * and scoped to the content area so it can't pick up the sidebar's scroller.
 */
const scrollTop = (page: Page) =>
  page.evaluate(() => {
    const main = document.querySelector("#main-content");
    const el = [...(main?.querySelectorAll("*") ?? [])].find((node) => {
      const style = getComputedStyle(node);
      return (
        /auto|scroll/.test(style.overflowY) &&
        node.scrollHeight > node.clientHeight + 4
      );
    });
    return el ? el.scrollTop : null;
  });

const selectFramework = async (page: Page, label: string) => {
  await page.locator("#agent-guide-framework").click();
  await page.getByRole("option", { name: label }).click();
};

test.beforeEach(async ({ page }) => {
  await mockCommonApis(page);
  await page.goto(GUIDE_URL);
  await expect(page.locator("#agent-guide-framework")).toBeVisible();
});

test("scrolls with the wheel after changing framework, without clicking first", async ({
  page,
}) => {
  await selectFramework(page, "LangChain4j");
  await expect(page).toHaveURL(/framework=langchain4j/);

  const before = await scrollTop(page);
  expect(before).toBe(0);

  // Move (not click) over the content, then send real wheel input.
  const viewport = page.viewportSize()!;
  await page.mouse.move(viewport.width / 2, viewport.height / 2);
  await page.mouse.wheel(0, 600);

  await expect.poll(() => scrollTop(page)).toBeGreaterThan(0);
});

test("the menu's modal root is removed from the DOM after closing", async ({
  page,
}) => {
  // The root cause: navigating from the change handler interrupted the Menu's
  // exit transition, so MUI never set `exited` and left the Modal root mounted
  // across the viewport. Navigating from onExited instead lets the close
  // finish, so nothing is left behind.
  await page.locator("#agent-guide-framework").click();
  await expect(page.locator(".MuiMenu-root")).toHaveCount(1);

  await page.getByRole("option", { name: "LangChain4j" }).click();
  await expect(page).toHaveURL(/framework=langchain4j/);
  await expect(page.locator(".MuiMenu-root")).toHaveCount(0);
});

test("scroll stays locked while the menu is open, and is released after", async ({
  page,
}) => {
  const bodyOverflow = () =>
    page.evaluate(() => document.body.style.overflow || "");

  await page.locator("#agent-guide-framework").click();
  await expect(page.getByRole("option", { name: "LangChain4j" })).toBeVisible();
  // Locking the page behind an open menu is intended; the fix must not remove it.
  expect(await bodyOverflow()).toBe("hidden");

  await page.getByRole("option", { name: "LangChain4j" }).click();
  await expect(page).toHaveURL(/framework=langchain4j/);
  await expect.poll(bodyOverflow).toBe("");
});

test("the framework menu is still usable after the fix", async ({ page }) => {
  // pointer-events are disabled on the Modal root and re-enabled on the paper,
  // so the menu itself must remain clickable — guard against over-correcting.
  await selectFramework(page, "LangChain4j");
  await expect(page).toHaveURL(/framework=langchain4j/);

  await selectFramework(page, "Google ADK");
  await expect(page).toHaveURL(/framework=google-adk/);
});
