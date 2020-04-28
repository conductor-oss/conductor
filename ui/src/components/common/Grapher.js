import React, {Component} from 'react';
import dagreD3 from 'dagre-d3'
import d3 from 'd3'
import {Tabs, Tab, Table} from 'react-bootstrap';
import Clipboard from 'clipboard';
import { Link } from 'react-router';

new Clipboard('.btn');

class Grapher extends Component {
    constructor(props) {
        super(props);

        this.state = {};
        this.state.selectedTask = {};
        this.state.logs = {};
        this.grapher = new dagreD3.render();

        this.setSvgRef = elem => this.svgElem = elem;
        this.setDivRef = elem => this.divElem = elem;
        this.setPropsDivRef = elem => this.propsDivElem = elem;

        let starPoints = function (outerRadius, innerRadius) {
            var results = "";
            var angle = Math.PI
                / 8;
            for (var i = 0; i < 2 * 8; i++) {
                // Use outer or inner radius depending on what iteration we are in.
                var r = (i & 1) == 0 ? outerRadius : innerRadius;
                var currX = 0 + Math.cos(i * angle) * r;
                var currY = 0 + Math.sin(i * angle) * r;
                if (i == 0) {
                    results = currX + "," + currY;
                } else {
                    results += ", " + currX + "," + currY;
                }
            }
            return results;
        };

        this.grapher.shapes().house = function (parent, bbox, node) {
            var w = bbox.width,
                h = bbox.height,
                points = [
                    {x: 0, y: 0},
                    {x: w, y: 0},
                    {x: w, y: -h},
                    {x: w / 2, y: -h * 3 / 2},
                    {x: 0, y: -h}
                ];
            let shapeSvg = parent.insert("polygon", ":first-child")
                .attr("points", points.map(function (d) {
                    return d.x + "," + d.y;
                }).join(" "))
                .attr("transform", "translate(" + (-w / 2) + "," + (h * 3 / 4) + ")");

            node.intersect = function (point) {
                return dagreD3.intersect.polygon(node, points, point);
            };

            return shapeSvg;
        };

        this.grapher.shapes().star = function (parent, bbox, node) {
            var w = bbox.width,
                h = bbox.height,
                points = [
                    {x: 0, y: 0},
                    {x: w, y: 0},
                    {x: w, y: -h},
                    {x: w / 2, y: -h * 3 / 2},
                    {x: 0, y: -h}
                ];
            let shapeSvg = parent.insert("polygon", ":first-child").attr("points", starPoints(w, h))
            node.intersect = function (point) {
                return dagreD3.intersect.polygon(node, points, point);
            };

            return shapeSvg;
        };
    }

    componentDidMount() {
        this.forceUpdate();
    }

    componentWillReceiveProps(nextProps) {
        this.state.innerGraph = nextProps.innerGraph;
    };

    getSubGraph() {
        let subg = this.state.subGraph;
        if (subg == null) {
            return '';
        }
        return <Grapher edges={subg.n} vertices={subg.vx} layout={subg.layout}/>;
    }

