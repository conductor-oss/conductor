import React, { FunctionComponent } from "react";
import { Box, Switch } from "@mui/material";

interface EncodeProps {
  onChange: (value: boolean) => void;
  value: boolean;
  title?: string;
}

export const Encode: FunctionComponent<EncodeProps> = ({
  onChange,
  value,
  title = "Encode",
}) => {
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(e.target.checked);
  };

  return (
    <>
      <Box>
        <Box sx={{ fontWeight: 600, color: "#767676", ml: -2.2 }}>
          <Switch color="primary" checked={value} onChange={handleChange} />
          {title}
        </Box>
        <Box sx={{ opacity: 0.5 }}>
          Automatically encodes query parameters in the URI before sending the
          HTTP request.
        </Box>
      </Box>
    </>
  );
};
