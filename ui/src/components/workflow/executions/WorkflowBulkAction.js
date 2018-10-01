import React, {Component} from 'react';
import PropTypes from "prop-types";
import {Button, Col, Grid, Input, Panel, Row} from 'react-bootstrap';
import isEmpty from 'lodash/isEmpty';
import map from 'lodash/map';
import size from 'lodash/size';

export default class WorkflowBulkAction extends Component {
  render() {
    const {bulkProcess, isFetching, selectedCount, successfulResults, errorResults,
      bulkProcessOperation, onChangeBulkProcessSelection, validationMessage} = this.props;

    const successMessage = size(successfulResults) > 0 ? map(successfulResults, id => <p key={id}>{id}</p>) : <p key="NONE">None</p>;
    const errorMessage = size(errorResults) > 0 ? map(errorResults, id => <p key={id}>{id}</p>) : null;

    return <Panel header="Bulk Processing  (click to expand)" collapsible>
      <label>Select workflows from table below</label>
      <Grid fluid={true}>
        <Row className="show-grid">
          <Col md={3}>
            <Input plaintext><span style={{"fontSize":"150%"}}>{selectedCount}</span></Input>
            &nbsp;
            <i className="fa fa-angle-up fa-1x"/>
            &nbsp;&nbsp;&nbsp;
            <label className="small nobold">Number of Workflows Selected</label>
          </Col>
          <Col md={2}>
            <Input type="select" onChange={onChangeBulkProcessSelection} value={bulkProcessOperation}>
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
            <Button bsStyle="success" onClick={bulkProcess} value="Process"
                    className="btn" disabled={isFetching}>Process</Button>
            &nbsp;
          </Col>
          <Col md={4}>
            {isFetching && <i style={{"fontSize":"150%"}} className="fa fa-spinner fa-spin"/>}
            {(size(successfulResults) > 0 && size(errorResults) === 0) && <span style={{"fontSize":"150%", "color":"green"}}>All Successful!</span> || null}
            {size(errorResults) > 0 && <span>
              <span style={{"fontSize":"125%", "color":"green"}}>Successful: {successMessage}</span>
              <span style={{"fontSize":"125%", "color":"red"}}>Errors: {errorMessage}</span>
            </span>}
            {!isEmpty(validationMessage) && <span style={{"fontSize":"150%", "color":"red"}}>{validationMessage}</span>}
          </Col>
        </Row>
      </Grid>
    </Panel>
  }
}

WorkflowBulkAction.propTypes = {
  bulkProcess: PropTypes.func.isRequired,
  bulkProcessOperation: PropTypes.string.isRequired,
  error: PropTypes.string,
  errorResults: PropTypes.objectOf(PropTypes.string).isRequired,
  isFetching: PropTypes.bool.isRequired,
  onChangeBulkProcessSelection: PropTypes.func.isRequired,
  successfulResults: PropTypes.arrayOf(PropTypes.string).isRequired,
  selectedCount: PropTypes.number.isRequired,
  validationMessage: PropTypes.string.isRequired
};
