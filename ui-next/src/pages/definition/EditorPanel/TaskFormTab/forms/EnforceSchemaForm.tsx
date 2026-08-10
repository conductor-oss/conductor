import React from "react";
import { Box, FormControlLabel, Link, Switch, Typography } from "@mui/material";
interface EnforceSchemaProps {
  onChange: (value: boolean) => void;
  value?: boolean;
  defaultValue?: boolean;
  showEnforceSchemaSwitch?: boolean;
}

export const EnforceSchema = ({
  onChange,
  value,
  defaultValue = false,
  showEnforceSchemaSwitch = false,
}: EnforceSchemaProps) => {
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange(e.target.checked);
  };

  return (
    <>
      {showEnforceSchemaSwitch && (
        <FormControlLabel
          sx={{ mt: 1 }}
          labelPlacement="end"
          checked={value ?? defaultValue}
          control={<Switch color="primary" onChange={handleChange} />}
          label={
            <Typography fontWeight={600} color="#767676">
              Enforce Schema
            </Typography>
          }
        />
      )}
      <Box>
        <Typography component={"span"} style={{ opacity: 0.5 }}>
          Select input and/or output schemas to validate task data and ensure
          data integrity throughout the workflow execution.
        </Typography>
        <Link
          sx={{
            fontWeight: 400,
            fontSize: "12px",
            marginLeft: 1,
          }}
          target="_blank"
          href={`https://orkes.io/content/developer-guides/schema-validation`}
          rel="noreferrer"
        >
          {"Learn more."}
        </Link>
      </Box>
    </>
  );
};
