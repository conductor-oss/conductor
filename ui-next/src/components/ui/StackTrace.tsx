import React, { useState } from "react";

export function StackTraceComponent({ stacktrace }: { stacktrace: string }) {
  const lines = stacktrace.split("\n");
  const head = lines.slice(0, 3);
  const tail = lines.slice(3);

  const [collapsed, setCollapsed] = useState(true);

  const toggleCollapsed = () => {
    setCollapsed(!collapsed);
  };

  const linkStyle = {
    cursor: "pointer",
    color: "#1976d2",
  };

  const tailElement = (
    <span style={{ display: collapsed ? "none" : "inline" }}>
      {tail.join("\n")}
      <br />
    </span>
  );
  const toggleElement = (
    <span
      onClick={toggleCollapsed}
      style={{ display: lines.length > 3 ? "inherit" : "none", ...linkStyle }}
    >
      {collapsed ? `${tail.length} more lines` : `Hide ${tail.length} lines`}
    </span>
  );

  return (
    <code style={{ margin: 0, whiteSpace: "pre" }}>
      {head.join("\n")}
      <br />
      {tailElement}&#9;{toggleElement}
    </code>
  );
}
