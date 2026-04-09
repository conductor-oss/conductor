import { Box, Grid, GridProps } from "@mui/material";
import { FunctionComponent, ReactNode } from "react";

import ConductorInput from "components/ui/inputs/ConductorInput";

export interface QuickSearchProps {
  autoFocusValue: boolean;
  createButton?: ReactNode;
  description?: ReactNode;
  onChange: (val: string) => void;
  quickSearchLabel?: ReactNode;
  quickSearchPlaceholder: string;
  searchTerm: string;
  searchModalContainerProps?: GridProps;
}

export const QuickSearch: FunctionComponent<QuickSearchProps> = ({
  autoFocusValue,
  createButton,
  description,
  quickSearchLabel = "Quick search",
  onChange,
  quickSearchPlaceholder,
  searchTerm,
  searchModalContainerProps,
}) => {
  return (
    <Box padding={6}>
      <Grid
        container
        sx={{ width: "100%" }}
        justifyContent="space-between"
        spacing={2}
      >
        <Grid
          size={{
            xs: 12,
            md: 4,
          }}
          {...searchModalContainerProps}
        >
          <ConductorInput
            id="quick-search-field"
            fullWidth
            label={quickSearchLabel}
            placeholder={quickSearchPlaceholder}
            showClearButton
            value={searchTerm}
            onTextInputChange={onChange}
            autoFocus={autoFocusValue}
          />
        </Grid>

        {createButton && (
          <Grid
            display="flex"
            justifyContent="flex-end"
            size={{
              xs: 12,
              md: 3,
            }}
          >
            {createButton}
          </Grid>
        )}

        {description && (
          <Grid
            display="flex"
            alignItems="center"
            size={{
              xs: 12,
              md: createButton ? 12 : 8,
            }}
          >
            {description}
          </Grid>
        )}
      </Grid>
    </Box>
  );
};
