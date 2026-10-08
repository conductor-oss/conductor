/**
 * Save used to bury required-field rules inside EventHandlerButton HOCs, so a
 * UX change could ship with nothing asserting when Save must stay disabled.
 * These pin the form and Code-tab gates: name + event required; description
 * optional; no-op and invalid JSON blocked.
 */
import { describe, expect, it } from "vitest";
import { isEditorSaveDisabled, isFormSaveDisabled } from "./saveGuards";

const readyToSave = {
  name: "my_handler",
  event: "kafka:config:topic",
  noChanges: false,
  isTrialExpired: false,
};

describe("isFormSaveDisabled", () => {
  it("allows saving when name and event are set and the form is dirty", () => {
    expect(isFormSaveDisabled(readyToSave)).toBe(false);
  });

  it("blocks saving when name is empty", () => {
    expect(isFormSaveDisabled({ ...readyToSave, name: "" })).toBe(true);
    expect(isFormSaveDisabled({ ...readyToSave, name: "   " })).toBe(true);
    expect(isFormSaveDisabled({ ...readyToSave, name: null })).toBe(true);
  });

  it("blocks saving when event is empty", () => {
    expect(isFormSaveDisabled({ ...readyToSave, event: "" })).toBe(true);
    expect(isFormSaveDisabled({ ...readyToSave, event: "   " })).toBe(true);
    expect(isFormSaveDisabled({ ...readyToSave, event: undefined })).toBe(true);
  });

  it("does not require a description — only name and event gate Save", () => {
    // Description is never passed in; an empty one must not block.
    expect(isFormSaveDisabled(readyToSave)).toBe(false);
  });

  it("blocks saving an unchanged definition", () => {
    expect(isFormSaveDisabled({ ...readyToSave, noChanges: true })).toBe(true);
  });

  it("blocks saving on an expired trial", () => {
    expect(isFormSaveDisabled({ ...readyToSave, isTrialExpired: true })).toBe(
      true,
    );
  });
});

describe("isEditorSaveDisabled", () => {
  const editorReady = {
    ...readyToSave,
    invalidJson: false,
    hasEditorContent: true,
  };

  it("allows saving valid dirty JSON with name and event", () => {
    expect(isEditorSaveDisabled(editorReady)).toBe(false);
  });

  it("blocks saving when the JSON cannot be parsed", () => {
    expect(isEditorSaveDisabled({ ...editorReady, invalidJson: true })).toBe(
      true,
    );
  });

  it("blocks saving when parsed name or event is empty", () => {
    expect(isEditorSaveDisabled({ ...editorReady, name: "" })).toBe(true);
    expect(isEditorSaveDisabled({ ...editorReady, event: "" })).toBe(true);
  });

  it("skips name/event checks when the editor has no content yet", () => {
    expect(
      isEditorSaveDisabled({
        ...editorReady,
        name: "",
        event: "",
        hasEditorContent: false,
      }),
    ).toBe(false);
  });

  it("blocks saving unchanged or trial-expired editors", () => {
    expect(isEditorSaveDisabled({ ...editorReady, noChanges: true })).toBe(
      true,
    );
    expect(isEditorSaveDisabled({ ...editorReady, isTrialExpired: true })).toBe(
      true,
    );
  });
});
