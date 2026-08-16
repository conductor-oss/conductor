import MuiTypography from "components/ui/MuiTypography";

export const ExampleSearchQuery = () => {
  return (
    <MuiTypography
      sx={{
        fontFamily: 'Menlo, Monaco, "Courier New", monospace',
        fontWeight: "normal",
        fontSize: "12px",
      }}
    >
      <span>workflowType</span>
      <span style={{ color: "#778899" }}> = </span>
      <span style={{ color: "#ff0000" }}>'test'</span>
      <span style={{ color: "#778899" }}> AND </span>
      <span> status </span>
      <span style={{ color: "#778899" }}> in </span>
      <span style={{ color: "#0000ff" }}>(</span>
      <span style={{ color: "#ff0000" }}>'RUNNING'</span>
      <span> , </span>
      <span style={{ color: "#ff0000" }}>'COMPLETED'</span>
      <span style={{ color: "#0000ff" }}>)</span>
      <span style={{ color: "#778899" }}> AND </span>
      <span style={{ color: "#0000ff" }}> input</span>
      <span> . Age </span>
      <span style={{ color: "#778899" }}>= </span>
      <span style={{ color: "#098658" }}>10</span>
      <span style={{ color: "#778899" }}> AND </span>
      <span> createdBy </span>
      <span style={{ color: "#778899" }}> = </span>
      <span style={{ color: "#ff0000" }}>'mail@example.com'</span>
    </MuiTypography>
  );
};
