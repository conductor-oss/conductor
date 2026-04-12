import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import Modal from "@mui/material/Modal";
import { CSSObject } from "@mui/material/styles";
import { useState } from "react";

import { Button, Input, Paper, Typography } from "components";
import RunIcon from "components/icons/RunIcon";
import CPlusPlusLogo from "images/svg/c-plus-plus-logo.svg";
import CSharpLogo from "images/svg/c-sharp-logo.svg";
import GoLangLogo from "images/svg/go-lang-logo.svg";
import JavaLogo from "images/svg/java-logo.svg";
import JavaScriptLogo from "images/svg/javascript-logo.svg";
import PythonLogo from "images/svg/python-logo.svg";
import { orkesBrandN200, orkesBrandS600 } from "theme/tokens/colors";
import CircleCheckIcon from "components/icons/CircleCheckIcon";

const inputStyle = {
  "& .MuiInputBase-root": {
    minHeight: "auto",
  },
  "& .MuiOutlinedInput-notchedOutline": {
    border: "none",
  },
  "& .MuiInputBase-input": {
    p: 0,
  },
};

const goals = [
  { id: 1, label: "Evaluating Orkes for my company" },
  { id: 2, label: "Learn about the features and functionalities" },
  { id: 3, label: "Build an application for a use case" },
  {
    id: 4,
    label: (
      <Box sx={{ width: "100%" }}>
        <Typography>Other - please specify</Typography>
        <Input fullWidth placeholder="(20-30 words or less)" sx={inputStyle} />
      </Box>
    ),
  },
];

const purposes = [
  { id: 1, label: "Microservices based applications" },
  { id: 2, label: "Data pipelines" },
  { id: 3, label: "Gen-AI powered workflows" },
  {
    id: 4,
    label: (
      <Box sx={{ width: "100%" }}>
        <Typography>Other - please specify</Typography>
        <Input fullWidth placeholder="(20-30 words or less)" sx={inputStyle} />
      </Box>
    ),
  },
];

const languages = [
  {
    id: 1,
    label: "Java",
    logo: JavaLogo,
  },
  {
    id: 2,
    label: "Python",
    logo: PythonLogo,
  },
  {
    id: 3,
    label: "C Sharp",
    logo: CSharpLogo,
  },
  {
    id: 4,
    label: "C ++",
    logo: CPlusPlusLogo,
  },
  {
    id: 5,
    label: "GoLang",
    logo: GoLangLogo,
  },
  {
    id: 6,
    label: "JavaScript",
    logo: JavaScriptLogo,
  },
];

const paperStyle: CSSObject = {
  position: "absolute",
  top: "50%",
  left: "50%",
  transform: "translate(-50%, -50%)",
  p: "20px 40px",
  overflow: "auto",
  maxHeight: "95%",
  outline: "none",
};

const itemStyle: CSSObject = {
  position: "relative",
  display: "flex",
  alignItems: "center",
  justifyContent: "space-between",
  cursor: "pointer",
  border: `1px solid ${orkesBrandN200}`,
  borderRadius: "6px",
  p: "5px 13px",
  width: "100%",
  height: "100%",
  gap: "5px",
};

const selectedStyle: CSSObject = {
  borderColor: orkesBrandS600,
  borderWidth: "2px",
};

const titleStyle: CSSObject = {
  fontSize: "20px",
  fontWeight: 700,
  my: 1,
};

