import { styled } from "@mui/material";
import { grey } from "@mui/material/colors";

const Puller = styled("div")(({ theme }) => ({
  width: 30,
  height: 5,
  backgroundColor: grey[400],
  borderRadius: 3,
  position: "absolute",
  top: 8,
  left: "calc(50% - 15px)",
  ...theme.applyStyles("dark", {
    backgroundColor: grey[900],
  }),
}));

export default Puller;
