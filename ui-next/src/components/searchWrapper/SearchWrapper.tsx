import SearchIcon from "@mui/icons-material/Search";
import { Stack } from "@mui/material";
import MuiTypography from "components/MuiTypography";
import SearchEverything from "components/SearchEverything";
import UIModal from "components/UIModal";
import HotKeysButton from "components/Sidebar/HotKeysButton";
import { Fragment } from "react";
import { colors } from "theme/tokens/variables";
import { useSearchMachine } from "./state/hook";

export interface SearchModalProps {
  open: boolean;
  setOpen: (val: boolean) => void;
}
const props = {
  title: "Search",
  icon: <SearchIcon />,
  description: "Search the platform for all your definitions in one place",
  enableCloseButton: true,
};

function SearchEverythingModal({ open, setOpen }: SearchModalProps) {
  const [{ searchTerm, searchResults }, { setSearchTerm }] = useSearchMachine();

  return (
    <UIModal
      {...props}
      disableRestoreFocus
      open={open}
      setOpen={setOpen}
      PaperProps={{ sx: { position: "fixed", top: 50, m: 0 } }}
      footerChildren={
        <>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <HotKeysButton
              shortcuts={[<Fragment key="enter">&#8629;</Fragment>]}
            />
            <MuiTypography component="span" fontSize={12}>
              to select
            </MuiTypography>
          </Stack>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <HotKeysButton
              shortcuts={[
                <Fragment key="up">&#8593;</Fragment>,
                <Fragment key="down">&#8595;</Fragment>,
              ]}
            />
            <MuiTypography component="span" fontSize={12}>
              to navigate
            </MuiTypography>
          </Stack>
          <Stack direction="row" spacing={1.5} alignItems="center">
            <HotKeysButton shortcuts={["ESC"]} />
            <MuiTypography component="span" fontSize={12}>
              to close
            </MuiTypography>
          </Stack>
        </>
      }
      footerSx={{
        p: 1.5,
        justifyContent: "center",
        color: colors.greyText2,
        backgroundColor: colors.sidebarBarelyPastWhite,
        borderTop: "none",
      }}
    >
      <SearchEverything
        onChange={setSearchTerm!}
        searchTerm={searchTerm ?? ""}
        onClear={() => setSearchTerm!("")}
        {...(searchResults && { searchResults: searchResults })}
        setOpen={setOpen}
        maxSearchResults={5}
      />
    </UIModal>
  );
}

export default SearchEverythingModal;
