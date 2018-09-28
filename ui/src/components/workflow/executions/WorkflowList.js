/* eslint-disable no-restricted-globals */

import React from 'react';
import { Link } from 'react-router';
import { connect } from 'react-redux';
import { searchWorkflows, getWorkflowDefs, bulkRetryWorkflow, bulkPauseWorkflow, bulkResumeWorkflow, bulkRestartWorkflow, bulkTerminateWorkflow } from '../../../actions/WorkflowActions';
import Typeahead from 'react-bootstrap-typeahead';
import { BootstrapTable, TableHeaderColumn } from 'react-bootstrap-table';
import { Input, Button, Panel, Popover, OverlayTrigger, ButtonGroup, Grid, Row, Col } from 'react-bootstrap';

let versionSuffix = /\/[0-9]+$/;

function linkMaker(cell) {
  return <Link to={`/workflow/id/${cell}`}>{cell}</Link>;
}

function zeroPad(num) {
  return `0${num}`.slice(-2);
}

function formatDate(cell) {
  if (cell == null || !cell.split) {
    return '';
  }
  const c = cell.split('T');
  const time = c[1].split(':');
  const hh = zeroPad(time[0]);
  const mm = zeroPad(time[1]);
  const ss = zeroPad(time[2].replace('Z', ''));

  const dt = `${c[0]}T${hh}:${mm}:${ss}Z`;

  if (dt == null || dt === '') {
    return '';
  }

  return new Date(dt).toLocaleString('en-US');
}

function miniDetails(cell, row) {
  return (
    <ButtonGroup>
      <OverlayTrigger
        trigger="click"
        rootClose
        placement="left"
        overlay={
          <Popover title="Workflow Details" width={400}>
            <span className="red">
              {row.reasonForIncompletion == null ? (
                ''
              ) : (
                <span>
                  {row.reasonForIncompletion}
                  <hr />
                </span>
              )}
            </span>
            <b>Input</b>
            <br />
            <span className="small" style={{ maxWidth: '400px' }}>
              {row.input}
            </span>
            <hr />
            <b>Output</b>
            <br />
            <span className="small">{row.output}</span>
            <hr />
            <br />
          </Popover>
        }
      >
        <Button bsSize="xsmall">details</Button>
      </OverlayTrigger>
    </ButtonGroup>
  );
}

class Workflow extends React.Component {
  constructor(props) {
    super(props);
    const {
      location: {
        query: { workflowTypes = '', q = '', status = '', start = 0 }
      }
    } = props;

    this.state = {
      search: q === 'undefined' || q === '' ? '' : q,
      workflowTypes: workflowTypes === '' ? [] : workflowTypes.split(','),
      status: status !== '' ? status.split(',') : [],
      h: this.props.location.query.h,
      workflows: [],
      update: true,
      fullstr: true,
      start: !isNaN(start, 10) ? parseInt(start, 10) : start,
      selectedWFEs:[],
      bulkProcessOperation: "",
      bulkProcessInFlight: false,
      bulkProcessSuccess: false,
      error:false,
      bulkError:false,
      bulkValidationMessage:"",
      bulkPanelOpen:false,
      bulkErrorMessages: [],
      bulkSuccessfulResults:[]
    };
  }

  componentWillMount(){
    this.props.dispatch(getWorkflowDefs());
    this.doDispatch();
  }

  componentWillReceiveProps(nextProps) {
    let {workflows = [], location} = nextProps;
    let {query} = location;
    let {h,start, status = '', q} = query;

    const workflowDefs = workflows.map((workflowDef) => {
      return workflowDef.name + "/" + workflowDef.version;
    });

    let search = q;
    if (search == null || search === 'undefined' || search === '') {
      search = '';
    }

    if(!this.state.bulkProcessSuccess && nextProps.bulkProcessSuccess){
      this.onBulkSuccess();
    }

    let update = true;
    update = this.state.search !== search;
    update = update || this.state.h !== h;
    update = update || this.state.start !== start;

    this.constructBulkErrorMessages(nextProps);
    this.removeSuccessfulWorkFlows((nextProps && nextProps.bulkSuccessfulResults) || []);
    this.setState({
      search : search,
      h: isNaN(h, 10) ? '' : h,
      update : update,
      status : status !== '' ? status.split(',') : [],
      workflows : workflowDefs,
      bulkProcessInFlight : nextProps.bulkProcessInFlight,
      bulkProcessSuccess : nextProps.bulkProcessSuccess,
      bulkSuccessfulResults: nextProps.bulkSuccessfulResults,
      start: isNaN(start, 10) ? 0 : start,
      error: nextProps.error,
      bulkError: ((!this.state.error && nextProps.error && this.state.bulkProcessInFlight) || nextProps.bulkServerErrors) ? true : this.state.bulkError
    });
    this.refreshResults();
  }

