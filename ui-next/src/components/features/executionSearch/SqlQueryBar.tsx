import Editor, { BeforeMount, OnMount } from "@monaco-editor/react";
import SearchIcon from "@mui/icons-material/Search";
import { Box, InputBase, useMediaQuery } from "@mui/material";
import { Theme } from "@mui/material/styles";
import { KeyboardEvent, useContext, useRef, useState } from "react";
import { ColorModeContext } from "theme/material/ColorModeContext";
import { SMALL_EDITOR_DEFAULT_OPTIONS } from "utils/constants";
import { MONO_FONT } from "./searchScopes";
import { fieldBorderColor, helperTextColor, placeholderColor } from "./styles";

const LINE_HEIGHT = 20;
const MIN_EDITOR_HEIGHT = 38;
const MAX_EDITOR_HEIGHT = 160;

const focusRing = (theme: Theme) => ({
  "&:focus-within": {
    borderColor: theme.palette.primary.main,
    boxShadow: `0 0 0 1px ${theme.palette.primary.main}`,
  },
});

export interface SqlQueryBarProps {
  value: string;
  onChange: (value: string) => void;
  /** Runs the query. Also bound to ⌘/Ctrl+Enter in the editor. */
  onSubmit: () => void;
  /** e.g. to register completion items for field names and values. */
  beforeMount?: BeforeMount;
  hint: string;
  searchButtonId?: string;
  /** An example query shown while the editor is empty. */
  placeholder?: string;
}

/**
 * The SQL mode counterpart of the scoped search bar: one outlined bar with a
 * "SQL" segment, the query editor and Search, then a hint line. The editor
 * grows with the query up to a few lines.
 */
export const SqlQueryBar = ({
  value,
  onChange,
  onSubmit,
  beforeMount,
  hint,
  searchButtonId = "search-workflow-btn",
  placeholder = "workflowType = 'my_workflow' AND status IN (FAILED)",
}: SqlQueryBarProps) => {
  const { mode } = useContext(ColorModeContext);
  // Phones use 44px controls, like the Free text box below the bar.
  const isPhone = useMediaQuery((theme: Theme) => theme.breakpoints.down("sm"));
  const minEditorHeight = isPhone ? MIN_EDITOR_HEIGHT + 4 : MIN_EDITOR_HEIGHT;
  const [contentHeight, setContentHeight] = useState(0);
  const height = Math.min(
    MAX_EDITOR_HEIGHT,
    Math.max(minEditorHeight, contentHeight),
  );
  // Monaco keeps the first command it is given; read the latest handler.
  const submitRef = useRef(onSubmit);
  submitRef.current = onSubmit;

  const handleMount: OnMount = (editor, monaco) => {
    editor.onDidContentSizeChange(({ contentHeight: next }) =>
      setContentHeight(next),
    );
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () =>
      submitRef.current(),
    );
  };

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 0.5 }}>
      <Box
        sx={(theme) => ({
          ...focusRing(theme),
          display: "flex",
          alignItems: "stretch",
          minHeight: { xs: 44, sm: 40 },
          border: 1,
          borderColor: fieldBorderColor(theme),
          borderRadius: 1.5,
          bgcolor: "background.paper",
          overflow: "hidden",
        })}
      >
        <Box
          component="span"
          aria-hidden="true"
          sx={{
            display: { xs: "none", sm: "flex" },
            alignItems: "center",
            pl: 2,
            pr: 1.5,
            borderRight: 1,
            borderColor: fieldBorderColor,
            bgcolor: "action.hover",
            fontSize: 13,
            fontWeight: 500,
          }}
        >
          SQL
        </Box>
        <Box
          sx={{
            position: "relative",
            flex: 1,
            minWidth: 0,
            alignSelf: "center",
            pl: 1.5,
          }}
        >
          {!value && (
            <Box
              aria-hidden="true"
              sx={{
                position: "absolute",
                inset: "0 12px 0 12px",
                lineHeight: `${minEditorHeight}px`,
                overflow: "hidden",
                whiteSpace: "nowrap",
                textOverflow: "ellipsis",
                fontFamily: MONO_FONT,
                fontSize: 13,
                color: placeholderColor,
                pointerEvents: "none",
                zIndex: 1,
              }}
            >
              {placeholder}
            </Box>
          )}
          <Editor
            height={height}
            defaultLanguage="sql"
            theme={mode === "dark" ? "vs-dark" : "light"}
            value={value}
            onChange={(next) => onChange(next ?? "")}
            beforeMount={beforeMount}
            onMount={handleMount}
            options={{
              ...SMALL_EDITOR_DEFAULT_OPTIONS,
              ariaLabel: "SQL query",
              fontSize: 13,
              lineHeight: LINE_HEIGHT,
              padding: { top: 9, bottom: 9 },
              wordWrap: "on",
              scrollBeyondLastLine: false,
              // Lets the suggestion list escape the bar's clipped edges.
              fixedOverflowWidgets: true,
              // Typing "(" or "," must not accept an open suggestion, or
              // "status IN (" can turn into a suggested word.
              acceptSuggestionOnCommitCharacter: false,
            }}
          />
        </Box>
        <Box
          component="button"
          type="button"
          id={searchButtonId}
          onClick={onSubmit}
          sx={{
            px: 4,
            border: 0,
            bgcolor: "primary.main",
            color: "primary.contrastText",
            font: "inherit",
            fontSize: 13,
            fontWeight: 500,
            cursor: "pointer",
            "&:hover": { bgcolor: "primary.dark" },
            "&:focus-visible": {
              outline: "2px solid",
              outlineColor: "primary.contrastText",
              outlineOffset: -4,
            },
          }}
        >
          Search
        </Box>
      </Box>
      <Box
        component="p"
        sx={{
          m: 0,
          pl: 0.25,
          fontSize: 12,
          lineHeight: "16px",
          color: helperTextColor,
        }}
      >
        {hint}
      </Box>
    </Box>
  );
};

export interface FreeTextInputProps {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  placeholder?: string;
}

/** A free text box styled like the scoped search bar's input. */
export const FreeTextInput = ({
  value,
  onChange,
  onSubmit,
  placeholder = "Free text: words from input, output, variables, task outputs or failure reasons",
}: FreeTextInputProps) => (
  <Box
    sx={(theme) => ({
      ...focusRing(theme),
      display: "flex",
      alignItems: "center",
      gap: 1.25,
      height: { xs: 44, sm: 40 },
      px: 1.5,
      border: 1,
      borderColor: fieldBorderColor(theme),
      borderRadius: 1.5,
      bgcolor: "background.paper",
    })}
  >
    <SearchIcon
      aria-hidden="true"
      sx={{ fontSize: 18, color: helperTextColor }}
    />
    <InputBase
      fullWidth
      type="search"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      onKeyDown={(event: KeyboardEvent<HTMLInputElement>) => {
        if (event.key === "Enter") {
          event.preventDefault();
          onSubmit();
        }
      }}
      placeholder={placeholder}
      inputProps={{ "aria-label": "Free text" }}
      sx={{ fontSize: { xs: 16, sm: 14 } }}
    />
  </Box>
);
