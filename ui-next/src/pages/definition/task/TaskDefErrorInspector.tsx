import { colors } from "theme/tokens/variables";
import AccordionSummary from "@mui/material/AccordionSummary";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import MuiTypography from "components/MuiTypography";
import AccordionDetails from "@mui/material/AccordionDetails";
import List from "@mui/material/List";
import { ListItem } from "@mui/material";
import Accordion from "@mui/material/Accordion";

const TaskDefErrorInspector = ({
  error,
  title,
}: {
  error: { [key: string]: { message: string } };
  title?: string;
}) => {
  const errorKeys = error ? Object.keys(error) : [];

  return (
    <Accordion
      sx={{
        width: "100%",
        backgroundColor: (theme) => theme.palette.error.main,
        color: colors.white,
        "&:first-of-type": {
          borderTopLeftRadius: 0,
          borderTopRightRadius: 0,
        },
        "&.Mui-expanded": {
          margin: 0,
        },
      }}
    >
      <AccordionSummary
        expandIcon={<ExpandMoreIcon sx={{ color: colors.white }} />}
        aria-controls="panel1a-content"
        id="panel1a-header"
        sx={{
          "&.Mui-expanded": {
            minHeight: 48,
          },
          ".MuiAccordionSummary-content": {
            margin: 0,
            "&.Mui-expanded": {
              margin: 0,
            },
          },
        }}
      >
        <MuiTypography>
          {title ? `${title} ` : ""}Errors ({errorKeys.length})
        </MuiTypography>
      </AccordionSummary>
      <AccordionDetails
        sx={{
          backgroundColor: colors.gray02,
        }}
      >
        <List disablePadding dense>
          {errorKeys.map((key) => (
            <ListItem key={key} sx={{ alignItems: "start" }}>
              <MuiTypography component="span" color={colors.yellow09}>
                {key}
              </MuiTypography>
              :&nbsp;
              <MuiTypography component="span">
                {error[key]?.message}
              </MuiTypography>
            </ListItem>
          ))}
        </List>
      </AccordionDetails>
    </Accordion>
  );
};

export default TaskDefErrorInspector;
