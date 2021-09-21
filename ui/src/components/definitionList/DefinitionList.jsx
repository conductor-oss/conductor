import withStyles from "@material-ui/core/styles/withStyles";
import Table from "@material-ui/core/Table";
import TableBody from "@material-ui/core/TableBody";
import * as React from "react";

const DefinitionList = ({ children, classes }) => (
  <Table>
    <TableBody className={classes.root}>{children}</TableBody>
  </Table>
);

export default withStyles((theme) => ({
  root: {
    "& tr:first-child": {
      borderTopColor: theme.palette.divider,
      borderTopStyle: "solid",
      borderTopWidth: "1px",
    },
  },
}))(DefinitionList);
