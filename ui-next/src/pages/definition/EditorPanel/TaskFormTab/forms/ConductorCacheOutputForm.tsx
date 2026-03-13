import { Box, FormControlLabel, Grid, Link, Switch } from "@mui/material";
import ConductorInput from "components/v1/ConductorInput";
import { path as _path } from "lodash/fp";
import { FunctionComponent, useState } from "react";
import { updateField } from "utils/fieldHelpers";
import ConductorFlexibleAutoCompleteVariables from "./ConductorFlexibleAutoCompleteVariables";

interface ConductorCacheOutputProps {
  onChange: (value: any) => void;
  taskJson: any;
}

const ttlPath = "cacheConfig.ttlInSecond";
const cacheKeyPath = "cacheConfig.key";

export const ConductorCacheOutput: FunctionComponent<
  ConductorCacheOutputProps
> = ({ onChange, taskJson }) => {
  const cacheKeyOptions = taskJson?.inputParameters
    ? Object.keys(taskJson?.inputParameters).map((item) => `\${${item}}`)
    : [];
  const ttl = _path(ttlPath, taskJson);
  const cacheKey = _path(cacheKeyPath, taskJson);
  const changeTtl = (value: any) => {
    onChange(updateField(ttlPath, value, taskJson));
  };
  const changeCacheKey = (value: any) => {
    onChange(updateField(cacheKeyPath, value, taskJson));
  };
  const [show, setShow] = useState(ttl ? true : false);
  const handleSetShow = () => {
    if (show) {
      onChange({ ...taskJson, cacheConfig: undefined });
      setShow(false);
    } else {
      setShow(true);
    }
  };
  return (
    <Box>
      <Grid
        container
        spacing={2}
        marginTop={1}
        justifyContent="flex-start"
        alignItems={"flex-start"}
        sx={{ width: "100%", mt: 3 }}
      >
        <Grid size={12}>
          <FormControlLabel
            labelPlacement="end"
            checked={show}
            control={<Switch color="primary" onChange={handleSetShow} />}
            label={
              <Box sx={{ display: "flex", gap: 2, alignItems: "center" }}>
                <Box sx={{ fontWeight: 600, color: "#767676" }}>
                  Cache Output
                </Box>
                <Link
                  sx={{ fontSize: "12px", fontWeight: 400 }}
                  target="_blank"
                  href={`https://orkes.io/content/faqs/task-cache-output`}
                  rel="noreferrer"
                >
                  Learn more
                </Link>
              </Box>
            }
          />
        </Grid>
      </Grid>
      <Box style={{ opacity: 0.5 }}>
        When turned on, cache outputs can be saved for reuse in subsequent task
        executions.
      </Box>
      {show && (
        <Grid
          container
          spacing={2}
          marginTop={1}
          // marginBottom={2}
          justifyContent="flex-start"
          alignItems={"flex-start"}
        >
          <Grid size={9}>
            <ConductorFlexibleAutoCompleteVariables
              label="Cache key"
              options={cacheKeyOptions}
              value={cacheKey}
              onChange={changeCacheKey}
            />
          </Grid>
          <Grid size={3}>
            <ConductorInput
              label="TTL (in seconds)"
              value={ttl}
              fullWidth
              onTextInputChange={changeTtl}
              type="number"
              placeholder="e.g.: 3"
            />
          </Grid>
        </Grid>
      )}
    </Box>
  );
};
