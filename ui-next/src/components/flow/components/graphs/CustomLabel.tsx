import { FunctionComponent } from "react";
import { getFlowTheme } from "components/flow/theme";
import { EdgeData, LabelProps, NodeData, EdgeChildProps } from "reaflow";
import { EDGE_SPACING } from "./PanAndZoomWrapper/constants";

const HORIZONTAL_PADDING = 10;

type SelectEdgePram = { edge: EdgeData };

interface CustomLabelProps extends LabelProps {
  selectEdge: (edgeData: SelectEdgePram) => void;
  nodes: NodeData[];
  edgeChildProps: EdgeChildProps;
}

export const CustomLabel: FunctionComponent<Partial<CustomLabelProps>> = ({
  text = "",
  x = 0,
  y = 0,
  originalText = "",
  edgeChildProps: edgeProps,
  selectEdge = (_nonEdge) => {},
  ...labelProps
}) => {
  const label = text;
  const isDefault = edgeProps?.edge.data?.isDefault ?? false;
  const theme = getFlowTheme();

  // This should be `x + labelProps.width / 2`,
  // but the width is already divided by 2 in Reaflow, see:
  // https://github.com/reaviz/reaflow/blob/master/src/layout/elkLayout.ts#L262
  const labelPropsWidth = labelProps?.width || 0;
  const centeredX = x + labelPropsWidth;

  const labelSize = {
    // Multiplying width * 2 since it's already divided by 2 in Reaflow.
    // HORIZONTAL_PADDING is added to both sides to make sure the label is not cut off.
    width: labelPropsWidth * 2 + HORIZONTAL_PADDING * 2,
    height: 30,
    x: centeredX,
    y: y,
  };

  const onClickLabel = () => {
    if (edgeProps?.edge) selectEdge({ edge: edgeProps.edge });
  };

  return (
    <g
      transform={`translate(${labelSize.x - EDGE_SPACING / 2}, ${labelSize.y})`}
    >
      <foreignObject
        style={{
          overflow: "visible",
        }}
        width={EDGE_SPACING}
        height={labelSize.height}
      >
        <div
          title={originalText}
          style={{
            display: "block",
            pointerEvents: "auto",
            minHeight: labelSize.height,
            lineHeight: `${labelSize.height}px`,
            backgroundColor: isDefault
              ? theme.decisionOperator.caseLabel.defaultCaseBackground
              : theme.decisionOperator.caseLabel.background,
            textAlign: "center",
            borderRadius: "8px",
            padding: "0 10px",
            boxShadow: "0 0 10px rgba(0, 0, 0, 0.5)",
            textOverflow: "ellipsis",
            wordWrap: "break-word",
            overflow: "hidden",
            width: EDGE_SPACING,
          }}
          onClick={onClickLabel}
        >
          {label}
        </div>
      </foreignObject>
    </g>
  );
};
