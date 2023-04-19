import React from "react";
import PropTypes from "prop-types";
import { graphlib, render as dagreD3Render, intersect } from "dagre-d3";
import * as d3 from "d3";
import _ from "lodash";
import { withResizeDetector } from "react-resize-detector";
import parseSvgPath from "parse-svg-path";
import { IconButton, Toolbar } from "@material-ui/core";
import ZoomInIcon from "@material-ui/icons/ZoomIn";
import ZoomOutIcon from "@material-ui/icons/ZoomOut";
import ZoomOutMapIcon from "@material-ui/icons/ZoomOutMap";
import HomeIcon from "@material-ui/icons/Home";
import "./diagram.scss";

const BAR_MARGIN = 50;
const BOTTOM_MARGIN = 30;
const GRAPH_MIN_HEIGHT = 600;

class WorkflowGraph extends React.Component {
  constructor(props) {
    super(props);
    this.renderer = new dagreD3Render();
    this.renderer.shapes().bar = barRenderer;
    this.renderer.shapes().stack = stackRenderer;

    this.svgRef = React.createRef();
  }

  componentDidUpdate(prevProps) {
    // useEffect on dag
    if (prevProps.dag !== this.props.dag) {
      this.drawGraph();
      this.zoomHome();
    }

    // useEffect on selectedRef
    if (prevProps.selectedTask !== this.props.selectedTask) {
      this.highlightSelectedNode();
    }
  }

  componentDidMount() {
    this.svg = d3.select(this.svgRef.current);

    // Set up zoom support
    this.zoom = d3
      .zoom()
      .filter((event) => {
        if (event.type === "wheel") {
          return event.ctrlKey;
        } else if (event.type === "dblclick") {
          return false; // ignore dblclick
        } else {
          return !event.ctrlKey && !event.button;
        }
      })
      .on("zoom", (event) => {
        this.inner.attr("transform", event.transform);
      });

    this.zoom(this.svg);

    this.drawGraph();
    this.highlightSelectedNode();
    this.zoomHome();
  }

  highlightSelectedNode = () => {
    const dagGraph = this.props.dag.graph;
    const taskResult = this.props.dag.resolveTaskResult(
      this.props.selectedTask
    );

    const selectedRef =
      taskResult &&
      (taskResult.referenceTaskName ||
        taskResult.workflowTask.taskReferenceName);

    let resolvedRef;
    if (!selectedRef) {
      resolvedRef = null;
    } else if (this.graph.hasNode(selectedRef)) {
      resolvedRef = selectedRef;
    } else if (dagGraph.hasNode(selectedRef)) {
      // if ref cannot be found in this.graph, it may be rendered as a stacked placeholder.

      const parentRef = _.first(dagGraph.predecessors(selectedRef));
      const parentType = dagGraph.node(parentRef).type;
      console.assert(
        parentType === "FORK_JOIN_DYNAMIC" || parentType === "DO_WHILE"
      );

      resolvedRef = this.graph
        .successors(parentRef)
        .find((ref) => ref.includes("DF_TASK_PLACEHOLDER"));
    } else {
      throw new Error("Assertion failed. ref not found");
    }

    const { inner } = this;
    inner.selectAll("g.node").classed("selected", false);

    if (resolvedRef) {
      inner.select(`g[id='${resolvedRef}']`).classed("selected", true);
    }
  };

  zoomInOut = (dir) => {
    const { svg, inner } = this;
    const currTransform = d3.zoomTransform(inner.node());
    const newZoom =
      dir === "in" ? currTransform.k * 1.25 : currTransform.k / 1.25;
    this.zoom.transform(svg, d3.zoomIdentity.scale(newZoom));
    const postZoomedHeight = inner.node().getBoundingClientRect().height;
    svg.attr(
      "height",
      Math.max(postZoomedHeight + BOTTOM_MARGIN, GRAPH_MIN_HEIGHT)
    );
  };

  zoomHome = () => {
    const { svg, inner } = this;
    const containerWidth = svg.node().getBoundingClientRect().width;
    const graphWidth = this.graph.graph().width;

    this.zoom.transform(
      svg,
      d3.zoomIdentity.translate(containerWidth / 2 - graphWidth / 2, 0)
    );

    const postZoomedHeight = inner.node().getBoundingClientRect().height;
    svg.attr(
      "height",
      Math.max(postZoomedHeight + BOTTOM_MARGIN, GRAPH_MIN_HEIGHT)
    );
  };

