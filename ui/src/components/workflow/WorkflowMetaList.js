import React, { Component } from 'react';
import { Link, browserHistory } from 'react-router';
import { Breadcrumb, BreadcrumbItem, Input, Well, Button, Panel, DropdownButton, MenuItem, Popover, OverlayTrigger, ButtonGroup } from 'react-bootstrap';
import {BootstrapTable, TableHeaderColumn} from 'react-bootstrap-table';
import { connect } from 'react-redux';
import { getWorkflowDefs } from '../../actions/WorkflowActions';
import { WorkflowMetaDetails } from './WorkflowMetaDetails';

const WorkflowMetaList = React.createClass({

  getInitialState() {
    return {
      name: '',
      version: '',
      workflows: []
    }
  },

  componentWillMount(){
    this.props.dispatch(getWorkflowDefs());
  },

  componentWillReceiveProps(nextProps){
    this.state.workflows = nextProps.workflows;

  },

  render() {
    var wfs = this.state.workflows;

    function jsonMaker(cell, row){
      return JSON.stringify(cell);
    };

    function taskMaker(cell, row){
      if(cell == null){
        return '';
      }
      return JSON.stringify(cell.map(task => {return task.name;}));
    };

    function nameMaker(cell, row){
      return (<Link to={`/workflow/metadata/${row.name}/${row.version}`}>{row.name} / {row.version}</Link>);
    };

    return (
      <div className="ui-content">
        <h1>Workflows</h1>
        <BootstrapTable data={wfs} striped={true} hover={true} search={true} exportCSV={false} pagination={false}>
          <TableHeaderColumn dataField="name" isKey={true} dataAlign="left" dataSort={true} dataFormat={nameMaker}>Name/Version</TableHeaderColumn>
          <TableHeaderColumn dataField="inputParameters" dataSort={true} dataFormat={jsonMaker}>Input Parameters</TableHeaderColumn>
          <TableHeaderColumn dataField="tasks" hidden={false} dataFormat={taskMaker}>Tasks</TableHeaderColumn>
          </BootstrapTable>
      </div>
    );
  }
});
export default connect(state => state.workflow)(WorkflowMetaList);