export default function OnboardingQuiz() {
  const [open, setOpen] = useState(true);
  const [selectedGoals, setSelectedGoals] = useState<number[]>([]);
  const [selectedPurposes, setSelectedPurposes] = useState<number[]>([]);
  const [selectedLanguages, setSelectedLanguages] = useState<number[]>([]);

  const isValid = selectedGoals && selectedPurposes && selectedLanguages;

  const handleState = (currentState: number[], selectedItem: number) => {
    const result = [...currentState];
    const selectedIndex = result.findIndex((item) => item === selectedItem);

    if (selectedIndex > -1) {
      result.splice(selectedIndex, 1);
    } else {
      result.push(selectedItem);
    }

    return result;
  };

  return (
    <Modal open={open}>
      <Paper sx={paperStyle}>
        <Typography textAlign="center" fontSize={20}>
          Help us jump start your work
        </Typography>

        <Grid container sx={{ width: "100%" }} spacing={4} mt={3}>
          <Grid size={12}>
            <Typography sx={titleStyle}>WHAT IS YOUR GOAL?</Typography>
          </Grid>

          {goals.map(({ id, label }) => {
            const isActive = selectedGoals.includes(id);

            return (
              <Grid key={id} size={12}>
                <Box
                  sx={[itemStyle, isActive && selectedStyle]}
                  onClick={() =>
                    setSelectedGoals((currentState) =>
                      handleState(currentState, id),
                    )
                  }
                >
                  {label}
                  {isActive && <CircleCheckIcon color={orkesBrandS600} />}
                </Box>
              </Grid>
            );
          })}
        </Grid>

        <Grid container sx={{ width: "100%" }} spacing={4} mt={1}>
          <Grid size={12}>
            <Typography sx={titleStyle}>
              WHAT ARE YOU LOOKING TO BUILD WITH ORKES?
            </Typography>
          </Grid>

          {purposes.map(({ id, label }) => {
            const isActive = selectedPurposes.includes(id);

            return (
              <Grid
                key={id}
                size={{
                  xs: 12,
                  sm: 12,
                  md: 6,
                }}
              >
                <Box
                  sx={[itemStyle, isActive && selectedStyle]}
                  onClick={() =>
                    setSelectedPurposes((currentState) =>
                      handleState(currentState, id),
                    )
                  }
                >
                  {label}
                  {isActive && <CircleCheckIcon color={orkesBrandS600} />}
                </Box>
              </Grid>
            );
          })}
        </Grid>

        <Grid container sx={{ width: "100%" }} spacing={4} mt={1}>
          <Grid size={12}>
            <Typography sx={titleStyle}>
              WHAT IS YOUR PREFERRED LANGUAGE FOR CODING?
            </Typography>
          </Grid>

          {languages.map(({ id, label, logo }) => {
            const isActive = selectedLanguages.includes(id);

            return (
              <Grid key={id}>
                <Box
                  sx={[
                    { ...itemStyle, flexDirection: "column", width: "82px" },
                    isActive && selectedStyle,
                  ]}
                  onClick={() =>
                    setSelectedLanguages((currentState) =>
                      handleState(currentState, id),
                    )
                  }
                >
                  <img
                    src={logo}
                    alt={label}
                    style={{ width: "47px", height: "47px" }}
                  />
                  <Box>{label}</Box>
                  {isActive && (
                    <CircleCheckIcon
                      color={orkesBrandS600}
                      style={{
                        position: "absolute",
                        right: -10,
                        top: -10,
                        zIndex: 1,
                      }}
                    />
                  )}
                </Box>
              </Grid>
            );
          })}

          <Grid key={7} size={12}>
            <Box
              sx={[
                { ...itemStyle },
                selectedLanguages.includes(7) && selectedStyle,
              ]}
              onClick={() =>
                setSelectedLanguages((currentState) =>
                  handleState(currentState, 7),
                )
              }
            >
              <Box sx={{ width: "100%" }}>
                <Typography>Other - please specify</Typography>
                <Input
                  fullWidth
                  placeholder="(20-30 words or less)"
                  sx={inputStyle}
                />
              </Box>
              {selectedLanguages.includes(7) && (
                <CircleCheckIcon
                  color={orkesBrandS600}
                  style={{
                    position: "absolute",
                    right: -10,
                    top: -10,
                    zIndex: 1,
                  }}
                />
              )}
            </Box>
          </Grid>
        </Grid>

        <Box sx={{ display: "flex", justifyContent: "end", mt: 10 }}>
          <Button
            disabled={!isValid}
            startIcon={<RunIcon />}
            sx={{ width: "auto" }}
            onClick={() => setOpen(false)}
          >
            Start exploring
          </Button>
        </Box>
      </Paper>
    </Modal>
  );
}