  zoomToFit = () => {
    const { svg, inner } = this;
    const containerWidth = svg.node().getBoundingClientRect().width;
    const scale = Math.min(containerWidth / this.graph.graph().width, 1);
    this.zoom.transform(svg, d3.zoomIdentity.scale(scale));

    // Adjust svg height to fit post-zoomed
    const postZoomedHeight = inner.nodes()[0].getBoundingClientRect().height;
    svg.attr(
      "height",
      Math.max(postZoomedHeight + BOTTOM_MARGIN, GRAPH_MIN_HEIGHT)
    );
  };

  collapseDfChildren = (parentRef, childrenRef) => {
    const graph = this.graph;
    const dagGraph = this.props.dag.graph;

    const tally = childrenRef
      .map((childRef) => dagGraph.node(childRef).status)
      .reduce(
        (prev, curr) => {
          const retval = { total: prev.total + 1 };
          if (curr === "COMPLETED") {
            retval.success = prev.success + 1;
          } else if (curr === "IN_PROGRESS" || curr === "SCHEDULED") {
            retval.inProgress = prev.inProgress + 1;
          } else if (curr === "CANCELED") {
            retval.canceled = prev.canceled + 1;
          }
          return {
            ...prev,
            ...retval,
          };
        },
        {
          success: 0,
          inProgress: 0,
          canceled: 0,
          total: 0,
        }
      );

    const placeholderRef = parentRef + "_DF_TASK_PLACEHOLDER";

    let status;
    if (tally.success === tally.total) {
      status = "COMPLETED";
    } else if (tally.inProgress) {
      status = "IN_PROGRESS";
    } else {
      status = "FAILED";
    }

    const placeholderNode = {
      name: placeholderRef,
      ref: placeholderRef,
      type: "DF_TASK_PLACEHOLDER",
      status: status, // Only used for coloring
      firstDfRef: _.first(childrenRef),
      tally: tally,
    };
    graph.setNode(placeholderRef, placeholderNode);

    const tailSet = new Set();
    for (const childRef of childrenRef) {
      graph
        .successors(childRef)
        .forEach((successorRef) => tailSet.add(successorRef));
      graph.removeNode(childRef); // This automatically removes any incident edges
    }

    // Add edges for placeholder
    graph.setEdge(parentRef, placeholderRef, { executed: true });

    // Should have only 1 unique successor (being a JOIN)
    console.assert(tailSet.size === 1);

    const successorRef = tailSet.values().next().value;
    const successor = dagGraph.node(successorRef);
    graph.setEdge(
      placeholderRef,
      successorRef,
      successor.status ? { executed: true } : undefined
    );
  };

  drawGraph = () => {
    if (this.inner) this.inner.remove();
    this.inner = this.svg.append("g");
    const { svg, inner } = this;

    const graph = new graphlib.Graph({ compound: true }).setGraph({
      nodesep: 15,
      ranksep: 30,
    });
    this.graph = graph;
    this.barNodes = [];

    const dagGraph = this.props.dag.graph;

    // Clone graph
    for (const nodeId of dagGraph.nodes()) {
      graph.setNode(nodeId);
    }
    for (const { v, w } of dagGraph.edges()) {
      graph.setEdge(v, w);
    }

    // Collapse Dynamic Fork children
    const dfNodes = dagGraph
      .nodes()
      .filter((nodeId) => dagGraph.node(nodeId).type === "FORK_JOIN_DYNAMIC");

    for (const parentRef of dfNodes) {
      const childRefs = dagGraph.successors(parentRef);

      if (childRefs.length > 2) {
        this.collapseDfChildren(parentRef, childRefs);
      }
    }

    // Collapse Do_while children
    const doWhileNodes = dagGraph
      .nodes()
      .filter((nodeId) => dagGraph.node(nodeId).type === "DO_WHILE");

    for (const parentRef of doWhileNodes) {
      const parentNode = dagGraph.node(parentRef);

      // Only collapse executed DO_WHILE loops
      if (_.get(parentNode, "status")) {
        const childRefs = dagGraph
          .successors(parentRef)
          .map((ref) => dagGraph.node(ref))
          .filter((node) => node.type !== "DO_WHILE_END")
          .map((node) => node.ref);

        if (childRefs.length > 0) {
          this.collapseDfChildren(parentRef, childRefs);
        }
      }
    }

    // Render Nodes
    for (const nodeId of graph.nodes()) {
      graph.setNode(nodeId, this.renderVertex(nodeId)); // Update nodes with render info
    }

    // Render Edges
    for (const edgeId of graph.edges()) {
      const dagEdge = dagGraph.edge(edgeId) || graph.edge(edgeId);

      const caseValue = _.get(dagEdge, "caseValue");
      const type = _.get(dagEdge, "type");

      let classes = [],
        label,
        labelStyle;

      if (type === "loop") {
        label = "LOOP";
        classes.push("reverse");
      } else {
        label = caseValue || (caseValue === null ? "default" : "");
      }

      if (this.props.executionMode) {
        const executed = _.get(dagEdge, "executed");
        if (executed) {
          classes.push("executed");
          labelStyle = "";
        } else {
          classes.push("dimmed");
          labelStyle = "fill: #ccc";
        }
      }

      graph.setEdge(edgeId.v, edgeId.w, {
        label: label,
        labelStyle: labelStyle,
        class: classes.join(" "),
      });
    }

    this.renderer(inner, graph);

    // Expand barNodes and rerender
    for (const barRef of this.barNodes) {
      this.expandBar(barRef);
    }

    // svg.width=100% via CSS
    svg.attr("height", graph.graph().height + BOTTOM_MARGIN);

    // Fix dagre-d3 bug with marker-end. Use css to set marker-end
    // See: https://github.com/dagrejs/dagre-d3/pull/413
    d3.selectAll("path.path").attr("marker-end", "");

    // Attach click handler
    inner.selectAll("g.node").on("click", this.handleClick);
  };

