import { Autocomplete } from "@mui/material";
import { createFilterOptions } from "@mui/material/Autocomplete";
import TagChip from "components/TagChip";
import ConductorInput from "components/v1/ConductorInput";
import XCloseIcon from "components/v1/icons/XCloseIcon";
import { ReactNode } from "react";
import { autocompleteStyle } from "shared/styles";
import { TagDto } from "types/Tag";

type ReplaceTagsInputProps = {
  onChange?: (tags: string[]) => void | null;
  label?: ReactNode;
  tags: TagDto[];
  options: TagDto[];
};
type SuggestValueType = { title: string; inputValue: string };

const filter = createFilterOptions<SuggestValueType>();

const ReplaceTagsInput = ({
  label = "Tags",
  onChange = () => null,
  tags = [],
  options = [],
}: ReplaceTagsInputProps) => {
  return (
    <Autocomplete
      id="tag-autocomplete-input"
      multiple
      freeSolo
      options={options.map((tag: TagDto) => {
        return (
          tag && {
            inputValue: `${tag.key}:${tag.value}`,
            title: `${tag.key}:${tag.value}`,
          }
        );
      })}
      defaultValue={tags.map((tag: TagDto) => {
        return (
          tag && {
            inputValue: `${tag.key}:${tag.value}`,
            title: `${tag.key}:${tag.value}`,
          }
        );
      })}
      renderTags={(value, getTagProps) =>
        value.map((option, index) => {
          const label = (
            option?.inputValue ? option.inputValue : option
          ) as string;
          const { key, ...otherTagProps } = getTagProps({ index });
          return (
            <TagChip
              key={key}
              id={`${label}-tag-chip`}
              label={label}
              {...otherTagProps}
              sx={{
                "& .MuiSvgIcon-root": {
                  background: "transparent",
                },
              }}
            />
          );
        })
      }
      getOptionLabel={(option) => {
        // Value selected with enter, right from the input
        if (typeof option === "string") {
          return option;
        }
        // Add "xxx" option created dynamically
        if (option.inputValue) {
          // Regular option
          return option.title;
        }
        // Regular option
        return option.title;
      }}
      filterOptions={(options, params) => {
        const filtered = filter(options, params);

        const { inputValue } = params;
        // Suggest the creation of a new value
        const isExisting = options.some(
          (option) => inputValue === option.title,
        );

        if (inputValue !== "" && !isExisting) {
          filtered.push({
            inputValue,
            title: `Add "${inputValue}"`,
          });
        }

        return filtered;
      }}
      onChange={(_event, newValue: (string | SuggestValueType)[]) => {
        onChange(
          newValue.map((val: string | SuggestValueType) =>
            typeof val === "string" ? val : val.inputValue,
          ),
        );
      }}
      renderInput={(params) => (
        <ConductorInput
          {...params}
          id="tag-input-field"
          label={label}
          placeholder="e.g.: team:design, region:us-east"
          helperText={
            <span>
              Type a tag name using the <strong>key:value</strong> format and
              press enter to create new tags.
            </span>
          }
        />
      )}
      sx={[autocompleteStyle({ value: tags })]}
      clearIcon={<XCloseIcon />}
    />
  );
};

export default ReplaceTagsInput;
