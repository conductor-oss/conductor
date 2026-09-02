type Props = {
    handleEditChanges?: (code: string) => void;
    editorChanges?: string;
    isConfirmSave?: boolean;
    originalSource?: string;
};
declare const EventHandlerEditor: ({ handleEditChanges, editorChanges, isConfirmSave, originalSource, }: Props) => import("react").JSX.Element;
export default EventHandlerEditor;