  searchBtnClick = () => {
    this.state.update = true;
    this.state.start = "0";
    this.refs.table.handlePaginationData(0, 100);
    this.refs.table.handleSearch('');
    this.refreshResults();
  };

  refreshResults = () => {
    if (this.state.update) {
      this.state.update = false;
      this.urlUpdate();
      this.doDispatch();
    }
  };

  urlUpdate = () => {
    const { workflowTypes, status, start, h, search: q } = this.state;
    this.props.history.pushState(
      null,
      `/workflow?q=${q}&h=${h}&workflowTypes=${encodeURIComponent(workflowTypes)}&status=${status}&start=${start}`
    );
  };

  doDispatch = () => {
    const { search = '' } = this.state;
    const query = [];

    if (this.state.workflowTypes.length > 0) {
      query.push(`workflowType IN (${this.state.workflowTypes.map((workFlowString) => {
        return workFlowString.replace(versionSuffix, "");
      }).join(',')}) `);
    }

    if (this.state.status.length > 0) {
      query.push(`status IN (${this.state.status.join(',')}) `);
    }

    this.props.dispatch(
      searchWorkflows(query.join(' AND '), search, this.state.h, this.state.fullstr, this.state.start)
    );
  };

  workflowTypeChange = workflowTypes => {
    this.state.update = true;
    this.state.workflowTypes = workflowTypes;
    this.refreshResults();
  };

  statusChange = status => {
    this.state.update = true;
    this.state.status = status;
    this.refreshResults();
  };

  nextPage = () => {
    this.state.start = 100 + parseInt(this.state.start, 10);
    this.state.update = true;
    this.refreshResults();
  };

  prevPage = () => {
    this.state.start = parseInt(this.state.start, 10) - 100;
    if (this.state.start < 0) {
      this.state.start = 0;
    }
    this.state.update = true;
    this.refreshResults();
  };

  searchChange = e => {
    const val = e.target.value;
    this.setState({ search: val });
  };

  hourChange = e => {
    this.state.update = true;
    this.state.h = e.target.value;
    this.refreshResults();
  };

  keyPress = e => {
    if (e.key == 'Enter') {
      this.state.update = true;
      this.state.start = "0";
      this.refs.table.handlePaginationData(0, 100);
      this.refs.table.handleSearch('');
      var q = e.target.value;
      this.setState({ search: q });
      this.refreshResults();
    }
  };

  prefChange = e => {
    this.setState({
      fullstr: e.target.checked
    });
    this.state.update = true;
    this.refreshResults();
  };

  handleRowSelect = (row, isSelected, e) => {
    let currWFEs = this.state.selectedWFEs;
    this.setState({update:true});
    if(isSelected){
      currWFEs.push(row);
      this.setState({selectedWFEs: currWFEs, bulkValidationMessage:""})
    } else {
      var index = this.state.selectedWFEs.filter((storedRow) => {
        return row.workflowId === storedRow.workflowId
      })[0] || -1;
      if(index === -1){
        return true;
      }
      currWFEs.splice(index, 1)
      this.setState({selectedWFEs: currWFEs, bulkValidationMessage:""})
    }

    return true;
  };

  handleSelectAll = (isSelected, rows, e) =>  {
    if(isSelected){
      this.setState({selectedWFEs: rows,bulkValidationMessage:""})
    } else {
      this.setState({selectedWFEs: [],bulkValidationMessage:""})
    }

    return true;
  };

  onChangeBulkProcessSelection = (e) => {
    this.setState({bulkProcessOperation:e.target.value, bulkValidationMessage:""})
  };

  onBulkSuccess = () => {
    this.refs.bulkProcessSelect.refs.input.value = "";
    this.setState({selectedWFEs:[], bulkProcessOperation:""});
    this.refs.table.cleanSelected();
  };

  removeSuccessfulWorkFlows = (successfulWorkflows) => {
    if(successfulWorkflows !== undefined && successfulWorkflows !== null){
      this.refs.table.cleanSelected(),
      this.setState({selectedWFEs: []});
    }
  }

  bulkProcess = () => {

    let wfes = this.state.selectedWFEs.map((wfe) => {return wfe.workflowId});
    let operation = this.state.bulkProcessOperation;
    this.setState({bulkProcessSuccess:false, bulkError:false, bulkValidationMessage: "", bulkErrorMessages:[], bulkSuccessfulResults:[]});

    if(wfes.length === 0){
      this.setState({bulkValidationMessage:"Error: No workflows selected"})
      return;
    }

    switch(operation){
      case "retry":
        this.props.dispatch(bulkRetryWorkflow(wfes));
        break;
      case "restart":
        this.props.dispatch(bulkRestartWorkflow(wfes))
        break;
      case "resume":
        this.props.dispatch(bulkResumeWorkflow(wfes))
        break;
      case "terminate":
        this.props.dispatch(bulkTerminateWorkflow(wfes))
        break;
      case "pause":
        this.props.dispatch(bulkPauseWorkflow(wfes))
        break;
      default:
        this.setState({bulkValidationMessage:"Error: No Workflow Operation selected"})
      return;
    }

  }

