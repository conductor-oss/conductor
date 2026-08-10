export const formContainerStyle = {
  marginTop: 5,
  marginBottom: 5,
  display: "flex",
  flexWrap: "wrap",
  width: "100%",
  gap: "20px",
};

export const boxStyle = {
  display: "flex",
  alignItems: "center",
};

export const textFieldStyle = {
  ">div": {
    width: 220,
  },
};

export type Props = {
  onRemove?: () => void;
  index?: number;
  payload?: any;
  handleChangeAction?: any;
};

export const formBoxStyle = {
  width: "calc(100% - 180px)",
  "@media screen and (max-width: 860px)": {
    width: "100%",
  },
};

export const flatMapStyle = {
  maxWidth: "100%",
};

export const removeButtonStyle = {
  height: "fit-content",
  position: "relative",
  bottom: "0px",
  right: "40px",
  background: "#e3e3e3",
};
