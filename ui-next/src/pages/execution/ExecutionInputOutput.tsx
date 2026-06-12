import GridViewOutlinedIcon from "@mui/icons-material/GridViewOutlined";
import ListOutlinedIcon from "@mui/icons-material/ListOutlined";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import { CSSProperties, useState } from "react";

import { ReactJson } from "components";
import MuiIconButton from "components/ui/buttons/MuiIconButton";
import { colors } from "theme/tokens/variables";

type DataType = {
  title: string;
  src: Record<string, unknown>;
  hidden: boolean;
  style: CSSProperties;
};

interface InputOutputProp {
  data: DataType[];
  execution: Record<string, unknown>;
  isEditable?: boolean;
  handleUpdate?: (value: string) => void;
}

export default function InputOutput({
  data,
  execution,
  isEditable = false,
  handleUpdate,
}: InputOutputProp) {
  const [isDisplayList, setIsDisplayList] = useState(false);
  const [fullScreen, setFullScreen] = useState<DataType[]>([]);

  const handleFullScreen = (item: DataType) => {
    if (fullScreen.length > 0) {
      setFullScreen([]);
      return;
    }
    setFullScreen([item]);
  };

  const customEditorOptions = {
    minimap: { enabled: false },
    lightbulb: { enabled: false },
    renderLineHighlight: "none",
    overviewRulerLanes: 0,
    hideCursorInOverviewRuler: true,
    scrollbar: {
      // this property is added because it was not allowing us to scroll when mouse pointer is over this component
      alwaysConsumeMouseWheel: false,
      verticalSliderSize: 9,
      horizontalSliderSize: 9,
      useShadows: true,
    },
  };

  const renderItems = (items: DataType[]) => {
    return items.map((item, index) =>
      item.hidden ? null : (
        <Grid
          key={index}
          sx={{
            height: "100%",
          }}
          size={{
            xs: 12,
            md: isDisplayList ? 12 : 12 / items.length,
          }}
        >
          <Box sx={{ pt: 5, height: "100%" }}>
            <ReactJson
              isEditable={isEditable}
              handleUpdate={handleUpdate}
              src={item.src}
              title={item.title}
              // theme={theme}
              workflowName={execution.workflowName as string}
              editorHeight="100%"
              fullScreen={fullScreen}
              item={item}
              handleFullScreen={handleFullScreen}
              customOptions={customEditorOptions}
            />
          </Box>
        </Grid>
      ),
    );
  };

  const isManyItems = data.length > 1;

  return (
    <>
      {isManyItems && (
        <Box display="flex" justifyContent="end">
          <Stack direction="row" spacing={0.5}>
            <MuiIconButton onClick={() => setIsDisplayList(true)}>
              <ListOutlinedIcon
                sx={{ color: isDisplayList ? colors.purple : null }}
              />
            </MuiIconButton>
            <MuiIconButton onClick={() => setIsDisplayList(false)}>
              <GridViewOutlinedIcon
                sx={{ color: isDisplayList ? null : colors.purple }}
              />
            </MuiIconButton>
          </Stack>
        </Box>
      )}

      <Grid
        container
        spacing={isManyItems ? 5 : 0}
        sx={{
          width: "100%",
          mt: isManyItems ? -2 : null,
          p: 3,
          pt: isManyItems ? 0 : 3,
          flex: 1,
          overflow: "auto",
        }}
      >
        {renderItems(fullScreen.length > 0 ? fullScreen : data)}
      </Grid>
    </>
  );
}
