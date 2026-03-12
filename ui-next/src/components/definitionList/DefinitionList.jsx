import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import { styled } from "@mui/material/styles";

const StyledTableBody = styled(TableBody)(({ theme }) => ({
  "& tr:first-child": {
    borderTopColor: theme.palette.divider,
    borderTopStyle: "solid",
    borderTopWidth: "1px",
  },
}));

const DefinitionList = ({ children }) => (
  <Table>
    <StyledTableBody>{children}</StyledTableBody>
  </Table>
);

export default DefinitionList;
