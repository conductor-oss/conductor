import { Box } from "@mui/material";

const HelperText = ({ children }) => {
  return (
    <Box pt={2} style={{ opacity: 0.5 }}>
      {children}
    </Box>
  );
};

export default HelperText;
