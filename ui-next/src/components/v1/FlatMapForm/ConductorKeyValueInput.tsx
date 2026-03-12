import { Grid } from "@mui/material";
import MuiCheckbox from "components/MuiCheckbox";
import IconButton from "components/MuiIconButton";
import { ConductorCodeBlockInput } from "components/v1/ConductorCodeBlockInput";
import ConductorInput from "components/v1/ConductorInput";
import ConductorInputNumber from "components/v1/ConductorInputNumber";
import TrashIcon from "components/v1/icons/TrashIcon";
import { ConductorTooltipProps } from "components/conductorTooltip/ConductorTooltip";
import _isEmpty from "lodash/isEmpty";
import {
  ChangeEvent,
  FunctionComponent,
  ReactNode,
  useEffect,
  useRef,
  useState,
} from "react";
import {
  FIELD_TYPE_BOOLEAN,
  FIELD_TYPE_NULL,
  FIELD_TYPE_NUMBER,
  FIELD_TYPE_OBJECT,
  FieldType,
} from "types/index";
import { castToType, inferType } from "utils";
import { ConductorStringOrJsonInput } from "./ConductorStringOrJsonInput";

interface ConductorKeyValueInputProps {
  onChangeValue: (a: string | number | boolean | null) => void;
  mKey: string;
  onChangeKey: (k: string) => void;
  onDeleteItem: (k: string) => void;
  value: string | Record<string, unknown>;
  hideValue: boolean;
  existingKeys: string[];
  hideButtons: boolean;
  showFieldTypes: boolean;
  focusOnField?: string;
  keyColumnLabel?: string;
  valueColumnLabel?: string;
  typeColumnLabel?: string;
  enableAutocomplete?: boolean;
  autoFocusField?: boolean;
  tooltip?: {
    type?: Omit<ConductorTooltipProps, "children">;
    key?: Omit<ConductorTooltipProps, "children">;
    value?: Omit<ConductorTooltipProps, "children">;
  };
  customInput?: ReactNode;
  placeholder?: string;
}

export type MaybeInputProps = {
  cantCoerce: boolean;
  isContainsError: boolean;
  objValue: string;
  onChangeValue: (value: any) => void;
  onObjChange: (a: string) => void;
  type?: FieldType;
  value: any;
  valueColumnLabel?: string;
  customInput?: ReactNode;
  tooltip?: Omit<ConductorTooltipProps, "children">;
  placeholder?: string;
};

export const MaybeInput = (props: MaybeInputProps) => {
  const {
    cantCoerce,
    objValue,
    onChangeValue,
    onObjChange,
    type,
    value,
    valueColumnLabel = "",
    tooltip,
    isContainsError,
    customInput,
    placeholder = "e.g.: max-age=...",
  } = props;

  switch (type) {
    case FIELD_TYPE_NULL:
      return null;

    case FIELD_TYPE_BOOLEAN:
      return (
        <MuiCheckbox
          checked={value}
          onChange={(event) => onChangeValue(event.target.checked)}
        />
      );

    case FIELD_TYPE_NUMBER:
      return (
        <ConductorInputNumber
          fullWidth
          placeholder="e.g.: 123..."
          onChange={(value) => {
            if (value === null) return onChangeValue("");
            return onChangeValue(castToType(value, inferType(value)));
          }}
          value={value}
          inputProps={{
            allowNegative: true,
            thousandSeparator: false,
            valueIsNumericString: true,
          }}
          label={valueColumnLabel}
          tooltip={tooltip}
        />
      );

    case FIELD_TYPE_OBJECT:
      return (
        <ConductorCodeBlockInput
          label={valueColumnLabel}
          onChange={onObjChange}
          value={objValue}
          error={cantCoerce}
          containerProps={{ sx: { width: "100%" } }}
          tooltip={tooltip}
        />
      );

    default:
      return customInput ? (
        customInput
      ) : (
        <ConductorInput
          fullWidth
          onTextInputChange={(val) =>
            onChangeValue(castToType(val, inferType(value)))
          }
          value={value}
          placeholder={placeholder}
          helperText={isContainsError}
          label={valueColumnLabel}
          showClearButton
        />
      );
  }
};

export const ConductorKeyValueInput: FunctionComponent<
  ConductorKeyValueInputProps
> = ({
  onChangeValue,
  onChangeKey,
  mKey,
  hideValue,
  value,
  existingKeys,
  hideButtons,
  showFieldTypes,
  onDeleteItem,
  focusOnField,
  keyColumnLabel,
  valueColumnLabel,
  autoFocusField,
  tooltip,
  customInput,
  placeholder,
}) => {
  const inputRef = useRef<HTMLInputElement>(null);
  const [valueAnEr, setValueAnEr] = useState<[string, string]>([mKey, ""]);
  const [currentType, _setCurrentType] = useState<FieldType>(inferType(value));

  const handleUpdateValue = (val: string) => {
    if (existingKeys.includes(val)) {
      setValueAnEr([val, "Key should be unique"]);
    } else {
      setValueAnEr([val, ""]);
      onChangeKey(val);
    }
  };

  const firstValue = valueAnEr[0];

  useEffect(() => {
    if (focusOnField && inputRef !== null && firstValue === focusOnField) {
      const inputChildRef = inputRef?.current?.querySelector("input");
      inputChildRef?.focus();
    }
  }, [inputRef, focusOnField, firstValue]);

  const containsError = !_isEmpty(valueAnEr[1]);

  const handleFocus = (
    e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    e.target.select();
  };

  const dynamicWidth = () => {
    if (hideValue) {
      return 12;
    }

    return 4;
  };

  return (
    <Grid
      container
      sx={{ width: "100%", display: "grid", gridTemplateColumns: "1fr auto" }}
      size={12}
    >
      <Grid container sx={{ width: "100%" }} spacing={4}>
        <Grid
          container
          spacing={4}
          size={{
            xs: 12,
            sm: 12,
            md: 12,
          }}
          sx={{ width: "100%" }}
        >
          <Grid
            size={{
              xs: 12,
              sm: 12,
              md: dynamicWidth(),
            }}
          >
            <ConductorInput
              onTextInputChange={handleUpdateValue}
              value={valueAnEr[0]}
              autoFocus={
                (focusOnField === valueAnEr[0] || valueAnEr[0] != null) &&
                autoFocusField
              }
              onFocus={(
                e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
              ) => handleFocus(e)}
              fullWidth
              placeholder="e.g.: Cache-Control..."
              error={containsError}
              helperText={valueAnEr[1]}
              ref={inputRef}
              label={keyColumnLabel}
              showClearButton
              tooltip={tooltip?.key}
            />
          </Grid>

          {!hideValue && (
            <Grid
              size={{
                xs: 12,
                sm: 12,
                md: 8,
              }}
            >
              <ConductorStringOrJsonInput
                label={valueColumnLabel}
                value={value}
                onChange={onChangeValue}
                customInput={customInput}
                placeholder={placeholder}
                showFieldTypes={showFieldTypes}
              />
            </Grid>
          )}
        </Grid>
      </Grid>
      {!hideButtons ? (
        <Grid
          alignSelf={
            (hideValue || currentType !== FIELD_TYPE_OBJECT) && !containsError
              ? "center"
              : "start"
          }
        >
          <IconButton onClick={() => onDeleteItem(mKey)}>
            <TrashIcon />
          </IconButton>
        </Grid>
      ) : null}
    </Grid>
  );
};
