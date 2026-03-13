import ModelTrainingOutlined from "@mui/icons-material/ModelTrainingOutlined";
import AddCircleOutlineIcon from "@mui/icons-material/AddCircleOutline";
import MinimizeOutlinedIcon from "@mui/icons-material/MinimizeOutlined";
import InsertEmoticonOutlinedIcon from "@mui/icons-material/InsertEmoticonOutlined";
import AndroidOutlinedIcon from "@mui/icons-material/AndroidOutlined";
import { Box } from "@mui/material";
import { useRef, useState, useEffect } from "react";
import {
  greyBorder,
  greyText,
  greyText2,
  purple,
  white,
} from "theme/tokens/colors";
import ArrowBox from "components/v1/ArrowBox";
import { RoundedInput } from "components/v1/RoundedInput";
import ChatOutlinedIcon from "@mui/icons-material/ChatOutlined";

const boxStyle = (toggle: boolean) => {
  return {
    position: "fixed",
    left: 15,
    bottom: -4,
    borderRadius: "6px",
    width: "100%",
    maxWidth: "284px",
    maxHeight: "474px",
    height: toggle ? "100%" : "auto",
    boxShadow: "4px 4px 10px 0px rgba(89, 89, 89, 0.41)",
    padding: "4px",
    background: white,
  };
};

const headerStyle = {
  padding: "10px",
  display: "flex",
  alignItems: "center",
  fontSize: "14px",
  fontWeight: 600,
};

const controlStyle = {
  fontSize: "8px",
  display: "flex",
  alignItems: "center",
  position: "absolute",
  right: 10,
  cursor: "pointer",
};
const contentStyle = {
  marginTop: "20px",
  paddingBottom: "6px",
  height: "360px",
  overflow: "auto",
};
const footerStyle = {
  padding: "10px 7px",
  backgroundImage: "linear-gradient(180deg, white, white)",
};
const userStyle = {
  display: "flex",
  alignItems: "center",
};

const arrowBox1Args = {
  children:
    "Create a workflow to send flowers to my mother. Don't spend over $100.",
  position: "right",
  backgroundColor: "#F4EEFF",
  borderColor: purple,
};
const arrowBox2Args = {
  children: "Here is a workflow template for sending flowers.",
  position: "left",
};

const UserChat = () => {
  return (
    <Box sx={{ padding: "7px 0" }}>
      <Box
        sx={{
          // width: "90%",
          marginBottom: "13px",
          paddingLeft: "15px",
          display: "flex",
          justifyContent: "flex-end",
        }}
      >
        <ArrowBox {...arrowBox1Args} />
      </Box>
      <Box sx={{ ...userStyle, justifyContent: "flex-end" }}>
        <Box
          sx={{
            color: purple,
          }}
        >
          <InsertEmoticonOutlinedIcon sx={{ width: "20px" }} />
        </Box>
        <Box sx={{ padding: "0 4px" }}>
          <Box sx={{ fontSize: "12px" }}>Me</Box>
          <Box
            sx={{
              fontSize: "8px",
              lineHeight: "normal",
              color: greyText2,
            }}
          >
            1 min ago
          </Box>
        </Box>
      </Box>
    </Box>
  );
};

const CoPilotChat = () => {
  return (
    <Box sx={{ padding: "7px 0" }}>
      <Box
        sx={{
          marginBottom: "13px",
          paddingRight: "15px",
          display: "flex",
          justifyContent: "flex-end",
        }}
      >
        <ArrowBox {...arrowBox2Args} />
      </Box>
      <Box sx={userStyle}>
        <Box sx={{ padding: "0 4px" }}>
          <Box sx={{ fontSize: "12px" }}>CoPilot</Box>
          <Box
            sx={{
              fontSize: "8px",
              lineHeight: "normal",
              color: greyText2,
            }}
          >
            Just now
          </Box>
        </Box>
        <Box sx={{ color: purple }}>
          <AndroidOutlinedIcon sx={{ width: "20px" }} />
        </Box>
      </Box>
    </Box>
  );
};

function CoPilot() {
  const [toggle, setToggle] = useState(false);
  const contentRef = useRef<HTMLElement>(null);
  const handleToggle = () => {
    setToggle(!toggle);
  };

  useEffect(() => {
    if (contentRef.current) {
      contentRef.current.scroll({
        top: contentRef.current?.scrollHeight,
        behavior: "smooth",
      });
    }
  }, [toggle]);

  return (
    <Box sx={boxStyle(toggle)}>
      <Box sx={headerStyle}>
        <Box sx={{ display: "flex", alignItems: "center" }}>
          <ModelTrainingOutlined />
        </Box>
        <Box sx={{ padding: "0 7px" }}>CoPilot</Box>
        {/* maximize */}
        <Box
          sx={{ ...controlStyle, color: toggle ? greyText : purple }}
          onClick={handleToggle}
        >
          <Box sx={{ display: "flex", alignItems: "center" }}>
            {toggle ? (
              <MinimizeOutlinedIcon
                sx={{ width: "20px", marginTop: "-12px" }}
              />
            ) : (
              <AddCircleOutlineIcon sx={{ width: "16px" }} />
            )}
          </Box>
          <Box sx={{ padding: "0 2px" }}> {toggle ? "Minimize" : "Open"}</Box>
        </Box>
      </Box>
      {toggle && (
        <>
          <Box sx={contentStyle} ref={contentRef}>
            <UserChat />
            <CoPilotChat />
          </Box>
          <Box sx={footerStyle}>
            <RoundedInput
              autoFocus
              icon={<ChatOutlinedIcon sx={{ color: greyBorder }} />}
            />
          </Box>
        </>
      )}
    </Box>
  );
}

export default CoPilot;
