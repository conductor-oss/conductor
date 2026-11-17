/**
 * Save used to be disabled whenever the description was empty, with nothing on
 * screen saying so — and the rule was not even applied consistently (new task
 * definitions bypassed it, so a definition could be created without a
 * description and then never edited again). The API marks description optional;
 * only ownerEmail is required. These tests pin what may and may not block Save.
 */
import { describe, expect, it } from "vitest";
import { isSaveDisabled } from "./helpers";

const editingExisting = {
  noChanges: false,
  isNewTaskDef: false,
  isTrialExpired: false,
};

describe("isSaveDisabled", () => {
  it("allows saving an edited definition", () => {
    expect(isSaveDisabled(editingExisting)).toBe(false);
  });

  it("blocks saving an unchanged existing definition", () => {
    expect(isSaveDisabled({ ...editingExisting, noChanges: true })).toBe(true);
  });

  it("allows saving a brand new definition that has no changes yet", () => {
    expect(
      isSaveDisabled({
        ...editingExisting,
        isNewTaskDef: true,
        noChanges: true,
      }),
    ).toBe(false);
  });

  it("blocks saving when the JSON cannot be parsed", () => {
    expect(isSaveDisabled({ ...editingExisting, jsonInvalid: true })).toBe(
      true,
    );
  });

  it("blocks saving on an expired trial", () => {
    expect(isSaveDisabled({ ...editingExisting, isTrialExpired: true })).toBe(
      true,
    );
  });

  it("treats an undefined isNewTaskDef as an existing definition", () => {
    // The form machine's context does not carry the flag.
    expect(
      isSaveDisabled({
        noChanges: true,
        isTrialExpired: false,
        isNewTaskDef: undefined,
      }),
    ).toBe(true);
  });
});