  /**
   * Get the taskRef id base on browsers
   * @param e
   * @returns {string | undefined} The id of the task ref
   */
  getTaskRef = (e) => {
    const flag = navigator.userAgent.toLowerCase().indexOf("firefox") > -1 || navigator.userAgent.toLowerCase().indexOf("chrome") > -1;
    if (flag) {
      return e.target?.parentNode?.id;
    }
    return e?.path[1]?.id || e?.path[2]?.id; // could be 2 layers down
  };

  handleClick = (e) => {
    const taskRef = e.composedPath()[1].id || e.composedPath()[2].id; // could be 2 layers down
    const node = this.graph.node(taskRef);
    if (node.type === "DF_TASK_PLACEHOLDER") {
      if (this.props.onClick) this.props.onClick({ ref: node.firstDfRef });
    } else if (
      node.type === "DF_EMPTY_PLACEHOLDER" ||
      node.type === "TERMINAL"
    ) {
      return null; // No-op for click on unexecuted DF card-pile or terminal nodes
    } else {
      // Non-DF, or unexecuted DF vertex
      if (this.props.onClick) this.props.onClick({ ref: taskRef });
    }
  };

  render() {
    const { style, className } = this.props;
    return (
      <div style={style} className={`graphWrapper ${className || ""}`}>
        <Toolbar>
          <IconButton onClick={() => this.zoomInOut("in")}>
            <ZoomInIcon />
          </IconButton>
          <IconButton onClick={() => this.zoomInOut("out")}>
            <ZoomOutIcon />
          </IconButton>
          <IconButton onClick={this.zoomHome}>
            <HomeIcon />
          </IconButton>
          <IconButton onClick={this.zoomToFit}>
            <ZoomOutMapIcon />
          </IconButton>
          <span>Shortcut: Ctrl + scroll to zoom</span>
        </Toolbar>
        <svg ref={this.svgRef} className="graphSvg">
          <defs>
            <filter id="brightness">
              <feComponentTransfer>
                <feFuncR type="linear" slope="0.9"></feFuncR>
                <feFuncG type="linear" slope="0.9"></feFuncG>
                <feFuncB type="linear" slope="0.9"></feFuncB>
              </feComponentTransfer>
            </filter>

            <filter
              id="dropShadow"
              height="300%"
              width="300%"
              x="-75%"
              y="-75%"
            >
              <feMorphology
                operator="dilate"
                radius="4"
                in="SourceAlpha"
                result="thicken"
              />
              <feGaussianBlur in="thicken" stdDeviation="7" result="blurred" />
              <feFlood floodColor="rgb(0,122,255)" result="glowColor" />
              <feComposite
                in="glowColor"
                in2="blurred"
                operator="in"
                result="softGlow_colored"
              />

              <feMerge>
                <feMergeNode in="softGlow_colored" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>

            <marker
              id="endarrow"
              markerWidth="8"
              markerHeight="6"
              refX="8"
              refY="3"
              orient="auto"
              markerUnits="strokeWidth"
            >
              <polygon points="0 0, 8 3, 0 6" />
            </marker>

            <marker
              id="startarrow"
              markerWidth="8"
              markerHeight="6"
              refX="0"
              refY="3"
              orient="auto"
              markerUnits="strokeWidth"
            >
              <polygon points="8 0, 8 6, 0 3" />
            </marker>

            <marker
              id="endarrow-dimmed"
              markerWidth="8"
              markerHeight="6"
              refX="8"
              refY="3"
              orient="auto"
              markerUnits="strokeWidth"
              stroke="#c8c8c8"
              fill="#c8c8c8"
            >
              <polygon points="0 0, 8 3, 0 6" />
            </marker>

            <marker
              id="startarrow-dimmed"
              markerWidth="8"
              markerHeight="6"
              refX="0"
              refY="3"
              orient="auto"
              markerUnits="strokeWidth"
              stroke="#c8c8c8"
              fill="#c8c8c8"
            >
              <polygon points="8 0, 8 6, 0 3" />
            </marker>
          </defs>
        </svg>
      </div>
    );
  }

