import { colors } from "../theme/variables";

export default {
  wrapper: {
    overflowY: "scroll",
    overflowX: "hidden",
    height: "100%",
  },
  padded: {
    padding: 30,
  },
  header: {
    backgroundColor: colors.gray14,
    paddingLeft: 50,
    paddingTop: 20,
    "@media (min-width: 1920px)": {
      paddingLeft: 200,
    },
  },
  tabContent: {
    paddingTop: 20,
    paddingRight: 50,
    paddingBottom: 50,
    paddingLeft: 50,
    "@media (min-width: 1920px)": {
      paddingLeft: 200,
    },
  },
};
