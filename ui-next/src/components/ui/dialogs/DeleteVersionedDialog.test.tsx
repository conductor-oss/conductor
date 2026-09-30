/**
 * CCOR-13397 / CCOR-13424 / CCOR-13432 — deleting a versioned definition from a list page
 * used to silently remove only the latest version, leaving the definition listed at the
 * next-older one. The dialog now asks which version to remove.
 */
import { fireEvent, render, screen } from "@testing-library/react";

import DeleteVersionedDialog from "./DeleteVersionedDialog";

// ConductorInput autofocuses through a ref that jsdom leaves undefined; the typed-name
// gating is what matters here, so stand in with a plain input.
vi.mock("components/ui/inputs/ConductorInput", () => ({
  default: ({ id, value, onTextInputChange }: any) => (
    <input
      id={id}
      value={value ?? ""}
      onChange={(event) => onTextInputChange?.(event.target.value)}
    />
  ),
}));

vi.mock("components/ui/inputs/ConductorSelect", () => ({
  default: ({ id, value, items, error, onChange }: any) => (
    <select
      id={id}
      value={value}
      data-error={String(!!error)}
      onChange={(event) => onChange({ target: { value: event.target.value } })}
    >
      {items.map((item: any) => (
        <option key={String(item.value)} value={item.value}>
          {item.label}
        </option>
      ))}
    </select>
  ),
}));

const renderDialog = (
  props: Partial<Parameters<typeof DeleteVersionedDialog>[0]> = {},
) => {
  const onConfirm = vi.fn();
  const onCancel = vi.fn();
  render(
    <DeleteVersionedDialog
      name="my_workflow"
      entityLabel="workflow"
      versions={[1, 2, 3]}
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...props}
    />,
  );
  return { onConfirm, onCancel };
};

const versionField = () =>
  document.querySelector("#delete-version-field") as HTMLSelectElement | null;
const nameField = () =>
  document.querySelector(
    "#choice-dialog-confirmation-field",
  ) as HTMLInputElement;
const confirmBtn = () =>
  document.querySelector("#choice-dialog-confirm-btn") as HTMLButtonElement;

const typeName = (value: string) =>
  fireEvent.change(nameField(), { target: { value } });

describe("DeleteVersionedDialog", () => {
  beforeEach(() => vi.clearAllMocks());

  it("offers every version, newest first, and marks the latest", () => {
    renderDialog();

    const labels = Array.from(versionField()!.querySelectorAll("option")).map(
      (option) => option.textContent,
    );
    expect(labels).toEqual(["3 — latest", "2", "1"]);
  });

  it("preselects the latest even when versions arrive after the first render", () => {
    const onConfirm = vi.fn();
    const { rerender } = render(
      <DeleteVersionedDialog
        name="my_workflow"
        entityLabel="workflow"
        versions={[]}
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    );

    rerender(
      <DeleteVersionedDialog
        name="my_workflow"
        entityLabel="workflow"
        versions={[1, 2, 3]}
        onConfirm={onConfirm}
        onCancel={vi.fn()}
      />,
    );

    expect(versionField()).toHaveValue("3");
  });

  it("preselects the latest version, which is the one the row shows", () => {
    renderDialog();

    expect(versionField()).toHaveValue("3");
  });

  it("deletes the selected version", () => {
    const { onConfirm } = renderDialog();

    fireEvent.change(versionField()!, { target: { value: "2" } });
    typeName("my_workflow");
    fireEvent.click(confirmBtn());

    expect(onConfirm).toHaveBeenCalledWith(2);
  });

  it("reports a number, not the select's string", () => {
    const { onConfirm } = renderDialog();

    typeName("my_workflow");
    fireEvent.click(confirmBtn());

    expect(onConfirm.mock.calls[0][0]).toBe(3);
  });

  it("still requires the name to be typed", () => {
    const { onConfirm } = renderDialog();

    expect(confirmBtn()).toBeDisabled();
    typeName("wrong_name");
    expect(confirmBtn()).toBeDisabled();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it("cancels without deleting", () => {
    const { onConfirm, onCancel } = renderDialog();

    fireEvent.click(
      document.querySelector("#choice-dialog-cancel-btn") as HTMLButtonElement,
    );

    expect(onCancel).toHaveBeenCalled();
    expect(onConfirm).not.toHaveBeenCalled();
  });

  describe("all versions", () => {
    it("is not offered unless the caller allows it", () => {
      renderDialog();

      const values = Array.from(versionField()!.querySelectorAll("option")).map(
        (option) => option.getAttribute("value"),
      );
      expect(values).not.toContain("all");
    });

    it("reports undefined so the caller deletes the whole definition", () => {
      const { onConfirm } = renderDialog({ allowDeleteAll: true });

      fireEvent.change(versionField()!, { target: { value: "all" } });
      typeName("my_workflow");
      fireEvent.click(confirmBtn());

      expect(onConfirm).toHaveBeenCalledWith(undefined);
    });

    it("warns that nothing survives", () => {
      renderDialog({ allowDeleteAll: true });

      fireEvent.change(versionField()!, { target: { value: "all" } });

      expect(
        screen.getByText("Nothing will remain — the workflow itself is removed."),
      ).toBeInTheDocument();
    });

    it("marks the field itself as the destructive choice", () => {
      renderDialog({ allowDeleteAll: true });

      expect(versionField()).toHaveAttribute("data-error", "false");

      fireEvent.change(versionField()!, { target: { value: "all" } });

      expect(versionField()).toHaveAttribute("data-error", "true");
    });

    it("names the button after the scope, not just \u201cConfirm\u201d", () => {
      renderDialog({ allowDeleteAll: true });

      expect(confirmBtn()).toHaveTextContent("Delete version 3");

      fireEvent.change(versionField()!, { target: { value: "all" } });

      expect(confirmBtn()).toHaveTextContent("Delete all versions");
    });
  });

  describe("what survives", () => {
    it("says which versions remain and who becomes latest", () => {
      renderDialog();

      expect(
        screen.getByText(
          "Versions 2 and 1 will remain, and version 2 becomes the latest.",
        ),
      ).toBeInTheDocument();
    });

    it("leaves latest alone when an older version is the one going", () => {
      renderDialog();

      fireEvent.change(versionField()!, { target: { value: "1" } });

      expect(
        screen.getByText("Versions 3 and 2 will remain."),
      ).toBeInTheDocument();
    });
  });

  describe("with nothing to choose", () => {
    it("hides the picker for a single-version definition", () => {
      renderDialog({ versions: [1] });

      expect(versionField()).not.toBeInTheDocument();
    });

    it("still deletes that single version", () => {
      const { onConfirm } = renderDialog({ versions: [1] });

      typeName("my_workflow");
      fireEvent.click(confirmBtn());

      expect(onConfirm).toHaveBeenCalledWith(1);
    });

    it("deletes the whole definition when no versions are known", () => {
      const { onConfirm } = renderDialog({ versions: [] });

      typeName("my_workflow");
      fireEvent.click(confirmBtn());

      expect(onConfirm).toHaveBeenCalledWith(undefined);
    });

    it("hides the picker for one version even when all-versions is on offer", () => {
      // "version 1" and "all versions" would be the same delete.
      renderDialog({ versions: [1], allowDeleteAll: true });

      expect(versionField()).not.toBeInTheDocument();
    });

    it("still deletes that one version when all-versions is on offer", () => {
      const { onConfirm } = renderDialog({
        versions: [1],
        allowDeleteAll: true,
      });

      typeName("my_workflow");
      fireEvent.click(confirmBtn());

      expect(onConfirm).toHaveBeenCalledWith(1);
    });
  });
});
