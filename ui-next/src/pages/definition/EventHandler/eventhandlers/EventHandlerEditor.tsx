import { CodeTabEditor } from "components/ui/CodeTab";
import { configureMonaco } from "utils/monacoUtils/CodeEditorUtils";
import { validateEventHandlerJson } from "./eventHandlerValidation";

type Props = {
  handleEditChanges?: (code: string) => void;
  editorChanges?: string;
  isConfirmSave?: boolean;
  originalSource?: string;
};

const EventHandlerEditor = ({
  handleEditChanges,
  editorChanges,
  isConfirmSave,
  originalSource,
}: Props) => (
  <CodeTabEditor
    idPrefix="event-handler-code"
    value={editorChanges ?? ""}
    onChange={(text) => handleEditChanges?.(text)}
    validate={validateEventHandlerJson}
    diff={isConfirmSave ? { original: originalSource ?? "" } : null}
    downloadName={(parsed) => parsed?.name || "event-handler"}
    schemaLabel="JSON · EventHandler schema"
    beforeMount={configureMonaco}
    // The machine treats the editor's first value as the baseline, so sync it
    // as soon as Monaco has loaded the model.
    onMount={(editor) => handleEditChanges?.(editor.getValue())}
  />
);

export default EventHandlerEditor;
