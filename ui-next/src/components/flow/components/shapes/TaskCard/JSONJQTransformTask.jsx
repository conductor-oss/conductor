import { useEffect } from "react";
import Prism from "prismjs";
import "prismjs/themes/prism-coy.css";

const JSONJQTransformTask = ({ nodeData }) => {
  const { task } = nodeData;

  useEffect(() => {
    Prism.highlightAll();
  }, []);

  const {
    inputParameters: { queryExpression },
  } = task;

  return (
    <code
      component="code"
      style={{
        marginTop: "1em",
        borderRadius: "6px",
        background: "#eeeeee",
        fontFamily: "monospace",
        fontSize: "0.9em",
        overflowX: "hidden",
        overflowY: "auto",
        wordBreak: "break-word",
        marginBottom: "0",
        height: "50px",
        // Prism's rules are very specific
        // e.g.: `:not(pre) > code[class*="language-"]` (!)
        display: "block",
        margin: "10px 0 0 0",
      }}
      // TODO: Support other languages according to Evaluator type.
      className="language-js"
    >
      {typeof queryExpression === "string" ? queryExpression : ""}
    </code>
  );
};

export default JSONJQTransformTask;
