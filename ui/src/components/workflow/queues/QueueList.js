import React, { Component } from 'react';
import moment from 'moment';
import { Link, browserHistory } from 'react-router';
import { Breadcrumb, BreadcrumbItem, Input, Well, Button, Panel, DropdownButton, ButtonToolbar, MenuItem, Popover, OverlayTrigger, ButtonGroup } from 'react-bootstrap';
import {BootstrapTable, TableHeaderColumn} from 'react-bootstrap-table';
import { connect } from 'react-redux';
import { getQueueData } from '../../../actions/WorkflowActions';

const QueueListList = React.createClass({

  getInitialState() {
    return {
      name: '',
      version: '',
      queueData: []
    }
  },

  componentWillMount(){
    this.props.dispatch(getQueueData());
  },

  componentWillReceiveProps(nextProps){
    this.state.queueData = nextProps.queueData;
  },

  render() {
    var wfs = this.state.queueData;

    function formatName(cell, row){
      var name = row.queueName;
      if(row.domain != null){
        name = name + " (" + row.domain + ")";
      }
      return name;
    };

    function formatDate(cell, row){
      return moment(row.lastPollTime).fromNow();
    };

    return (
      <div className="ui-content">
        <h1>Queues</h1>
        <BootstrapTable data={wfs} striped={true} hover={true} search={true} exportCSV={false} pagination={false}>
          <TableHeaderColumn dataField="queueName" isKey={true} dataAlign="left" dataSort={true} dataFormat={formatName}>Name (Domain)</TableHeaderColumn>
          <TableHeaderColumn dataField="qsize" dataSort={true} >Size</TableHeaderColumn>
          <TableHeaderColumn dataField="lastPollTime" dataSort={true} dataFormat={formatDate}>Last Poll Time</TableHeaderColumn>
          <TableHeaderColumn dataField="workerId" dataSort={true} >Last Polled By</TableHeaderColumn>
          </BootstrapTable>
      </div>
    );
  }
});
export default connect(state => state.workflow)(QueueListList);