  renderVertex = (nodeId) => {
    const dagGraph = this.props.dag.graph;
    const graph = this.graph;

    const v = dagGraph.node(nodeId) || graph.node(nodeId); // synthetic nodes (e.g. DF placeholder) not found in 'dag' but preloaded into local graph.

    let retval = {
      id: v.ref,
      class: `type-${v.type}`,
      type: v.type,
    };

    switch (v.type) {
      case "SUB_WORKFLOW":
        retval.label = `${v.ref}\n(${v.name})`;
        break;
      case "TERMINAL":
        retval.label = v.name;
        retval.shape = "circle";
        break;
      case "TERMINATE":
        retval.label = `${v.ref}\n(terminate)`;
        retval.shape = "circle";
        break;
      case "FORK_JOIN":
      case "FORK_JOIN_DYNAMIC":
        retval = composeBarNode(v, "down");
        this.barNodes.push(v.ref);
        break;
      case "JOIN":
      case "EXCLUSIVE_JOIN":
        retval = composeBarNode(v, "up");
        this.barNodes.push(v.ref);
        break;
      case "DECISION":
      case "SWITCH":
        retval.label = v.ref;
        retval.shape = "diamond";
        retval.height = 40;
        break;
      case "DF_EMPTY_PLACEHOLDER":
        retval.label = v.status
          ? "No tasks spawned"
          : "Dynamically spawned tasks";
        retval.shape = "stack";
        break;
      case "DF_TASK_PLACEHOLDER":
        retval.label = `${v.tally.success} of ${v.tally.total} tasks succeeded`;
        if (v.tally.inProgress) {
          retval.label += `\n${v.tally.inProgress} pending`;
        }
        if (v.tally.canceled) {
          retval.label += `\n${v.tally.canceled} canceled`;
        }
        retval.firstDfRef = v.firstDfRef;
        retval.shape = "stack";
        break;
      case "DO_WHILE":
      case "DO_WHILE_END":
        retval = composeBarNode(v, "down");
        retval.label = `${retval.label} [DO_WHILE]`;
        this.barNodes.push(v.ref);
        break;
      default:
        retval.label = `${v.ref}\n(${v.name})`;
        retval.shape = "rect";
    }

    if (_.size(v.taskResults) > 1) {
      retval.label += `\n${v.taskResults.length} Attempts`;
    }

    if (this.props.executionMode) {
      if (v.status) {
        if (v.type !== "TERMINAL") {
          retval.class += ` status_${v.status}`;
        }
      } else {
        retval.class += " dimmed";
      }
    }

    return retval;
  };

