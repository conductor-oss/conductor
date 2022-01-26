import React from "react";
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle } from "@material-ui/core";
import { makeStyles } from "@material-ui/styles";
import Text from "./Text";

const useStyles = makeStyles({
  confirmationMessage: {
    color: "black",
    opacity: 0.8,
    paddingLeft: 10,
    fontSize: 15,
    lineHeight: 1.5,
    "& p": {
      fontSize: 15,
      fontWeight: "normal"
    },
    "& svg": {
      fontSize: 15,
    }
  }
});

export default function ({
  header = "Confirmation",
  message = "Please confirm",
  handleConfirmationValue,
}) {
  const classes = useStyles();
  return (
    <Dialog fullWidth maxWidth="sm" open onClose={() => handleConfirmationValue(false)}>
      <DialogTitle>{header}</DialogTitle>
      <DialogContent>
        <Box mt={4}>
          <Text className={classes.confirmationMessage} style={{ marginRight: 10 }}>
            {message}
          </Text>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => handleConfirmationValue(false)}>Cancel</Button>
        <Button onClick={() => handleConfirmationValue(true)}>Confirm</Button>
      </DialogActions>
    </Dialog>
  );
}
