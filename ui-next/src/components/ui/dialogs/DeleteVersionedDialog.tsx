/**
 * Delete confirmation for definitions that carry versions — workflows, agents, schemas,
 * user forms, AI prompts.
 *
 * The list pages show one row per definition at its latest version, so a bare "delete"
 * there is ambiguous: it used to remove just that latest version and leave the rest
 * behind, which read as "the delete did nothing". This asks which version to remove, says
 * what will survive, and offers every version at once where the backend supports it.
 *
 * The frame, buttons and typed-name gate come from {@link ConfirmChoiceDialog}, so this
 * stays in step with every other confirmation in the product.
 */
import { Box } from "@mui/material";
import { Warning } from "@phosphor-icons/react";
import { ReactNode, useState } from "react";

import { Text } from "components/index";
import ConductorSelect from "components/ui/inputs/ConductorSelect";
import { colors } from "theme/tokens/variables";

import ConfirmChoiceDialog from "./ConfirmChoiceDialog";
import { confirmDialogStyle } from "./confirmDialogStyle";
import { describeOutcome } from "./deleteOutcome";

/** The select's value for "every version". Not a version number, so it cannot collide. */
const ALL_VERSIONS = "all";

const VERSION_FIELD_ID = "delete-version-field";
const VERSION_LABEL_ID = "delete-version-label";

const style = {
  outcome: {
    marginTop: "8px",
    fontSize: "12.5px",
    lineHeight: 1.45,
    color: colors.gray07,
  },
  warning: {
    display: "flex",
    alignItems: "flex-start",
    gap: "9px",
    marginTop: "10px",
    padding: "10px 12px",
    borderRadius: "8px",
    backgroundColor: colors.redXXLight,
  },
  // Picking "all versions" is the destructive choice, so the field itself says so. The
  // outline comes from ConductorInput's own `error` styling, which `sx` cannot outrank.
  allVersionsField: {
    "& .MuiInputBase-root": { backgroundColor: colors.redXXLight },
    "& .MuiSelect-icon": { color: colors.failureDark },
  },
};

export interface DeleteVersionedDialogProps {
  /** Definition name. The user types this to confirm, as they did before. */
  name: string;
  /** Every known version. Fewer than two hides the picker — there is nothing to choose. */
  versions?: number[];
  /** Noun for the copy, e.g. "workflow", "schema", "user form". */
  entityLabel: string;
  /** Offer "All versions". Leave off where the backend cannot delete a whole definition. */
  allowDeleteAll?: boolean;
  isDeleting?: boolean;
  onCancel: () => void;
  /** `undefined` means every version; a number means just that one. */
  onConfirm: (version: number | undefined) => void;
  id?: string;
}

export default function DeleteVersionedDialog({
  name,
  versions,
  entityLabel,
  allowDeleteAll = false,
  isDeleting,
  onCancel,
  onConfirm,
  id,
}: DeleteVersionedDialogProps) {
  const sorted = [...(versions ?? [])].sort((a, b) => b - a);
  const latest = sorted[0];
  // Latest is preselected: it is the version the row is showing, so it is what the user
  // was looking at when they pressed delete. Derived rather than stored, because callers
  // that fetch their versions have none on the first render — a stored default would
  // stick at empty once they arrive.
  const [chosen, setChosen] = useState<string>();
  const selection = chosen ?? String(latest ?? "");

  // One version makes "that version" and "all versions" the same delete, so there is
  // nothing to pick and the dialog stays as it was.
  const hasChoice = sorted.length > 1;

  const items = [
    ...(allowDeleteAll
      ? [{ label: `All ${sorted.length} versions`, value: ALL_VERSIONS }]
      : []),
    ...sorted.map((each) => ({
      label: each === latest ? `${each} (latest)` : String(each),
      value: String(each),
    })),
  ];

  // An empty selection means the caller gave no versions at all — nothing to single out,
  // so the whole definition goes.
  const deletingAll = selection === ALL_VERSIONS || selection === "";
  const version = deletingAll ? undefined : Number(selection);

  const boldName = (
    <Box
      component="strong"
      sx={{ ...confirmDialogStyle.name, color: colors.red07 }}
    >
      {name}
    </Box>
  );

  let message: ReactNode;
  if (hasChoice) {
    message = <>Choose which version of {boldName} to remove.</>;
  } else if (sorted.length === 1) {
    message = <>Deleting {boldName} removes its only version.</>;
  } else {
    message = <>Deleting {boldName} removes it and every version it has.</>;
  }

  let confirmLabel: string;
  if (!hasChoice) {
    confirmLabel = `Delete ${entityLabel}`;
  } else if (deletingAll) {
    confirmLabel = "Delete all versions";
  } else {
    confirmLabel = `Delete version ${version}`;
  }

  const outcome = describeOutcome(sorted, version, entityLabel);

  return (
    <ConfirmChoiceDialog
      id={id}
      header={`Delete ${entityLabel}`}
      destructive
      isInputConfirmation
      valueToBeDeleted={name}
      confirmBtnLabel={confirmLabel}
      inputLabel={
        <>
          Type{" "}
          <Box component="strong" sx={confirmDialogStyle.name}>
            {name}
          </Box>{" "}
          to confirm
        </>
      }
      isConfirmLoading={isDeleting}
      disableBackdropClick={isDeleting}
      disableEscapeKeyDown={isDeleting}
      handleConfirmationValue={(confirmed) =>
        confirmed ? onConfirm(version) : onCancel()
      }
      message={
        <>
          <Text component="p" sx={{ margin: 0, fontSize: "14px" }}>
            {message} This cannot be undone.
          </Text>

          {hasChoice && (
            <Box sx={{ marginTop: "20px" }}>
              <Box
                component="label"
                id={VERSION_LABEL_ID}
                htmlFor={VERSION_FIELD_ID}
                sx={confirmDialogStyle.fieldLabel}
              >
                Version to delete
              </Box>
              {/* Fixed width, so the field does not resize as the selection changes. */}
              <Box sx={{ width: 180 }}>
                <ConductorSelect
                  id={VERSION_FIELD_ID}
                  SelectProps={{ labelId: VERSION_LABEL_ID }}
                  error={deletingAll}
                  sx={deletingAll ? style.allVersionsField : undefined}
                  fullWidth
                  value={selection}
                  items={items}
                  onChange={(event) => setChosen(String(event.target.value))}
                />
              </Box>
              {deletingAll ? (
                <Box sx={style.warning}>
                  <Warning
                    size={16}
                    color={colors.failureDark}
                    style={{ flexShrink: 0, marginTop: 1 }}
                  />
                  <Text
                    sx={{
                      fontSize: "12.5px",
                      lineHeight: 1.45,
                      color: colors.red04,
                    }}
                  >
                    {outcome}
                  </Text>
                </Box>
              ) : (
                <Text sx={style.outcome} component="p">
                  {outcome}
                </Text>
              )}
            </Box>
          )}
        </>
      }
    />
  );
}
