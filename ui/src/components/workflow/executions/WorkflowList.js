import React, { Component } from 'react';
import { Link, browserHistory } from 'react-router';
import { Input, Button, Panel, Popover, OverlayTrigger, ButtonGroup, Grid, Row, Col  } from 'react-bootstrap';
import {BootstrapTable, TableHeaderColumn} from 'react-bootstrap-table';
import { connect } from 'react-redux';
import { searchWorkflows, getWorkflowDefs } from '../../../actions/WorkflowActions';
import Typeahead from 'react-bootstrap-typeahead';


function linkMaker(cell) {
    return <Link to={`/workflow/id/${cell}`}>{cell}</Link>;
}

function zeroPad(num) {
    return ('0' + num).slice(-2);
}

function formatDate(cell){
    if(cell == null || !cell.split) {
        return '';
    }
    let c = cell.split("T");
    let time = c[1].split(":");
    let hh = zeroPad(time[0]);
    let mm = zeroPad(time[1]);
    let ss = zeroPad(time[2].replace("Z",""));

    let dt = c[0] + "T" + hh + ":" + mm + ":" + ss + "Z";

    if(dt == null || dt === ''){
        return '';
    }

    return new Date(dt).toLocaleString('en-US');
}

function miniDetails(cell, row){
    return (<ButtonGroup><OverlayTrigger trigger="click" rootClose placement="left" overlay={
        <Popover title="Workflow Details" width={400}>
            <span className="red">{row.reasonForIncompletion == null?'':<span>{row.reasonForIncompletion}<hr/></span>}</span>
            <b>Input</b><br/>
            <span className="small" style={{maxWidth:'400px'}}>{row.input}</span>
            <hr/><b>Output</b><br/>
            <span className="small">{row.output}</span>
            <hr/><br/>
        </Popover>

    }><Button bsSize="xsmall">details</Button></OverlayTrigger></ButtonGroup>);
}