    render() {
        const {layout, edges, vertices} = this.props;

        let g = new dagreD3.graphlib.Graph().setGraph({rankdir: layout});

        for (let vk in vertices) {
            let v = vertices[vk];
            let l = v.name;
            if (!v.system) {
                l = v.name + '\n \n(' + v.ref + ')';
            } else {
                l = v.ref;
            }
            g.setNode(v.ref, {
                label: l,
                shape: v.shape,
                style: v.style,
                labelStyle: v.labelStyle + '; font-weight:normal; font-size: 11px'
            });
        }

        edges.forEach(e => {
            g.setEdge(e.from, e.to, {label: e.label, lineInterpolate: 'basis', style: e.style});
        });

        g.nodes().forEach(function (v) {
            var node = g.node(v);
            if (node == null) {
                console.log('NO node found ' + v);
            }
            node.rx = node.ry = 5;
        });

        let svg = d3.select(this.svgElem);
        let inner = svg.select("g");
        inner.attr("transform", "translate(20,20)");
        this.grapher(inner, g);

        let w = g.graph().width + 200;
        let h = g.graph().height + 50;

        svg.attr("width", w + "px").attr("height", h + "px");


        let innerGraph = this.state.innerGraph || [];
        let p = this;

        let showSubGraphDetails = function () {
            let id = p.state.subGraphId;
            window.open('#/workflow/id/' + id, '_new');
        };

        let hidesub = function () {
            p.setState({showSubGraph: false});
        };

        let hideProps = function () {
            p.setState({showSideBar: false});
        };

        inner.selectAll("g.node")
            .on("click", function (v) {
                if (innerGraph[v] != null) {
                    let data = vertices[v].data;
                    let renderedInnerGraph = innerGraph[v]();
                    let n = renderedInnerGraph.edges;
                    let vx = renderedInnerGraph.vertices;
                    let subg = {n: n, vx: vx, layout: layout};

                    p.propsDivElem.style.left = (window.innerWidth/2 + 100) + 'px';
                    p.propsDivElem.style.width = window.innerWidth/2 - 100 + 'px'
                    p.propsDivElem.style.overflowX = "scroll"
                    p.propsDivElem.style.height = window.innerHeight + "px";
                    p.divElem.style.width = window.outerWidth/2 - 100 + "px";
                    p.divElem.style.display = "inline-block";

                    p.setState({
                        selectedTask: data.task,
                        showSubGraph: true,
                        showSideBar: true,
                        subGraph: subg,
                        subGraphId: renderedInnerGraph.id
                    });
                    p.setState({showSubGraph: true});

                } else if (vertices[v].tooltip != null) {
                    let data = vertices[v].data;
                    p.propsDivElem.style.left = (window.innerWidth/2 + 100) + 'px';
                    p.propsDivElem.style.width = window.innerWidth/2 - 100 + 'px'
                    p.propsDivElem.style.height = window.innerHeight + "px";

                    p.propsDivElem.style.position = "fixed"
                    p.propsDivElem.style.display = "block"
                    p.setState({selectedTask: data.task, showSideBar: true, subGraph: null, showSubGraph: false});
                }
            })
            .append("svg:title")
            .text(function(v) { return vertices[v].description; });

        return (
            <div className="graph-ui-content" id="graph-ui-content">
                <div className="right-prop-overlay" ref={this.setPropsDivRef}
                     style={{overflowX: 'scroll', display: this.state.showSideBar ? '' : 'none', padding: '5px 5px 10px 10px'}}>
                    <h4 className="propsheader">
                        <i className="fa fa-close fa-1x close-btn" onClick={hideProps}/>
                        {this.state.selectedTask.taskType} ({this.state.selectedTask.status})
                    </h4>
                    {this.state.selectedTask.taskType == 'SUB_WORKFLOW' &&
                        <div>
                            <p>
                            <Link onClick={hideProps} to={'/workflow/id/' + this.state.selectedTask.subWorkflowId}>
                                <u>View Subworkflow</u>
                            </Link>
                            </p>
                        </div>
                    }
                    <div style={{
                        color: '#ff0000',
                        display: this.state.selectedTask.status === 'FAILED' ? '' : 'none'
                    }}>{this.state.selectedTask.reasonForIncompletion}</div>
                    <Tabs defaultActiveKey={1}>
                        <Tab eventKey={1} title="Summary">
                            <Table responsive={true} striped={false} hover={false} condensed={false} bordered={true}>
                                <tbody>
                                <tr>
                                    <th>Task Ref. Name</th>
                                    <td colSpan="3"
                                        style={{colSpan: 3}}>{this.state.selectedTask.referenceTaskName}</td>
                                </tr>
                                <tr>
                                    <th>Poll Count</th>
                                    <td>{this.state.selectedTask.pollCount}</td>
                                    <th>Callback After</th>
                                    <td>{this.state.selectedTask.callbackAfterSeconds ? this.state.selectedTask.callbackAfterSeconds : 0} (second)</td>
                                </tr>
                                <tr>
                                    <th colSpan="4">Input <i title="copy to clipboard" className="btn fa fa-clipboard"
                                                             data-clipboard-target="#t_input"/></th>
                                </tr>
                                <tr>
                                    <td colSpan="4">
                                        <pre style={{width:(window.outerWidth/2 - 140) + "px"}}
                                            id="t_input">{JSON.stringify(this.state.selectedTask.inputData, null, 3)}</pre>
                                    </td>
                                </tr>
                                <tr>
                                    <th colSpan="4">Output <i title="copy to clipboard" className="btn fa fa-clipboard"
                                                              data-clipboard-target="#t_output"/></th>
                                </tr>
                                <tr>
                                    <td colSpan="4" >
                                        <pre style={{width:(window.outerWidth/2 - 140) + "px"}}
                                            id="t_output">{JSON.stringify(this.state.selectedTask.outputData, null, 3)}</pre>
                                    </td>
                                </tr>
                                </tbody>
                            </Table>
                        </Tab>
                        <Tab eventKey={2} title="JSON"><br/>
                            <i title="copy to clipboard" className="btn fa fa-clipboard"
                               data-clipboard-target="#t_json"/>
                            <pre id="t_json">{JSON.stringify(this.state.selectedTask, null, 3)}</pre>
                        </Tab>
                        <Tab eventKey={3} title="Logs"><br/>
                            <i title="copy to clipboard" className="btn fa fa-clipboard"
                               data-clipboard-target="#t_logs"/>
                            <pre id="t_logs">{JSON.stringify(this.state.selectedTask.logs, null, 3)}</pre>
                        </Tab>
                    </Tabs>
                </div>
                <div style={{overflowX: "auto", width:"100%"}}>
                  <svg ref={this.setSvgRef}>
                    <g transform="translate(20,20)"/>
                  </svg>
                </div>

                <div className="right-prop-overlay" ref={this.setDivRef} style={{
                    overflowX: 'scroll',
                    display: this.state.showSubGraph ? '' : 'none',
                    padding: '5px 5px 10px 10px',
                    zIndex: this.state.showSubGraph ? '' : '-100'
                }}>
                    <h4 className="propsheader">
                        <i className="fa fa-close fa-1x close-btn" onClick={hidesub}/>
                        <a onClick={showSubGraphDetails}>Sub Workflow Details</a>
                    </h4>
                    {this.getSubGraph()}
                </div>
            </div>
        );
    }
};
export default Grapher;