  expandBar(barRef) {
    const barNode = this.graph.node(barRef);
    let fanOut;
    if (barNode.fanDir === "down") {
      fanOut = this.graph.outEdges(barRef).map((e) => {
        const points = parseSvgPath(
          this.graph.edge(e).elem.querySelector("path").getAttribute("d")
        );
        return _.first(points);
      });
    } else if (barNode.fanDir === "bidir") {
      fanOut = this.graph.inEdges(barRef).map((e) => {
        const points = parseSvgPath(
          this.graph.edge(e).elem.querySelector("path").getAttribute("d")
        );
        return _.last(points);
      });
    } else {
      fanOut = this.graph.inEdges(barRef).map((e) => {
        const points = parseSvgPath(
          this.graph.edge(e).elem.querySelector("path").getAttribute("d")
        );
        return _.last(points);
      });
    }

    const barWidth = barNode.elem.getBBox().width;
    let translateX = getTranslateX(barNode.elem),
      translateY = getTranslateY(barNode.elem);
    let minX = barNode.x - barWidth / 2;
    let maxX = barNode.x + barWidth / 2;

    for (const point of fanOut) {
      const left = point[1] - BAR_MARGIN;
      const right = point[1] + BAR_MARGIN;
      if (right > maxX) maxX = right;
      if (left < minX) minX = left;
    }

    if (minX < 0) {
      maxX = maxX - minX + BAR_MARGIN;
      minX = -BAR_MARGIN;
    }

    translateX = minX;
    barNode.elem.setAttribute(
      "transform",
      `translate(${translateX}, ${translateY})`
    );

    const rect = barNode.elem.querySelector("rect");
    const currTransformY = rect.transform.baseVal[0].matrix.f;
    const newWidth = maxX - minX;
    const newTransformX = 0;
    rect.removeAttribute("transform");
    rect.setAttribute("y", currTransformY);
    rect.setAttribute("width", newWidth);

    const text = barNode.elem.querySelector("g.label > g");
    const textWidth = text.getBBox().width;
    const newTextTransformX = newTransformX + (newWidth - textWidth) / 2;
    const currTextTransformY = text.transform.baseVal[0].matrix.f;
    text.setAttribute(
      "transform",
      `translate(${newTextTransformX}, ${currTextTransformY})`
    );
  }
}

export default withResizeDetector(WorkflowGraph);
WorkflowGraph.propTypes = {
  dag: PropTypes.object,
  onClick: PropTypes.func,
  selectedTask: PropTypes.object,
  width: PropTypes.number,
  height: PropTypes.number,
};

function composeBarNode(v, fanDir) {
  const retval = {
    id: v.ref,
    type: v.type,
    fanDir: fanDir,
    class: `bar type-${v.type}`,
    shape: "bar",
    labelStyle: "font-size:11px",
    padding: 4,
    label: `${v.name} (${v.aliasForRef || v.ref})`,
  };
  return retval;
}

function barRenderer(parent, bbox, node) {
  const group = parent.insert("g", ":first-child");
  group
    .insert("rect")
    .attr("width", bbox.width)
    .attr("height", bbox.height)
    .attr("transform", `translate(${-bbox.width / 2}, ${-bbox.height / 2})`);

  /*
  if(node.type === 'EXCLUSIVE_JOIN') {
    group.insert("rect")
    .attr("class", "underline")
    .attr("width", bbox.width)
    .attr("height", 3)
    .attr("transform", `translate(${-bbox.width/2}, ${bbox.height - 7})`);
  }*/

  node.intersect = function (point) {
    // Only spread out arrows in fan direction
    return {
      x:
        (node.fanDir === "down" && point.y > node.y) ||
          (node.fanDir === "up" && point.y < node.y)
          ? point.x
          : intersect.rect(node, point).x,
      y: point.y < node.y ? node.y - bbox.height / 2 : node.y + bbox.height / 2,
    };
  };

  return group;
}

const STACK_OFFSET = 5;
function stackRenderer(parent, bbox, node) {
  const group = parent.insert("g", ":first-child");

  group
    .insert("rect")
    .attr("width", bbox.width)
    .attr("height", bbox.height)
    .attr(
      "transform",
      `translate(${-bbox.width / 2 - STACK_OFFSET * 2}, ${-bbox.height / 2 - STACK_OFFSET * 2
      })`
    );
  group
    .insert("rect")
    .attr("width", bbox.width)
    .attr("height", bbox.height)
    .attr(
      "transform",
      `translate(${-bbox.width / 2 - STACK_OFFSET}, ${-bbox.height / 2 - STACK_OFFSET
      })`
    );
  group
    .insert("rect")
    .attr("width", bbox.width)
    .attr("height", bbox.height)
    .attr("transform", `translate(${-bbox.width / 2}, ${-bbox.height / 2})`);

  node.intersect = function (point) {
    const retval = intersect.rect(node, point);
    if (retval.y < node.y) retval.y -= STACK_OFFSET;
    if (retval.y >= node.y) retval.y -= STACK_OFFSET * 2;

    return retval;
  };
  return group;
}

function getTranslateX(elem) {
  return elem.transform.baseVal[0].matrix.e;
}
function getTranslateY(elem) {
  return elem.transform.baseVal[0].matrix.f;
}