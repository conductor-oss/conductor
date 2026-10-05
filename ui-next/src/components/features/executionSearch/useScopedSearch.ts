import { useState } from "react";
import {
  mergeValues,
  parseSearchInput,
  SearchScope,
  SearchScopeConfig,
} from "./searchScopes";

export interface UseScopedSearchOptions {
  /** The fields the search bar offers, in menu and chip order. */
  scopes: SearchScopeConfig[];
  /** Current filter values for each scope, as stored by the page. */
  values: Record<SearchScope, string[]>;
  setValues: (scope: SearchScope, values: string[]) => void;
  /** Runs the search again when submitting would not change any filter. */
  onSearchAgain: () => void;
}

export interface SearchChipData {
  scope: SearchScope;
  values: string[];
}

export interface ScopedSearchState {
  scopes: SearchScopeConfig[];
  scope: SearchScope;
  setScope: (scope: SearchScope) => void;
  term: string;
  setTerm: (term: string) => void;
  chips: SearchChipData[];
  submit: () => void;
  removeValue: (scope: SearchScope, value: string) => void;
  removeScope: (scope: SearchScope) => void;
  /** Clears what is typed in the search bar, e.g. on Clear all. */
  clearTerm: () => void;
}

/**
 * State for the scoped search bar and its chips. The page owns the filter
 * values (usually URL query state); this adds the typed term, the chosen field
 * and the chip actions on top.
 */
export const useScopedSearch = ({
  scopes,
  values,
  setValues,
  onSearchAgain,
}: UseScopedSearchOptions): ScopedSearchState => {
  const [scope, setScope] = useState<SearchScope>(scopes[0].key);
  const [term, setTerm] = useState("");
  const config = scopes.find((s) => s.key === scope) ?? scopes[0];

  const submit = () => {
    const added = parseSearchInput(config, term);
    const current = values[config.key] ?? [];
    const merged = mergeValues(current, added);
    setTerm("");
    if (merged.length === current.length) {
      // Nothing typed, or everything typed is already a filter.
      onSearchAgain();
      return;
    }
    setValues(config.key, merged);
  };

  const removeValue = (chipScope: SearchScope, value: string) => {
    setValues(
      chipScope,
      (values[chipScope] ?? []).filter((v) => v !== value),
    );
  };

  const removeScope = (chipScope: SearchScope) => setValues(chipScope, []);

  const chips = scopes
    .map(({ key }) => ({ scope: key, values: values[key] ?? [] }))
    .filter((chip) => chip.values.length > 0);

  return {
    scopes,
    scope: config.key,
    setScope,
    term,
    setTerm,
    chips,
    submit,
    removeValue,
    removeScope,
    clearTerm: () => setTerm(""),
  };
};