  constructBulkErrorMessages = (props) => {
    var messages = [];

    if(props.bulkServerErrorMessage){
      messages.push(props.bulkServerErrorMessage);
    }

    if(props.bulkErrorResults){
      for(var id in props.bulkErrorResults){
        messages.push(id + " - " + props.bulkErrorResults[id] + "");
      }
    }
    this.setState({bulkErrorMessages: messages});
    return null;
  }

 render = () => {
    let wfs = [];
    let filteredWfs = [];
    let totalHits = 0;
    let found = 0;
    if (this.props.data.hits) {
      wfs = this.props.data.hits;
      totalHits = this.props.data.totalHits;
      found = wfs.length;
    }
    const start = parseInt(this.state.start);
    let max = start + 100;
    if (found < 100) {
      max = start + found;
    }
    let allWorkflowNames = this.state.workflows ? this.state.workflows : [];

    const workflowNames = Object.keys(allWorkflowNames.reduce((red, val) => {
      red[val] = true;
      return red;
    }, {}));

    const statusList = ['RUNNING', 'COMPLETED', 'FAILED', 'TIMED_OUT', 'TERMINATED', 'PAUSED'];


    const selectRow = {
      mode: 'checkbox',
      onSelect: this.handleRowSelect,
      onSelectAll: this.handleSelectAll
    };
    let bulkErrors = this.state.bulkErrorMessages.length === 0 ? [] :  this.state.bulkErrorMessages.map((mess) => {
      return <p>{mess}</p>
    });

    let bulkSuccessMessage = this.state.bulkSuccessfulResults && this.state.bulkSuccessfulResults.length > 0 ?  this.state.bulkSuccessfulResults.map((mess) => {
      return <p key={mess}>{mess}</p>
    }) : <p key="NONE">None</p> ;

    const bulkSpin = (this.state.bulkProcessInFlight ? (<i style={{"fontSize":"150%"}} className="fa fa-spinner fa-spin"></i>) : "");
    const bulkSuccess = (this.state.bulkProcessSuccess ? (<span style={{"fontSize":"150%", "color":"green"}}>All Successful!</span>) : "");
    const bulkValidation = (this.state.bulkValidationMessage !== "" ? (<span style={{"fontSize":"150%", "color":"red"}}>{this.state.bulkValidationMessage}</span>) : "");
    const bulkError = (this.state.bulkError ? (<span><span style={{"fontSize":"125%", "color":"green"}}>Successful: {bulkSuccessMessage}</span><span style={{"fontSize":"125%", "color":"red"}}>Errors: {this.state.bulkErrorMessages.map((mess) => {return <p key={mess}>{mess}</p>})}</span></span>) : "");
    //secondary filter to match sure we only show workflows that match the the status
    var currentStatusArray = this.state.status;
    if (currentStatusArray.length > 0 && wfs.length > 0) {
      filteredWfs = wfs.filter(wf => currentStatusArray.includes(wf.status));
    } else {
      filteredWfs = wfs;
    }


    return (
      <div className="ui-content">
        <div>
          <Panel header="Filter Workflows (Press Enter to search)">
            <Grid fluid>
              <Row className="show-grid">
                <Col md={4}>
                  <Input
                    type="input"
                    placeholder="Search"
                    groupClassName=""
                    ref="search"
                    value={this.state.search}
                    labelClassName=""
                    onKeyPress={this.keyPress}
                    onChange={this.searchChange}
                  />
                  &nbsp;<i className="fa fa-angle-up fa-1x" />&nbsp;&nbsp;<label className="small nobold">
                    Free Text Query
                  </label>
                  &nbsp;&nbsp;<input
                    type="checkbox"
                    checked={this.state.fullstr}
                    onChange={this.prefChange}
                    ref="fullstr"
                  />
                  <label className="small nobold">&nbsp;Search for entire string</label>
                </Col>
                <Col md={4}>
                  <Typeahead
                    ref="workflowTypes"
                    onChange={this.workflowTypeChange}
                    options={workflowNames}
                    placeholder="Filter by workflow type"
                    multiple
                    selected={this.state.workflowTypes}
                  />
                  &nbsp;<i className="fa fa-angle-up fa-1x" />&nbsp;&nbsp;<label className="small nobold">
                    Filter by Workflow Type
                  </label>
                </Col>
                <Col md={2}>
                  <Typeahead
                    ref="status"
                    onChange={this.statusChange}
                    options={statusList}
                    placeholder="Filter by status"
                    selected={this.state.status}
                    multiple
                  />
                  &nbsp;<i className="fa fa-angle-up fa-1x" />&nbsp;&nbsp;<label className="small nobold">
                    Filter by Workflow Status
                  </label>
                </Col>
                <Col md={2}>
                  <Input
                    className="number-input"
                    type="text"
                    ref="h"
                    groupClassName="inline"
                    labelClassName=""
                    label=""
                    value={this.state.h}
                    onChange={this.hourChange}
                  />
                  &nbsp;&nbsp;&nbsp;<Button
                    bsStyle="success"
                    onClick={this.searchBtnClick}
                    className="search-label btn"
                  >
                    <i className="fa fa-search" />&nbsp;&nbsp;Search
                  </Button>
                  <br />&nbsp;&nbsp;&nbsp;<i className="fa fa-angle-up fa-1x" />&nbsp;&nbsp;<label className="small nobold">
                    Created (in past hours)
                  </label>
                </Col>
              </Row>
            </Grid>
            <form />
          </Panel>
        </div>
        <Panel header="Bulk Processing  (click to expand)" collapsible >
        <label>Select workflows from table below</label>
        <Grid fluid={true}>
          <Row className="show-grid">
              <Col md={3}>
                <Input plaintext><span style={{"fontSize":"150%"}}>{this.state.selectedWFEs.length} </span></Input>
                &nbsp;
                <i className="fa fa-angle-up fa-1x"/>
                &nbsp;&nbsp;&nbsp;
                <label className="small nobold">Number of Workflows Selected</label>
              </Col>
            <Col md={2}>
                <Input type="select" ref="bulkProcessSelect" onChange={this.onChangeBulkProcessSelection} >
                  <option value=""></option>
                  <option value="pause">Pause</option>
                  <option value="resume">Resume</option>
                  <option value="restart">Restart</option>
                  <option value="retry">Retry</option>
                  <option value="terminate">Terminate</option>
                </Input>
                <i className="fa fa-angle-up fa-1x"/>
                &nbsp;&nbsp;&nbsp;
                <label className="small nobold">Workflow Operation</label>
            </Col>
            <Col md={1}>
              <Button bsStyle="success" onClick={this.bulkProcess} value="Process" className="btn" disabled={this.state.bulkProcessInFlight} >Process</Button>
              &nbsp;

            </Col>
            <Col md={4}>
              {bulkSpin}
              {bulkSuccess}
              {bulkError}
              {bulkValidation}
            </Col>
          </Row>
        </Grid>
        </Panel>
        <span>
          Total Workflows Found: <b>{totalHits}</b>, Displaying {this.state.start} <b>to</b> {max}
        </span>
        <span style={{ float: 'right' }}>
          {parseInt(this.state.start, 10) >= 100 ? (
            <a onClick={this.prevPage}>
              <i className="fa fa-backward" />&nbsp;Previous Page
            </a>
          ) : (
            ''
          )}
          {parseInt(this.state.start, 10) + 100 <= totalHits ? (
            <a onClick={this.nextPage}>
              &nbsp;&nbsp;Next Page&nbsp;<i className="fa fa-forward" />
            </a>
          ) : (
            ''
          )}
        </span>
        <BootstrapTable ref="table" data={filteredWfs} striped={true} hover={true} search={false} exportCSV={false} pagination={false} selectRow={selectRow} options={{sizePerPage:100}}>
        <TableHeaderColumn dataField="workflowType" dataAlign="left" dataSort>
          Workflow
        </TableHeaderColumn>
        <TableHeaderColumn dataField="workflowId" isKey dataSort dataFormat={linkMaker}>
          Workflow ID
        </TableHeaderColumn>
        <TableHeaderColumn dataField="status" dataSort>
          Status
        </TableHeaderColumn>
        <TableHeaderColumn dataField="startTime" dataSort dataFormat={formatDate}>
          Start Time
        </TableHeaderColumn>
        <TableHeaderColumn dataField="updateTime" dataSort dataFormat={formatDate}>
          Last Updated
        </TableHeaderColumn>
        <TableHeaderColumn dataField="endTime" hidden={false} dataFormat={formatDate}>
          End Time
        </TableHeaderColumn>
        <TableHeaderColumn dataField="reasonForIncompletion" hidden={false}>
          Failure Reason
        </TableHeaderColumn>
        <TableHeaderColumn dataField="failedReferenceTaskNames" hidden={false}>
          Failed Tasks
        </TableHeaderColumn>
        <TableHeaderColumn dataField="input" width="300">
          Input
        </TableHeaderColumn>
        <TableHeaderColumn dataField="workflowId" width="300" dataFormat={miniDetails}>
          &nbsp;
        </TableHeaderColumn>
        </BootstrapTable>

        <br />
        <br />
      </div>
    );
  }
}

export default connect(state => state.workflow)(Workflow);
