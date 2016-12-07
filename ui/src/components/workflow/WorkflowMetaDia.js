import React, { Component } from 'react';
import { Link } from 'react-router';
import { connect } from 'react-redux';
import Grapher from '../common/Grapher'
import  Workflow2Graph  from '../../api/wfegraph'

class WorkflowMetaDia extends Component {

  constructor(props) {
    super(props);
    this.state = {}
    this.state.edges = [];
    this.state.vertices = {};
    this.state.executedTasks = [];
    this.state.subworkflows = [];
    this.wfe2graph = new Workflow2Graph();
  };

  componentWillReceiveProps(nextProps) {
    let subworkflows = nextProps.subworkflows || {};
    let wfe = nextProps.wfe || {tasks: []};
    let metaTasks = nextProps.meta?nextProps.meta.tasks:[];
    let r = this.wfe2graph.convert(wfe, nextProps.meta);
    this.state.edges = r.edges;
    this.state.vertices = r.vertices;
    this.state.subworkflows = {};

    for(let refname  in subworkflows){
      let submeta = subworkflows[refname].meta;
      let subwfe = subworkflows[refname].wfe;
      let subr = this.wfe2graph.convert(subwfe, submeta);
      this.state.subworkflows[refname] = subr;
    }
  };

  render() {

    let edges = this.state.edges;
    let vertices = this.state.vertices;
    let layout = 'TD-auto';
    let subworkflows = this.state.subworkflows;

    return (
      <div id="workflowmeta">
        <Grapher edges={edges} vertices={vertices} layout={layout} parentElem="workflowmeta" innerGraph={subworkflows}/>
      </div>
    );
  }
};

export default WorkflowMetaDia;
