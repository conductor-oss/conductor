/** CardLabel-matching type badge (same CSS as CardLabel.jsx). */
export function TypeBadge({ label }: { label: string }) {
  if (!label) return null;
  return (
    <div
      style={{
        position: "absolute",
        top: "0px",
        right: "0px",
        height: "fit-content",
        padding: "4px 8px",
        fontSize: "0.8em",
        background: "#dddddd",
        color: "black",
        borderRadius: "5px",
        marginLeft: "8px",
      }}
    >
      {label}
    </div>
  );
}
