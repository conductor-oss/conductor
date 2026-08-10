import SearchIcon from "@mui/icons-material/Search";
import { Box } from "@mui/material";
import InputBase from "@mui/material/InputBase";
import { CaretDoubleRight, XCircle } from "@phosphor-icons/react";
import _first from "lodash/fp/first";
import _isEqual from "lodash/isEqual";
import { ReactElement, useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { blueLight, seGrey, seGrey2 } from "theme/tokens/colors";
import useArrowNavigation, {
  useArrowNavigationProps,
} from "useArrowNavigation";

type SearchResultBase = {
  icon?: ReactElement;
  title: string;
  route?: string;
};

type SearchResultRoute = SearchResultBase & {
  sub?: never;
};

type SearchResultSub = SearchResultBase & {
  sub: SearchResults;
};
type SearchResultItem = SearchResultRoute | SearchResultSub;

type SearchResults = Array<SearchResultItem>;

export interface SearchEverythingProps {
  onChange: (change: string, max?: number) => void;
  searchResults?: SearchResults;
  onClear: () => void;
  searchTerm: string;
  setOpen?: (value: boolean) => void;
  maxSearchResults?: number;
}

const searchBarStyle = {
  padding: "13px",
  borderRadius: "11px",
  border: `1px solid ${blueLight}`,
  display: "flex",
  alignItems: "center",
};
const closeCircleStyle = {
  marginLeft: "auto",
  display: "flex",
  alignItems: "center",
  cursor: "pointer",
};
const searchInputStyle = {
  padding: "0 8px",
  width: "100%",
  input: {
    fontSize: "14px",
    fontStyle: "normal",
    fontWeight: 500,
    lineHeight: "normal",
  },
};
const resultsWrapperStyle = {
  padding: "8px 0",
};
const resultGroupStyle = {
  padding: "8px 0",
};
const resultTitleStyle = {
  fontSize: "14px",
  fontStyle: "normal",
  fontWeight: 600,
  lineHeight: "normal",
  color: blueLight,
  padding: "8px 0",
  cursor: "pointer",
};

const noResultWrapper = {
  display: "flex",
  flexDirection: "column",
  justifyContent: "center",
  alignItems: "center",
  padding: "50px 0px 25px 0px",
};
const titleWithBg = {
  display: "flex",
  alignItems: "center",
  height: "60px",
  backgroundImage: `url(searchIconBg.svg)`,
  backgroundSize: "80px 80px",
  backgroundRepeat: "no-repeat",
  backgroundPosition: "center",
};
const noResultTitle = {
  color: "#060606",
  fontWeight: 600,
  fontSize: "16px",
  marginTop: "-15px",
};
const noResultSuggestion = {
  color: blueLight,
  fontSize: "12px",
  lineHeight: "16px",
  fontWeight: 700,
  textTransform: "uppercase",
  display: "flex",
  alignItems: "center",
  padding: "2px 0",
  cursor: "pointer",
};
const uniqueKeyGenerator = (index: number, subIndex: number, title: string) => {
  return `${index}-${subIndex}-${title}`;
};

const useSearchEverythingHook = (props: useArrowNavigationProps<any>) => {
  const firstOptionItemHash = useMemo(() => {
    const head = _first(props?.options);
    if (head) {
      return props?.optionsIdGen(head);
    }
    return undefined;
  }, [props]);

  const [higlightedElement, setHighlighetedElement] = useState<string>(
    firstOptionItemHash ?? "",
  );

  useEffect(() => {
    if (firstOptionItemHash !== undefined && firstOptionItemHash !== "") {
      setHighlighetedElement(firstOptionItemHash);
    }
  }, [firstOptionItemHash]);

  const arrowNavProps = useArrowNavigation({
    ...props,
    hoveredItem: higlightedElement,
    setHoveredItem: setHighlighetedElement,
  });
  return arrowNavProps;
};

function SearchEverything({
  onChange,
  searchResults,
  onClear,
  searchTerm,
  setOpen,
  maxSearchResults,
}: SearchEverythingProps) {
  const navigate = useNavigate();

  const searchItems: SearchResultBase[] = useMemo(() => {
    const result =
      searchResults?.map(
        (item) =>
          item.sub?.map((subItem) => {
            return subItem;
          }) || [],
      ) ?? [];
    if (result && result.length > 0) {
      return result.flat();
    }
    return [];
  }, [searchResults]);

  const optionsIdGen = useCallback((sr: SearchResultItem) => {
    return `${sr.route?.replace("/", "_")}`;
  }, []);

  const { inputProps, optionPropsForItem, hoveredItem } =
    useSearchEverythingHook({
      onSelect: (elem) => {
        handleRedirect(elem);
      },
      options: searchItems || [],
      optionsIdGen,
      scrollToCenter: true,
      hoveredItem: "",
      setHoveredItem: () => {},
    });

  const subTitleStyle = (item: string) => {
    return {
      transition: "all 0.3s ease",
      borderRadius: "6px",
      background: _isEqual(item, hoveredItem) ? blueLight : seGrey,
      color: _isEqual(item, hoveredItem) ? "#FFFFFF" : "000000",
      padding: "12px 24px",
      fontSize: "14px",
      fontWeight: 500,
      lineHeight: "normal",
      fontStyle: "normal",
      margin: "2px 0",
      cursor: "pointer",
      display: "flex",
      alignItems: "center",
      "&:hover #enter-icon": {
        visibility: "visible",
      },
    };
  };
  const enterIconStyle = (item: string) => {
    return {
      marginLeft: "auto",
      visibility: _isEqual(item, hoveredItem) ? "visible" : "hidden",
    };
  };
  const handleChangeText = (value: string) => {
    onChange(value, maxSearchResults);
  };

  const handleRedirect = (sub: SearchResultItem) => {
    if (sub.route) {
      navigate(sub.route);
      if (setOpen) {
        setOpen(false);
      }
    }
  };

  return (
    <Box sx={{ background: "#FFFFFF" }}>
      <Box sx={searchBarStyle}>
        <Box sx={{ display: "flex", alignItems: "center" }}>
          <SearchIcon sx={{ fontSize: "40px", color: blueLight }} />
        </Box>
        <Box sx={searchInputStyle}>
          <InputBase
            placeholder="Search..."
            value={searchTerm}
            style={{ width: "100%" }}
            autoFocus
            // onKeyDown={handleKeyDown}
            onChange={(e) => handleChangeText(e.target.value)}
            {...inputProps}
          />
        </Box>
        <Box sx={closeCircleStyle} onClick={() => onClear()}>
          <XCircle size={20} color={seGrey2} />
        </Box>
      </Box>
      {/* search result not found */}
      {searchResults && searchResults.length === 0 && (
        <Box sx={noResultWrapper}>
          <Box sx={titleWithBg}>
            <Box sx={noResultTitle}>{`No results for "${searchTerm}"`}</Box>
          </Box>
          <Box sx={{ color: seGrey2, fontSize: "12px", padding: "5px 0" }}>
            Try searching for:
          </Box>
          <Box>
            <Box
              sx={noResultSuggestion}
              onClick={() => handleChangeText("workflow")}
            >
              <CaretDoubleRight color={"#000000"} /> Workflow names
            </Box>
            <Box
              sx={noResultSuggestion}
              onClick={() => handleChangeText("task")}
            >
              <CaretDoubleRight color={"#000000"} /> Task definitions
            </Box>
          </Box>
        </Box>
      )}
      {/* search results found */}
      {searchResults && searchResults.length > 0 && (
        <Box sx={resultsWrapperStyle}>
          {searchResults.map(
            (item, index) =>
              item.sub &&
              item.sub.length > 0 && (
                <Box sx={resultGroupStyle} key={index}>
                  <Box
                    sx={resultTitleStyle}
                    onClick={() => handleRedirect(item)}
                  >
                    {item.title}
                  </Box>
                  {item.sub &&
                    item.sub.length > 0 &&
                    item.sub.map((subItem, subIndex) => (
                      <Box
                        sx={subTitleStyle(optionsIdGen(subItem))}
                        key={uniqueKeyGenerator(index, subIndex, subItem.title)}
                        {...optionPropsForItem(subItem)}
                        onClick={() => handleRedirect(subItem)}
                      >
                        {subItem.title}
                        <Box
                          sx={enterIconStyle(
                            uniqueKeyGenerator(index, subIndex, subItem.title),
                          )}
                          id="enter-icon"
                        >
                          <img alt="enter-icon" src="/enterIcon.svg"></img>
                        </Box>
                      </Box>
                    ))}
                </Box>
              ),
          )}
        </Box>
      )}
    </Box>
  );
}

export default SearchEverything;
