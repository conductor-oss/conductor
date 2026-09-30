/**
 * The confirmation dialog's shared look. Lives apart from the component so other dialogs
 * can build on the same frame without tripping react-refresh's one-export-per-file rule.
 */
import { colors } from "theme/tokens/variables";

export const CONFIRM_FIELD_ID = "choice-dialog-confirmation-field";

export const confirmDialogStyle = {
  // One width for every confirmation. These are all a short question plus, at most, a
  // typed-name field — nothing in them needs more room, and 690px stretched a one-line
  // question uncomfortably wide.
  paper: {
    width: "520px",
    maxWidth: "520px",
    borderRadius: "10px",
  },
  title: {
    display: "flex",
    alignItems: "center",
    gap: "12px",
    padding: "26px 28px 0",
    background: "transparent",
    border: "none",
    fontSize: "18px",
    fontWeight: 600,
  },
  badge: {
    flexShrink: 0,
    width: 32,
    height: 32,
    borderRadius: "8px",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.redXXLight,
    color: colors.failureDark,
  },
  // The padding lives on an inner Box: MUI zeroes padding-top on a DialogContent that
  // follows a DialogTitle, with a selector `sx` cannot outrank.
  content: {
    padding: 0,
  },
  body: {
    display: "flex",
    flexDirection: "column",
    gap: "20px",
    padding: "16px 28px 4px",
  },
  // Definition names run long and carry no spaces, so they have to be allowed to break.
  message: {
    fontSize: "14px",
    lineHeight: 1.5,
    color: colors.gray06,
    overflowWrap: "anywhere",
    "& p": { fontSize: "14px", fontWeight: "normal" },
    // Prose steps back, but the name a caller emphasises must not go grey with it.
    // Scoped to bare <strong>: anything a page coloured itself keeps its own treatment.
    "& strong:not([class])": { color: colors.black },
  },
  // An instruction, not a field label, so it matches the message prose it continues —
  // same size and colour, with only the name inside it at full strength. It belongs to
  // the input, so it sits 6px above it rather than a body gap away.
  inputLabel: {
    display: "block",
    marginBottom: "6px",
    fontSize: "14px",
    fontWeight: 400,
    color: colors.gray06,
    overflowWrap: "anywhere",
  },
  fieldLabel: {
    display: "block",
    marginBottom: "6px",
    fontSize: "12.5px",
    fontWeight: 600,
    color: colors.gray04,
    overflowWrap: "anywhere",
  },
  actions: {
    gap: "10px",
    marginTop: "24px",
    padding: "16px 28px",
    background: "transparent",
    borderTop: `1px solid ${colors.gray13}`,
  },
  cancelButton: {
    color: colors.gray04,
    backgroundColor: colors.white,
    borderColor: colors.gray11,
    ":hover": {
      backgroundColor: colors.gray14,
      borderColor: colors.gray10,
    },
  },
  // The theme's contained variants pin their own border colour, so this sets the border
  // alongside the fill. red07 (palette.error.main) does not clear 4.5:1 behind white
  // text; red06 does, at 6.3:1.
  destructiveButton: {
    color: colors.white,
    backgroundColor: colors.red06,
    borderColor: colors.red06,
    ":hover": {
      backgroundColor: colors.red05,
      borderColor: colors.red05,
    },
  },
  // The name the user has to read and retype, so it sits at full strength against the
  // greyed prose around it.
  name: {
    fontWeight: 600,
    color: colors.black,
  },
};
