const CardAttemptsBadge = ({ attempts }) => {
  return (
    <div
      style={{
        position: "absolute",
        bottom: "-15px",
        right: "-15px",
        borderRadius: "30px",
        width: "30px",
        height: "30px",
        background: "#f0f0f0",
        color: "#111111",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        boxShadow: "0 0 4px black",
        fontSize: "10pt",
      }}
    >
      {attempts}
    </div>
  );
};

export default CardAttemptsBadge;
