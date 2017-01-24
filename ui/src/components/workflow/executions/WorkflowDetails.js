import React, { Component } from 'react';
import { Link } from 'react-router';
import { Breadcrumb, BreadcrumbItem, Grid, Row, Col, Well, OverlayTrigger,Button,Popover, Panel, Tabs, Tab, Table } from 'react-bootstrap';
import {BootstrapTable, TableHeaderColumn} from 'react-bootstrap-table';
import { connect } from 'react-redux';
import { getWorkflowDetails } from '../../../actions/WorkflowActions';
import WorkflowAction  from './WorkflowAction';
import WorkflowMetaDia from '../WorkflowMetaDia';
import moment from 'moment';


class WorkflowDetails extends Component {

  constructor(props) {
    super(props);
  }

  componentWillReceiveProps(nextProps) {
    if (this.props.hash != nextProps.hash) {
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
    tasks = tasks.sort(function(a,b){
      return a.endTime - b.endTime;
    });
    function formatDate(dt){
      if(dt == null || dt == ''){
        return '';
      }
      return moment(dt).format('MM/DD/YYYY, HH:mm:ss:SSS');
    }
    function execTime(end, start){
      if(end == null || end == 0) {
        return "";
      }
      var total = end - start;
      var sec = total/1000;
      return sec;
    }
    function dateFormat(cell, row) {
      if(cell == null){
        return "";
      }
      return formatDate(cell);
    }
    function taskExecTime(cell, row){
      var time = execTime(row.endTime, row.scheduledTime);
      if(time < 0){
        return 'N/A';
      }
    }
    function workerLink(type, cell){
      if(cell == null){
        return "";
      }
      let href = "#";
      return <a target="_new" href={href}>{cell}</a>;
    }
    function popoverLink(cell, row){
      return (<OverlayTrigger trigger="click" rootClose placement="left" overlay={

        <Popover title="Task Details" width={400}>
          <Panel header="Task Input">
            <span className="small">{JSON.stringify(row.inputData, null, 2)}</span>
          </Panel>
          <Panel header="Task Output">
            <span className="small">{JSON.stringify(row.outputData, null, 2)}</span>
          </Panel>
          <Panel header="Task Failure Reason (if any)">
            <span className="small">{JSON.stringify(row.reasonForIncompletion, null, 2)}</span>
          </Panel>
        </Popover>

      }><Button bsStyle="default" bsSize="xsmall">Input/Output</Button></OverlayTrigger>);
    }
    function tableBody(tasks){
      let trs = [];
      tasks.forEach(task => {
        let row = <tr>
                    <td>{task.seq}</td>
                    <td>{task.taskType}</td>
                    <td>{task.referenceTaskName}</td>
                    <td>{formatDate(task.scheduledTime)}</td>
                    <td>{formatDate(task.startTime)} <br/> {formatDate(task.endTime)}</td>
                    <td>{task.status}</td>
                    <td>{formatDate(task.updateTime)}</td>
                    <td>{task.callbackAfterSeconds==null?0:task.callbackAfterSeconds}</td>
                    <td>{task.pollCount}</td>
                    <td>{popoverLink(null, task)}<br/>{workerLink(task.taskType, task.workerId)}</td>
                  </tr>;
        trs.push(row);
      });
      return <tbody>{trs}</tbody>
    }
    function getFailureReason(){
      return wf.reasonForIncompletion;
    }
    function showFailure(){
      if(wf.status == 'FAILED' || wf.status == 'TERMINATED' || wf.status == 'TIMED_OUT'){
        return '';
      }
      return 'none';
    }
    return (
      <div className="ui-content">
      <h4>
        {wf.workflowType}/{wf.version}
        <span className={(wf.status == 'FAILED' || wf.status == 'TERMINATED' || wf.status == 'TIMED_OUT') ? "red":"green"}>
          {wf.status}
        </span>
        <span>
          <WorkflowAction workflowStatus={wf.status} workflowId={wf.workflowId}/>
        </span>
      </h4>
      <br/><br/>
      <Table responsive={true} striped={false} hover={false} condensed={false} bordered={true}>
        <thead>
          <tr>
            <th>Workflow ID</th><th>Owner App</th><th>Total Time (sec)</th><th>Start/End Time</th><th>Correlation ID</th>
          </tr>
          <tr>
            <td>{wf.workflowId}</td>
            <td>{wf.ownerApp}</td>
            <td>{execTime(wf.endTime, wf.startTime)}</td>
            <td>{formatDate(wf.startTime)} - {formatDate(wf.endTime)}</td>
            <td>{wf.correlationId}</td>
          </tr>
          <tr style={{display:showFailure()}}><td style={{color:'#ff0000'}} colSpan={5}>{getFailureReason()}</td></tr>
        </thead>
      </Table>

        <Tabs defaultActiveKey={1}>
          <Tab eventKey={1} title="Execution Flow">
            <WorkflowMetaDia meta={this.props.meta} wfe={wf} subworkflows={this.props.subworkflows}/>
          </Tab>
          <Tab eventKey={2} title="Task Details">
            <Table responsive={true} striped={true} hover={true} condensed={false} bordered={true}>
            <thead>
              <tr>
                <th>#</th>
                <th>Task Type</th>
                <th>Task Ref. Name</th>
                <th>Scheduled Time</th>
                <th>Start/End Time</th>
                <th>Status</th>
                <th>Last Polled/Updated</th>
                <th>Callback After</th>
                <th>Poll Count</th>
                <th>Details</th>
              </tr>
            </thead>
              {tableBody(tasks)}
            </Table>
            <br/>
          </Tab>
          <Tab eventKey={3} title="Input/Output">
          <div>
            <strong>Workflow Input</strong>
            <pre style={{height:'200px'}}>{JSON.stringify(wf.input, null, 3)}</pre>
            <strong>Workflow Output</strong>
            <pre style={{height:'200px'}}>{JSON.stringify(wf.output==null?{}:wf.output, null, 3)}</pre>
            {wf.status == 'FAILED'?<div><strong>Workflow Faiure Reason (if any)</strong><pre>{wf.reasonForIncompletion?JSON.stringify(wf.reasonForIncompletion, null, 3):''}</pre></div>:''}
          </div>
          </Tab>
          <Tab eventKey={4} title="JSON">
            <pre style={{height:'80%'}}>{JSON.stringify(wf, null, 3)}</pre>
          </Tab>

        </Tabs>

      </div>
    );
  }
};
export default connect(state => state.workflow)(WorkflowDetails);
