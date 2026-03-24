export const mergeInNodeData = (node, values) => ({
  ...node,
  data: { ...node.data, ...values },
});

export const applyNodeSelectionHelpr = (nodes, selectedNodeIdx) =>
  selectedNodeIdx == null
    ? nodes
    : nodes.map((node, idx) =>
        mergeInNodeData(node, { selected: idx === selectedNodeIdx }),
      );
