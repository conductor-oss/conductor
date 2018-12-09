import React, { Component } from 'react';
import PropTypes from "prop-types";
import numeral from 'numeral';
import moment from 'moment';
import shallowCompare from 'react-addons-shallow-compare';
import {BootstrapTable, TableHeaderColumn} from "react-bootstrap-table";
import {Link} from 'react-router';
import {Button, Overlay, Popover} from 'react-bootstrap';
import isEmpty from 'lodash/isEmpty';
import size from 'lodash/size';

function linkMaker(cell) {
  return <Link to={`/workflow/id/${cell}`}>{cell}</Link>;
}

function formatDate(cell) {
  if (isEmpty(cell)) {
    return '';
  }

  return moment(cell).format('MM/DD/Y hh:mm:ss A');
}

export default class WorkflowTable extends Component {
  constructor(props) {
    super(props);

    this.handlePopoverClick = this.handlePopoverClick.bind(this);
    this.handleHide = this.handleHide.bind(this);
    this.formatDetails = this.formatDetails.bind(this);

    this.state = {
      show: false,
      selectedRow: {},
      target: null
    }
  }

  handleHide({target: {type, innerText}}) {
    // workaround to prevent closing the Popover when clicking other buttons
    if (type !== "button" && innerText !== "Details") {
      this.setState({show: false});
    }
  }

  handlePopoverClick(row) {
    return ({target}) => {
      this.setState({
        target,
        show: (target !== this.state.target) ? true : !this.state.show,
        selectedRow: row
      });
    }
  }

  formatDetails(id, cell) {
    return <Button onClick={this.handlePopoverClick(cell)} bsSize="xsmall">Details</Button>
  }

  shouldComponentUpdate(nextProps, nextState) {
    return shallowCompare(this, nextProps, nextState);
  }

  render() {
    const {results, selected, start, isFetching, totalHits, handleSelect, handleSelectAll, prevPage, nextPage} = this.props;
    const {show, selectedRow: {reasonForIncompletion = "", input = "", output = ""}} = this.state;

    const found = size(results);
    const max = (found < 100) ? start + found : start + 100;

    const selectRow = {
      mode: 'checkbox',
      onSelect: handleSelect,
      onSelectAll: handleSelectAll,
      selected
    };

    return <div style={{position: 'relative'}}>
      <div style={{marginBottom: '10px', fontSize: '115%', color: '#454545'}}>
        {isFetching && <span>Loading results...</span>}
        {!isFetching && <span>Found <strong>{numeral(totalHits).format('0,0')}</strong> workflows</span>}
        <span> {'\u00A0|\u00A0'} Displaying <strong>{numeral(start).format('0,0')} to {numeral(max).format('0,0')}</strong></span>
        <span style={{float: 'right', fontWeight: 'bold', userSelect: 'none'}}>
            {isFetching && <i className={'fa fa-refresh fa-spin'} style={{marginRight: '10px'}}/>}
            {start >= 100 && <a onClick={prevPage} style={{cursor: 'pointer'}}><i style={{fontSize: '80%'}} className="fa fa-chevron-left" />&nbsp;Previous</a>}
            {(start >= 100 && start + 100 <= totalHits) && '\u00A0\u00A0|\u00A0\u00A0'}
            {start + 100 <= totalHits && <a onClick={nextPage} style={{cursor: 'pointer'}}>Next&nbsp;<i style={{fontSize: '80%'}} className="fa fa-chevron-right" /></a>}
        </span>
      </div>

      <Overlay show={show} target={this.state.target} placement="left" container={this} onHide={this.handleHide} rootClose>
        <Popover id="popover-workflow-details" title="Workflow Details">
          <div style={{maxWidth: '400px', wordWrap: 'break-word'}}>
          <span style={{color: '#cc0000', fontWeight: 'bold'}}>
            {!isEmpty(reasonForIncompletion) && <span>{reasonForIncompletion}<hr/></span>}
          </span>
          <b>Input</b>
          <br />
          <span className="small">{input}</span>
          <hr />
          <b>Output</b>
          <br />
          <span className="small">{output}</span>
          </div>
        </Popover>
      </Overlay>

      <BootstrapTable data={results} striped={true} hover={true}
                           search={false} exportCSV={false}
                           pagination={false} selectRow={selectRow}
                           options={{sizePerPage: 100}} tableStyle={{backgroundColor: 'red'}}>
        <TableHeaderColumn dataField="workflowType" dataAlign="left" dataSort>Workflow</TableHeaderColumn>
        <TableHeaderColumn dataField="workflowId" isKey dataSort dataFormat={linkMaker}>Workflow ID</TableHeaderColumn>
        <TableHeaderColumn dataField="status" dataSort>Status</TableHeaderColumn>
        <TableHeaderColumn dataField="startTime" dataSort dataAlign="right" dataFormat={formatDate}>Start Time</TableHeaderColumn>
        <TableHeaderColumn dataField="updateTime" dataSort dataAlign="right" dataFormat={formatDate}>Last Updated</TableHeaderColumn>
        <TableHeaderColumn dataField="endTime" dataSort dataAlign="right" dataFormat={formatDate}>End Time</TableHeaderColumn>
        <TableHeaderColumn dataField="reasonForIncompletion" width="250">Failure Reason</TableHeaderColumn>
        <TableHeaderColumn dataField="failedReferenceTaskNames" width="250">Failed Tasks</TableHeaderColumn>
        <TableHeaderColumn dataField="input" width="250">Input</TableHeaderColumn>
        <TableHeaderColumn dataField="workflowId" width="75" dataAlign="center" dataFormat={this.formatDetails}>Details</TableHeaderColumn>
      </BootstrapTable>
    </div>
  }
}

WorkflowTable.propTypes = {
  start: PropTypes.number.isRequired,
  totalHits: PropTypes.number.isRequired,
  selected: PropTypes.arrayOf(PropTypes.string).isRequired,
  results: PropTypes.array.isRequired,
  isFetching: PropTypes.bool.isRequired,
  handleSelect: PropTypes.func.isRequired,
  handleSelectAll: PropTypes.func.isRequired,
  nextPage: PropTypes.func.isRequired,
  prevPage: PropTypes.func.isRequired
};
