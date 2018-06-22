import React, { Component } from 'react';
import { Link, browserHistory } from 'react-router';
import { Breadcrumb, BreadcrumbItem, Input, Well, Button, Panel, DropdownButton, ButtonToolbar, MenuItem, Popover, OverlayTrigger, ButtonGroup } from 'react-bootstrap';
import {BootstrapTable, TableHeaderColumn} from 'react-bootstrap-table';
import { connect } from 'react-redux';
import { getTaskDefs } from '../../../actions/WorkflowActions';

const TaskMetaList = React.createClass({

  getInitialState() {
    return {
      name: '',
      version: '',
      taskDefs: []
    }
  },

  componentWillMount(){
    this.props.dispatch(getTaskDefs());
  },

  componentWillReceiveProps(nextProps){
    this.state.taskDefs = nextProps.taskDefs;

  },

  render() {
    var wfs = this.state.taskDefs;

    function retries(cell, row){
      if(row.retryLogic == 'FIXED') {
        return row.retryLogic + ' (' + row.retryDelaySeconds + ' seconds)';
      }
    };

    function editor(cell, row){
      return (<OverlayTrigger trigger="click" rootClose placement="right" overlay={
        <Popover title={row.name} style={{ width: '500px'}}><div className="left">
          <form>
            <Input type="text" ref="retryCount" value={row.retryCount} addonBefore="Retry Count" addonAfter="Times"></Input><br/>
            <Input type="select" ref="retryLogic" value={row.retryLogic} addonBefore="Retry Logic">
                <option value="FIXED">FIXED</option>
                <option value="EXPONENTIAL_BACKOFF">EXPONENTIAL_BACKOFF</option>
            </Input><br/>
            <Input type="text" ref="retryDelaySeconds" value={row.retryDelaySeconds} addonBefore="Retry Delay" addonAfter="Seconds"></Input><br/>
            <Input type="select" ref="timeoutPolicy" value={row.timeoutPolicy} addonBefore="Time Out Action">
              <option value="RETRY_TASK">RETRY TASK</option>
              <option value="TIME_OUT_WF">TIME_OUT_WF</option>
            </Input><br/>
            <Input type="text" ref="timeoutSeconds" value={row.timeoutSeconds} addonBefore="Time Out" addonAfter="Seconds"></Input><br/>
            <Input type="text" ref="restimeoutSeconds" value={row.responseTimeoutSeconds} addonBefore="Response Time Out" addonAfter="timeoutSeconds"></Input><br/>
            <Input type="text" ref="concurrentExecLimit" value={row.concurrentExecLimit} addonBefore="Concurrent Exec Limit"></Input><br/>
            <Input type="textarea" label="Task Description" ref="description" value={row.description} readonly={true}/><br/>
          </form>
        </div></Popover>
      }><a>{cell}</a></OverlayTrigger>);
    };

    return (
      <div className="ui-content">
        <h1>Task Definitions</h1>
        <BootstrapTable data={wfs} striped={true} hover={true} search={true} exportCSV={false} pagination={false}>
          <TableHeaderColumn dataField="name" isKey={true} dataAlign="left" dataSort={true} dataFormat={editor}>Name/Version</TableHeaderColumn>
          <TableHeaderColumn dataField="ownerApp" dataSort={true} >Owner App</TableHeaderColumn>
          <TableHeaderColumn dataField="timeoutPolicy" dataSort={true} >Timeout Policy</TableHeaderColumn>
          <TableHeaderColumn dataField="timeoutSeconds" dataSort={true} >Timeout Seconds</TableHeaderColumn>
          <TableHeaderColumn dataField="responseTimeoutSeconds" dataSort={true} >Response Timeout Seconds</TableHeaderColumn>
          <TableHeaderColumn dataField="retryCount" dataSort={true} >Retry Count</TableHeaderColumn>
          <TableHeaderColumn dataField="concurrentExecLimit" dataSort={true} >Concurrent Exec Limit</TableHeaderColumn>
          <TableHeaderColumn dataField="retryLogic" dataSort={true} dataFormat={retries}>Retry Logic</TableHeaderColumn>
          </BootstrapTable>
      </div>
    );
  }
});
export default connect(state => state.workflow)(TaskMetaList);
