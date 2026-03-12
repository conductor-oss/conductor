import { cloneElement, FunctionComponent } from "react";
import { castToBooleanIfIsBooleanString } from "utils/utils";
import { clone, path as _path } from "lodash/fp";
import { updateField } from "utils/fieldHelpers";

export interface JSONFieldProps {
  path: string;
  onChange?: (value: any) => void;
  taskJson: any;
  checked?: boolean;
  children: any;
  enableCastToBoolean?: boolean;
}
const JSONField: FunctionComponent<JSONFieldProps> = ({
  path,
  onChange,
  taskJson,
  checked,
  children,
  enableCastToBoolean = true,
}: JSONFieldProps) => {
  return cloneElement(children, {
    value: clone(_path(path, taskJson)),
    checked: checked,
    // Needed for special fields like the SinkSelector in EventTaskForm.
    /* taskJson: taskJson, */
    onChange: (maybeEventOrValue: any, maybeValue: any) => {
      // Guarding to automatically detect different types of event handlers
      // working with different onChange signatures.
      let newValue;

      // If the onChange signature is (event, value)
      if (maybeEventOrValue?.target && maybeValue !== undefined) {
        newValue = maybeValue;
        // ...if it's just (event)
      } else if (maybeEventOrValue?.nativeEvent && maybeValue === undefined) {
        newValue = maybeEventOrValue.target.value;
        // ...if it's just (value)
      } else if (maybeEventOrValue) {
        newValue = maybeEventOrValue;
      }

      if (enableCastToBoolean) {
        newValue = castToBooleanIfIsBooleanString(newValue);
      }

      // if the outer onChange is defined, validate the value
      if (onChange) {
        onChange(updateField(path, newValue, taskJson));
      }
    },
  });
};

export default JSONField;
