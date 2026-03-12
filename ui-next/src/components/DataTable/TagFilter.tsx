import React, { useState, FunctionComponent, useMemo } from "react";
import {
  Popover,
  Tooltip,
  Box,
  Button,
  Chip,
  TextField,
  InputAdornment,
  Typography,
  Divider,
  List,
  ListItem,
  Checkbox,
  FormControlLabel,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from "@mui/material";
import {
  Tag as TagIcon,
  MagnifyingGlass as SearchIcon,
  CaretDown as ExpandIcon,
} from "@phosphor-icons/react";
import { TagDto } from "types/Tag";

export interface TagFilterProps {
  data: Record<string, unknown>[];
  onTagFilterChange: (selectedTags: string[]) => void;
  selectedTags: string[];
}

export const TagFilter: FunctionComponent<TagFilterProps> = ({
  data,
  onTagFilterChange,
  selectedTags,
}) => {
  const [anchorEl, setAnchorEl] = useState<HTMLButtonElement | null>(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [groupByKey, setGroupByKey] = useState(true);

  const handleClick = (event: React.MouseEvent<HTMLButtonElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
    setSearchTerm("");
  };

  // Extract all unique tags from the data and group them
  const { allTags, tagsByKey, tagKeys } = useMemo(() => {
    const tagMap = new Map<
      string,
      { key: string; value: string; fullTag: string }
    >();

    data.forEach((row: Record<string, unknown>) => {
      if (row?.tags && Array.isArray(row?.tags)) {
        row?.tags?.forEach((tag: TagDto) => {
          if (tag && tag?.key && tag?.value) {
            const fullTag = `${tag?.key}:${tag?.value}`;
            tagMap.set(fullTag, { key: tag?.key, value: tag?.value, fullTag });
          }
        });
      }
    });

    const allTags = Array.from(tagMap?.values())?.sort((a, b) =>
      a?.fullTag?.localeCompare(b?.fullTag),
    );

    // Group by key
    const tagsByKey = new Map<string, typeof allTags>();
    allTags?.forEach((tag) => {
      if (!tagsByKey?.has(tag?.key)) {
        tagsByKey.set(tag?.key, []);
      }
      tagsByKey?.get(tag?.key)?.push(tag);
    });

    const tagKeys = Array.from(tagsByKey?.keys())?.sort();

    return { allTags, tagsByKey, tagKeys };
  }, [data]);

  // Filter tags based on search term
  const filteredTags = useMemo(() => {
    if (!searchTerm) return allTags || [];

    const lowerSearchTerm = searchTerm.toLowerCase();
    return allTags?.filter(
      (tag) =>
        tag?.key?.toLowerCase()?.includes(lowerSearchTerm) ||
        tag?.value?.toLowerCase()?.includes(lowerSearchTerm) ||
        tag?.fullTag?.toLowerCase()?.includes(lowerSearchTerm),
    );
  }, [allTags, searchTerm]);

  const handleTagToggle = (tag: string) => {
    const newSelectedTags = selectedTags?.includes(tag)
      ? selectedTags.filter((t) => t !== tag)
      : [...selectedTags, tag];
    onTagFilterChange(newSelectedTags);
  };

  const handleClearAll = () => {
    onTagFilterChange([]);
  };

  const handleSelectAllInGroup = (key: string) => {
    const groupTags = tagsByKey?.get(key) || [];
    const groupTagStrings = groupTags?.map((tag) => tag?.fullTag);
    const allGroupSelected = groupTagStrings.every((tag) =>
      selectedTags.includes(tag),
    );

    if (allGroupSelected) {
      // Deselect all in group
      const newSelectedTags = selectedTags?.filter(
        (tag) => !groupTagStrings.includes(tag),
      );
      onTagFilterChange(newSelectedTags);
    } else {
      // Select all in group
      const newSelectedTags = [
        ...new Set([...selectedTags, ...groupTagStrings]),
      ];
      onTagFilterChange(newSelectedTags);
    }
  };

  const renderTagList = () => {
    if (allTags?.length === 0) {
      return (
        <Box sx={{ color: "text.secondary", fontStyle: "italic", p: 2 }}>
          No tags available
        </Box>
      );
    }

    if (groupByKey && !searchTerm) {
      // Grouped view
      return (
        <Box sx={{ maxHeight: 300, overflow: "auto", pt: 2 }}>
          {tagKeys.map((key) => {
            const groupTags = tagsByKey?.get(key) || [];
            const groupTagStrings = groupTags?.map((tag) => tag?.fullTag);
            const selectedInGroup = groupTagStrings?.filter((tag) =>
              selectedTags?.includes(tag),
            );
            const allGroupSelected =
              groupTagStrings?.length === selectedInGroup?.length;
            const someGroupSelected = selectedInGroup?.length > 0;

            return (
              <Accordion
                key={key}
                defaultExpanded={tagKeys?.length <= 5}
                elevation={0}
                sx={{
                  "&:before": {
                    display: "none",
                  },
                  "&.Mui-expanded": {
                    margin: 0,
                  },
                  border: "1px solid",
                  borderColor: "divider",
                  "&:not(:last-child)": {
                    borderBottom: 0,
                  },
                }}
              >
                <AccordionSummary expandIcon={<ExpandIcon size={16} />}>
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={allGroupSelected}
                        indeterminate={someGroupSelected && !allGroupSelected}
                        onChange={() => handleSelectAllInGroup(key)}
                        onClick={(e) => e.stopPropagation()}
                        size="small"
                      />
                    }
                    label={
                      <Typography variant="body2" sx={{ fontWeight: 500 }}>
                        {key} ({groupTags?.length})
                      </Typography>
                    }
                    onClick={(e) => e.stopPropagation()}
                  />
                </AccordionSummary>
                <AccordionDetails sx={{ pt: 0 }}>
                  <List dense sx={{ py: 0 }}>
                    {groupTags?.map((tag) => (
                      <ListItem key={tag?.fullTag} sx={{ py: 0, px: 2 }}>
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={selectedTags?.includes(tag?.fullTag)}
                              onChange={() => handleTagToggle(tag?.fullTag)}
                              size="small"
                            />
                          }
                          label={
                            <Typography
                              variant="body2"
                              sx={{ fontSize: "0.875rem" }}
                            >
                              {tag?.value}
                            </Typography>
                          }
                        />
                      </ListItem>
                    ))}
                  </List>
                </AccordionDetails>
              </Accordion>
            );
          })}
        </Box>
      );
    } else {
      // Flat list view (when searching or groupByKey is false)
      return (
        <List dense sx={{ maxHeight: 300, overflow: "auto" }}>
          {filteredTags?.map((tag) => (
            <ListItem key={tag?.fullTag} sx={{ py: 0 }}>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={selectedTags?.includes(tag?.fullTag)}
                    onChange={() => handleTagToggle(tag?.fullTag)}
                    size="small"
                  />
                }
                label={
                  <Typography variant="body2" sx={{ fontSize: "0.875rem" }}>
                    {tag?.fullTag}
                  </Typography>
                }
              />
            </ListItem>
          ))}
          {filteredTags?.length === 0 && searchTerm && (
            <ListItem>
              <Typography variant="body2" color="text.secondary">
                No tags found matching "{searchTerm}"
              </Typography>
            </ListItem>
          )}
        </List>
      );
    }
  };

  return (
    <>
      <Tooltip title="Filter by tags">
        <Button
          size="small"
          variant={"text"}
          startIcon={<TagIcon />}
          onClick={handleClick}
          color={selectedTags?.length > 0 ? "primary" : "inherit"}
          sx={{
            width: "fit-content",
            textTransform: "none",
          }}
        >
          Filter Tags
          {selectedTags?.length > 0 && ` (${selectedTags?.length})`}
        </Button>
      </Tooltip>
      <Popover
        onClose={handleClose}
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "right",
        }}
        transformOrigin={{
          vertical: "top",
          horizontal: "right",
        }}
        PaperProps={{
          style: {
            padding: 16,
            width: 400,
            maxHeight: 600,
            borderRadius: "6px",
          },
        }}
      >
        <Box>
          {/* Header */}
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              mb: 2,
            }}
          >
            <Typography variant="h6" sx={{ fontSize: "1rem", fontWeight: 500 }}>
              Filter by Tags
            </Typography>
            {selectedTags?.length > 0 && (
              <Button size="small" onClick={handleClearAll} color="secondary">
                Clear All
              </Button>
            )}
          </Box>

          {/* Search */}
          <TextField
            fullWidth
            size="small"
            placeholder="Search tags..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon size={16} />
                </InputAdornment>
              ),
            }}
            sx={{ mb: 2 }}
          />

          {/* Group by key toggle (only show when not searching) */}
          {!searchTerm && allTags?.length > 10 && (
            <FormControlLabel
              control={
                <Checkbox
                  checked={groupByKey}
                  onChange={(e) => setGroupByKey(e.target.checked)}
                  size="small"
                />
              }
              label="Group by key"
              sx={{ mb: 1 }}
            />
          )}

          <Divider sx={{ mb: 1 }} />

          {/* Tag list */}
          {renderTagList()}

          {/* Selected tags summary */}
          {selectedTags?.length > 0 && (
            <>
              <Divider sx={{ my: 2 }} />
              <Box>
                <Typography variant="body2" sx={{ fontWeight: 500, mb: 1 }}>
                  Selected ({selectedTags?.length}):
                </Typography>
                <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5 }}>
                  {selectedTags?.slice(0, 5)?.map((tag) => (
                    <Chip
                      key={tag}
                      label={tag}
                      size="small"
                      onDelete={() => handleTagToggle(tag)}
                      color="primary"
                      variant="filled"
                    />
                  ))}
                  {selectedTags?.length > 5 && (
                    <Chip
                      label={`+${selectedTags?.length - 5} more`}
                      size="small"
                      variant="outlined"
                    />
                  )}
                </Box>
              </Box>
            </>
          )}
        </Box>
      </Popover>
    </>
  );
};
