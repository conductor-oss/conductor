import { Box, IconButton, Popper, PopperProps, TextField } from "@mui/material";

import { colors } from "theme/tokens/variables";
import { ChangeEvent, useRef } from "react";
import XCloseIcon from "components/icons/XCloseIcon";
import ArrowUpIcon from "components/icons/ArrowUpIcon";
import ArrowDownIcon from "components/icons/ArrowDownIcon";
import useArrowNavigation from "useArrowNavigation";
import EnterIcon from "components/icons/EnterIcon";

type OptionsProps = {
  taskName: string;
  taskRef: string;
  type: string;
};

export type AdvancedSearchFieldPopperProps = PopperProps & {
  open?: boolean;
  options: OptionsProps[];
  handleClose: () => void;
  onSelectItem: (value: string | null) => void;
  filteredOptionsCount: number;
  setFilteredOptionsCount: (count: number) => void;
  hoveredItem: string;
  setHoveredItem: (item: string) => void;
  searchTerm: string;
  setSearchTerm: (value: string) => void;
  totalOptionsCount: number;
};

const SEARCH_POPPER_WIDTH = "370px";
const SEARCH_DROPDOWN_HEIGHT = "300px";

const style = {
  inputStyle: {
    "& .MuiOutlinedInput-notchedOutline, & .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline":
      {
        border: "none",
      },
    "& .MuiTextField-root, & .MuiInputBase-root ": {
      fontSize: "12px",
      minHeight: "30px",
      borderRadius: "20px",
      padding: "0px",
    },
    "& .MuiInputAdornment-positionStart": {
      width: "12px",
      paddingLeft: "8px",
    },
    "& .MuiInputAdornment-positionEnd": {
      paddingRight: "8px",
    },
  },
};

export const AdvancedSearchFieldPopper = ({
  options,
  anchorEl,
  onSelectItem,
  filteredOptionsCount,
  setFilteredOptionsCount,
  hoveredItem,
  setHoveredItem,
  searchTerm,
  setSearchTerm,
  totalOptionsCount,
}: AdvancedSearchFieldPopperProps) => {
  const parentPopperRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleInputChange = (
    event: ChangeEvent<HTMLTextAreaElement | HTMLInputElement>,
  ) => {
    setSearchTerm(event.target.value);
    const newFilteredOptions = options.filter((option) =>
      `${option.taskName}${option.taskRef}${option.type}`
        .toLowerCase()
        .includes(event.target.value.toLowerCase()),
    );
    setFilteredOptionsCount(newFilteredOptions.length);
  };

  const uniqueIdGenerator = (data: OptionsProps) => {
    return `${data.taskName}${data.taskRef}${data.type}`;
  };

  const { inputProps, optionPropsForItem, moveDown, moveUp } =
    useArrowNavigation({
      onSelect: (elem) => {
        onSelectItem(elem.taskRef);
      },
      options: options || [],
      optionsIdGen: uniqueIdGenerator,
      scrollToCenter: true,
      hoveredItem,
      setHoveredItem,
    });

  const handleClearSearch = () => {
    setSearchTerm("");
    if (inputRef?.current) {
      inputRef.current.focus();
    }
  };

  return (
    <Popper
      open={true}
      anchorEl={anchorEl}
      sx={{
        boxShadow: "4px 4px 10px 0px rgba(89, 89, 89, 0.41)",
        borderRadius: "6px",
        backgroundColor: colors.white,
        width: SEARCH_POPPER_WIDTH,
      }}
      role={undefined}
      ref={parentPopperRef}
      placement="bottom-start"
      {...inputProps}
    >
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          border: `1px solid ${colors.blueLight}`,
          borderRadius: "6px",
        }}
      >
        <TextField
          fullWidth
          inputRef={inputRef}
          sx={{ ...style.inputStyle }}
          autoFocus
          value={searchTerm}
          onChange={handleInputChange}
        />
        {searchTerm && (
          <IconButton onClick={handleClearSearch}>
            <XCloseIcon />
          </IconButton>
        )}
        <Box
          sx={{
            borderLeft: `1px solid ${colors.lightGrey}`,
            padding: "0 5px",
          }}
        >
          {filteredOptionsCount}/{totalOptionsCount}
        </Box>
        <IconButton onClick={moveUp}>
          <ArrowUpIcon />
        </IconButton>
        <IconButton onClick={moveDown}>
          <ArrowDownIcon />
        </IconButton>
      </Box>
      <Box
        sx={{
          maxHeight: SEARCH_DROPDOWN_HEIGHT,
          overflow: "hidden",
          overflowY: "scroll",
        }}
      >
        {searchTerm &&
          options &&
          options.length > 0 &&
          options.map((option) => (
            <Box
              key={uniqueIdGenerator(option)}
              {...optionPropsForItem(option)}
              style={{
                padding: "4px 2px 4px 15px",
                background:
                  uniqueIdGenerator(option) === hoveredItem
                    ? colors.lightBlueHoverBg
                    : "",
                cursor: "pointer",
              }}
              onClick={() => onSelectItem(option.taskRef)}
            >
              <Box sx={{ display: "flex", gap: 3, alignItems: "center" }}>
                <Box sx={{ fontWeight: 500 }}>{option.taskName}</Box>
                <Box>{option.taskRef}</Box>
                <Box
                  sx={{
                    background: colors.greyBorder,
                    borderRadius: "5px",
                    fontSize: "7px",
                    padding: "3px 8px",
                  }}
                >
                  {option.type}
                </Box>
                {uniqueIdGenerator(option) === hoveredItem && (
                  <Box
                    sx={{
                      display: "flex",
                      marginLeft: "auto",
                      paddingRight: "5px",
                    }}
                  >
                    <EnterIcon color={colors.darkBlueLightMode} size={14} />
                  </Box>
                )}
              </Box>
            </Box>
          ))}
      </Box>
    </Popper>
  );
};
