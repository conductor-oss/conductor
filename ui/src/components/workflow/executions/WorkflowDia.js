import React from 'react';
import { connect } from 'react-redux';
import { getWorkflowDetails } from '../../../actions/WorkflowActions';
import dagreD3 from 'dagre-d3'
import d3 from 'd3'

class WorkflowDia extends React.Component {
  componentWillReceiveProps(nextProps) {
    if (this.props.hash != nextProps.hash) {
      console.log('id=' + nextProps.params.workflowId);
      this.props.dispatch(getWorkflowDetails(nextProps.params.workflowId));
    }
  }

  shouldComponentUpdate(nextProps, nextState){
    if(nextProps.refetch){
      this.props.dispatch(getWorkflowDetails(nextProps.params.workflowId));
      return false;
    }
    return true;
  }

  render() {

    var wf = this.props.data;
    if(wf == null) {
      wf = {};
    }
    if(wf.tasks == null){
      wf.tasks = [];
    }
    let tasks = wf['tasks'];
    tasks.push({taskType:'final'});
    //let tasks = wf['tasks'].map(task => {return task.taskType;});
    var g = new dagreD3.graphlib.Graph().setGraph({rankdir: 'TD'});

    tasks.forEach(function(task) {
      let shape = 'rect';
      if(task.taskType == 'decision'){
        shape = 'diamond';
      }else if(task.taskType == 'final'){
        shape = 'circle';
      }
      let output = JSON.stringify(task.outputData);
      g.setNode(task.taskType, { label: task.taskType, shape: shape, output: output});

    });

    for(let i = 1; i < tasks.length; i++){
      let label = '';
      if(tasks[i-1].taskType == 'decision'){
        label = 'Case = ' + "''" || tasks[i-1].outputData.caseOutput;
      }else if( (i < tasks.length - 1) && tasks[i].taskType == 'decision'){
        label =  JSON.stringify(tasks[i-1].outputData);
      }
      g.setEdge(tasks[i-1].taskType, tasks[i].taskType, { label: label  });
    }

    g.nodes().forEach(function(v) {
      var node = g.node(v);
      node.rx = node.ry = 5;
    });

// Add some custom colors based on state
    //g.node('CLOSED').style = "fill: #f77";
    //g.node('ESTAB').style = "fill: #7f7";

    var svg = d3.select("svg"),
      inner = svg.select("g");

    // Create the renderer
    var render = new dagreD3.render();
    // Run the renderer. This is what draws the final edges.
    render(inner, g);
    //inner.selectAll("g.node").attr("title", function(v) { return styleTooltip(v, g.node(v).output) });

    return (
      <div className="ui-content container-fluid">
        <svg width="100%" height="600">
          <g transform="translate(20,20)"></g>
        </svg>
      </div>
    );
  }
};

export default connect(state => state.workflow)(WorkflowDia);
