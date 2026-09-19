/**
 * The editor holds the whole schema definition as JSON text, so the form has one
 * field. Named for this form rather than `FieldValues`, which is react-hook-form's
 * own name for the same idea and is imported alongside it.
 */
export type SchemaEditorFormValues = {
  editor: string;
};