const Workflow = React.createClass({

  getInitialState() {

    let workflowTypes = this.props.location.query.workflowTypes;
    if(workflowTypes != null && workflowTypes != '') {
      workflowTypes = workflowTypes.split(',');
    }else {
      workflowTypes = [];
    }
    let status = this.props.location.query.status;
    if(status != null && status != '') {
      status = status.split(',');
    }else {
      status = [];
    }
    let search = this.props.location.query.q;
    if(search == null || search == 'undefined' || search == '') {
      search = '';
    }
    let st = this.props.location.query.start;
    let start = 0;
    if(!isNaN(st)) {
      start = parseInt(st);
    }

    return {
      search: search,
      workflowTypes: workflowTypes,
      status: status,
      h: this.props.location.query.h,
      workflows: [],
      update: true,
      fullstr: true,
      start: start
    }
  },
  componentWillMount(){
    this.props.dispatch(getWorkflowDefs());
    this.doDispatch();
  },
  componentWillReceiveProps(nextProps) {

    let workflowDefs = nextProps.workflows;
    workflowDefs = workflowDefs ? workflowDefs : [];
    workflowDefs = workflowDefs.map(workflowDef => workflowDef.name);

    let search = nextProps.location.query.q;
    if(search == null || search == 'undefined' || search == '') {
      search = '';
    }
    let h = nextProps.location.query.h;
    if(isNaN(h)) {
      h = '';
    }
    let start = nextProps.location.query.start;
    if(isNaN(start)) {
      start = 0;
    }
    let status = nextProps.location.query.status;
    if(status != null && status != '') {
      status = status.split(',');
    }else {
      status = [];
    }

    let update = true;
    update = this.state.search != search;
    update = update || (this.state.h != h);
    update = update || (this.state.start != start);
    update = update || (this.state.status.join(',') != status.join(','));

    this.setState({
      search : search,
      h : h,
      update : update,
      status : status,
      workflows : workflowDefs,
      start : start
    });

    this.refreshResults();
  },
  searchBtnClick() {
    this.state.update = true;
    this.refreshResults();
  },
  refreshResults() {
    if(this.state.update) {
      this.state.update = false;
      this.urlUpdate();
      this.doDispatch();
    }
  },
  urlUpdate() {
    let q = this.state.search;
    let h = this.state.h;
    let workflowTypes = this.state.workflowTypes;
    let status = this.state.status;
    let start = this.state.start;
    this.props.history.pushState(null, "/workflow?q=" + q + "&h=" + h + "&workflowTypes=" + workflowTypes + "&status=" + status + "&start=" + start);
  },
  doDispatch() {

    let search = '';
    if(this.state.search != '') {
      search = this.state.search;
    }
    let h = this.state.h;
    let query = [];

    if(this.state.workflowTypes.length > 0) {
      query.push('workflowType IN (' + this.state.workflowTypes.join(',') + ') ');
    }
    if(this.state.status.length > 0) {
      query.push('status IN (' + this.state.status.join(',') + ') ');
    }
    this.props.dispatch(searchWorkflows(query.join(' AND '), search, this.state.h, this.state.fullstr, this.state.start));
  },
  workflowTypeChange(workflowTypes) {
    this.state.update = true;
    this.state.workflowTypes = workflowTypes;
    this.refreshResults();
  },
  statusChange(status) {
    this.state.update = true;
    this.state.status = status;
    this.refreshResults();
  },
  nextPage() {
    this.state.start = 100 + parseInt(this.state.start);
    this.state.update = true;
    this.refreshResults();
  },
  prevPage() {
    this.state.start = parseInt(this.state.start) - 100;
    if(this.state.start < 0) {
        this.state.start = 0;
    }
    this.state.update = true;
    this.refreshResults();
  },
  searchChange(e){
    let val = e.target.value;
    this.setState({ search: val });
  },
  hourChange(e){
    this.state.update = true;
    this.state.h = e.target.value;
    this.refreshResults();
  },
  keyPress(e){
   if(e.key == 'Enter'){
     this.state.update = true;
     var q = e.target.value;
     this.setState({search: q});
     this.refreshResults();
   }
  },
  prefChange(e) {
    this.setState({
      fullstr:e.target.checked
    });
    this.state.update = true;
    this.refreshResults();
  },
 render() {
    let wfs = [];
    let filteredWfs = [];

     let totalHits = 0;
    let found = 0;
    if(this.props.data.hits) {
      wfs = this.props.data.hits;
      totalHits = this.props.data.totalHits;
      found = wfs.length;
    }
    let start = parseInt(this.state.start);
    let max = start + 100;
    if(found < 100) {
      max = start + found;
    }
    const workflowNames = this.state.workflows?this.state.workflows:[];
    const statusList = ['RUNNING','COMPLETED','FAILED','TIMED_OUT','TERMINATED','PAUSED'];

    //secondary filter to match sure we only show workflows that match the the status
    var currentStatusArray = this.state.status;
    if(currentStatusArray.length>0 && wfs.length>0) {
        filteredWfs = wfs.filter( function (wf) {
            return currentStatusArray.includes(wf.status); //remove wft if status doesn't match search
        });
    } else {
        filteredWfs = wfs;
    }


    return (
      <div className="ui-content">
        <div>
          <Panel header="Filter Workflows (Press Enter to search)">
          <Grid fluid={true}>
            <Row className="show-grid">
              <Col md={4}>
                <Input type="input" placeholder="Search" groupClassName="" ref="search" value={this.state.search} labelClassName="" onKeyPress={this.keyPress} onChange={this.searchChange}/>
                &nbsp;<i className="fa fa-angle-up fa-1x"/>&nbsp;&nbsp;<label className="small nobold">Free Text Query</label>
                &nbsp;&nbsp;<input type="checkbox" checked={this.state.fullstr} onChange={this.prefChange} ref="fullstr"/><label className="small nobold">&nbsp;Search for entire string</label>
                </Col>
              <Col md={4}>
                <Typeahead ref="workflowTypes" onChange={this.workflowTypeChange} options={workflowNames} placeholder="Filter by workflow type" multiple={true} selected={this.state.workflowTypes}/>
                &nbsp;<i className="fa fa-angle-up fa-1x"/>&nbsp;&nbsp;<label className="small nobold">Filter by Workflow Type</label>
              </Col>
              <Col md={2}>
                <Typeahead ref="status" onChange={this.statusChange} options={statusList} placeholder="Filter by status" selected={this.state.status} multiple={true}/>
                &nbsp;<i className="fa fa-angle-up fa-1x"/>&nbsp;&nbsp;<label className="small nobold">Filter by Workflow Status</label>
              </Col>
              <Col md={2}>
                <Input className="number-input" type="text" ref="h" groupClassName="inline" labelClassName="" label="" value={this.state.h} onChange={this.hourChange}/>
                &nbsp;&nbsp;&nbsp;<Button bsStyle="success" onClick={this.searchBtnClick} className="search-label btn"><i className="fa fa-search"/>&nbsp;&nbsp;Search</Button>
                <br/>&nbsp;&nbsp;&nbsp;<i className="fa fa-angle-up fa-1x"/>&nbsp;&nbsp;<label className="small nobold">Created (in past hours)</label>
              </Col>
            </Row>
          </Grid>
          <form>

          </form>
          </Panel>
        </div>
        <span>Total Workflows Found: <b>{totalHits}</b>, Displaying {this.state.start} <b>to</b> {max}</span>
        <span style={{float:'right'}}>
          {parseInt(this.state.start) >= 100?<a onClick={this.prevPage}><i className="fa fa-backward"/>&nbsp;Previous Page</a>:''}
          {parseInt(this.state.start) + 100 <= totalHits?<a onClick={this.nextPage}>&nbsp;&nbsp;Next Page&nbsp;<i className="fa fa-forward"/></a>:''}
        </span>
        <BootstrapTable data={filteredWfs} striped={true} hover={true} search={false} exportCSV={false} pagination={false} options={{sizePerPage:100}}>
          <TableHeaderColumn dataField="workflowType" isKey={true} dataAlign="left" dataSort={true}>Workflow</TableHeaderColumn>
          <TableHeaderColumn dataField="workflowId" dataSort={true} dataFormat={linkMaker}>Workflow ID</TableHeaderColumn>
          <TableHeaderColumn dataField="status" dataSort={true}>Status</TableHeaderColumn>
          <TableHeaderColumn dataField="startTime" dataSort={true} dataFormat={formatDate}>Start Time</TableHeaderColumn>
          <TableHeaderColumn dataField="updateTime" dataSort={true} dataFormat={formatDate}>Last Updated</TableHeaderColumn>
          <TableHeaderColumn dataField="endTime" hidden={false} dataFormat={formatDate}>End Time</TableHeaderColumn>
          <TableHeaderColumn dataField="reasonForIncompletion" hidden={false}>Failure Reason</TableHeaderColumn>
          <TableHeaderColumn dataField="failedReferenceTaskNames" hidden={false}>Failed Tasks</TableHeaderColumn>
          <TableHeaderColumn dataField="input" width="300">Input</TableHeaderColumn>
          <TableHeaderColumn dataField="workflowId" width="300" dataFormat={miniDetails}>&nbsp;</TableHeaderColumn>
        </BootstrapTable>

        <br/><br/>
      </div>
    );
  }
});
export default connect(state => state.workflow)(Workflow);
