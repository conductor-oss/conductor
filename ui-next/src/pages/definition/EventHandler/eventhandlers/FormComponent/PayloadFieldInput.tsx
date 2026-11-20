import { ConductorAutocompleteVariables } from "components/FlatMapForm/ConductorAutocompleteVariables";
import ConductorInput, {
  ConductorInputProps,
} from "components/ui/inputs/ConductorInput";
import { usePayloadFields, WORKFLOW_ID_FIELD } from "./payloadFields";

type PayloadFieldInputProps = ConductorInputProps & {
  /** Suggested alongside the sample payload's fields. */
  extraFields?: string[];
};

/**
 * A text field for an action value that can reference the event payload.
 * Suggests the sample payload's `${...}` fields (plus any `extraFields`);
 * with nothing to suggest it is a plain input. Takes ConductorInput's props
 * so it can stand in wherever an action form used ConductorInput.
 */
export const PayloadFieldInput = ({
  extraFields = [],
  ...props
}: PayloadFieldInputProps) => {
  const sampleFields = usePayloadFields();
  const fields = [
    ...sampleFields,
    ...extraFields.filter((field) => !sampleFields.includes(field)),
  ];

  if (fields.length === 0) return <ConductorInput {...props} />;

  const {
    id,
    label,
    value,
    placeholder,
    helperText,
    error,
    fullWidth = true,
    multiline,
    onTextInputChange,
  } = props;

  return (
    <ConductorAutocompleteVariables
      id={id}
      label={label}
      value={value == null ? "" : String(value)}
      placeholder={placeholder}
      helperText={typeof helperText === "string" ? helperText : undefined}
      error={Boolean(error)}
      fullWidth={fullWidth}
      otherOptions={fields}
      // An empty field shows every suggestion on focus; once it has text the
      // list filters to matches, so a filled field stays quiet.
      openOnFocus
      // Single-line fields keep their height: growing on focus and shrinking
      // on blur moved the next control (e.g. the Append checkbox) out from
      // under the click that blurred the field. Multi-line ones (Prompt)
      // still grow so there is room to write.
      expandOnFocus={Boolean(multiline)}
      includeWorkflowOptions={false}
      onChange={(next) => onTextInputChange?.(String(next ?? ""))}
    />
  );
};

/** For fields that take a workflow (execution) ID. */
export const WorkflowIdInput = (props: ConductorInputProps) => (
  <PayloadFieldInput {...props} extraFields={[WORKFLOW_ID_FIELD]} />
);

export default PayloadFieldInput;
