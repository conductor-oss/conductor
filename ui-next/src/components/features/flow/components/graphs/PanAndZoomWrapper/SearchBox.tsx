import { Box } from "@mui/material";
import { FlowEvents, useFlowMachine } from "components/features/flow/state";

import ClickAwayListener from "@mui/material/ClickAwayListener";

import { FunctionComponent, useMemo, useState } from "react";

import { ActorRef } from "xstate";
import { usePanAndZoomActor } from "./state";
import { AdvancedSearchFieldPopper } from "components/inputs/AdvancedSearchFieldPopper";
import { isPseudoTask } from "utils/utils";
import { NodeData } from "reaflow";
import { useHotkeys } from "react-hotkeys-hook";
import { Key } from "ts-key-enum";

interface SearchBoxProps {
  flowActor: ActorRef<FlowEvents>;
  anchorEl: any;
}

export const SearchBox: FunctionComponent<SearchBoxProps> = ({
  flowActor,
  anchorEl,
}) => {
  const [{ selectNode }, { nodes, panAndZoomActor }] =
    useFlowMachine(flowActor);
  const [
    { viewportSize },
    { handleToggleSearchField, handleSelectSearchResult },
  ] = usePanAndZoomActor(panAndZoomActor);

  const [filteredOptionsCount, setFilteredOptionsCount] = useState(0);
  const [hoveredItem, setHoveredItem] = useState<string>("");
  const [searchTerm, setSearchTerm] = useState<string>("");

  const suggestions = nodes.reduce(
    (
      accumulator: { taskName: string; taskRef: string; type: string }[],
      item: NodeData,
    ) => {
      if (item.data.task && !isPseudoTask(item.data.task)) {
        accumulator.push({
          taskName: item.text,
          taskRef: item.id,
          type: item.data.task.type ?? "",
        });
      }
      return accumulator;
    },
    [],
  );

  const filteredOptions = useMemo(() => {
    if (suggestions) {
      const newFilteredOptions = suggestions.filter((option) =>
        `${option.taskName}${option.taskRef}${option.type}`
          .toLowerCase()
          .includes(searchTerm.toLowerCase()),
      );
      return newFilteredOptions;
    } else return [];
  }, [suggestions, searchTerm]);

  const handleClickSearchResult = (val: string | null) => {
    const [selectedTask] = nodes.filter((item) => item.id === val);
    if (selectedTask) {
      selectNode(selectedTask);
      handleSelectSearchResult(viewportSize?.width, viewportSize?.height);
    }
  };

  useHotkeys(Key.Escape, handleToggleSearchField, {
    enableOnFormTags: ["INPUT"],
  });

  return (
    <ClickAwayListener onClickAway={handleToggleSearchField}>
      <Box>
        <AdvancedSearchFieldPopper
          open={true}
          options={filteredOptions}
          anchorEl={anchorEl.current}
          handleClose={handleToggleSearchField}
          onSelectItem={handleClickSearchResult}
          filteredOptionsCount={filteredOptionsCount}
          setFilteredOptionsCount={setFilteredOptionsCount}
          hoveredItem={hoveredItem}
          setHoveredItem={setHoveredItem}
          searchTerm={searchTerm}
          setSearchTerm={setSearchTerm}
          totalOptionsCount={suggestions.length}
        />
      </Box>
    </ClickAwayListener>
  );
};
