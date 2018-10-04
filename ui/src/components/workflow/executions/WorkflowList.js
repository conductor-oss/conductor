/* eslint-disable no-restricted-globals */

import React, { Component } from 'react';
import PropTypes from "prop-types";
import { connect } from 'react-redux';
import { createSelector } from 'reselect'
import {changeSearch, fetchSearchResults} from '../../../actions/search';
import { performBulkOperation } from '../../../actions/bulk';
import WorkflowSearch from "./WorkflowSearch";
import WorkflowTable from './WorkflowTable';

import difference from 'lodash/difference';
import filter from "lodash/filter";
import get from "lodash/get";
import includes from "lodash/includes";
import intersection from 'lodash/intersection';
import isEmpty from "lodash/isEmpty";
import map from 'lodash/map';
import size from "lodash/size";
import uniq from 'lodash/uniq';
import without from 'lodash/without';
import WorkflowBulkAction from './WorkflowBulkAction';


class Workflow extends Component {
  constructor(props) {
    super(props);

    this.nextPage = this.nextPage.bind(this);
    this.prevPage = this.prevPage.bind(this);

    this.bulkProcess = this.bulkProcess.bind(this);
    this.onChangeBulkProcessSelection = this.onChangeBulkProcessSelection.bind(this);

    this.handleSelect = this.handleSelect.bind(this);
    this.handleSelectAll = this.handleSelectAll.bind(this);

    this.state = {
      selected: [],
      bulkProcessOperation: "pause",
      bulkValidationMessage: ""
    };
  }

  componentDidUpdate(prevProps) {
    const {search} = this.props;
    const {selected} = this.state;

    // remove from selection after updating filters
    if (prevProps.search.results !== search.results) {
      this.setState({
        selected: intersection(map(search.results, 'workflowId'), selected),
        bulkValidationMessage: ""
      });
    }
  }

  componentWillReceiveProps({bulk: {successfulResults}}) {
    // remove successful bulk workflows from selection
    const remaining = difference(this.state.selected, successfulResults);

    if (size(remaining) !== size(this.state.selected)) {
      this.setState({selected: remaining});
    }
  }

  nextPage() {
    const {changeSearch, fetchSearchResults, search} = this.props;
    const {start} = search;

    changeSearch({...search, start: start + 100});
    fetchSearchResults();
  }

  prevPage() {
    const {changeSearch, fetchSearchResults, search} = this.props;
    const {start} = search;

    changeSearch({...search, start: start - 100});
    fetchSearchResults();
  }

  onChangeBulkProcessSelection({target: {value}}) {
    this.setState({bulkProcessOperation: value, bulkValidationMessage: ""})
  }

  bulkProcess() {
    const {selected, bulkProcessOperation} = this.state;
    const {performBulkOperation}  = this.props;

    if (size(selected) === 0) {
      this.setState({bulkValidationMessage: "Error: No workflows selected"});

      return;
    }

    performBulkOperation(bulkProcessOperation, selected);
  }

  handleSelect({workflowId, id}, isSelected) {
    const {selected} = this.state;

    if (isSelected){
      this.setState({
        selected: uniq([...selected, workflowId]),
        bulkValidationMessage: ""
      });
    } else {
      this.setState({
        selected: without(selected, workflowId),
        bulkValidationMessage: ""
      });
    }

    return false;
  }

  handleSelectAll(isSelected, rows) {
    if (isSelected){
      this.setState({selected: map(rows, 'workflowId'), bulkValidationMessage: ""});
    } else {
      this.setState({selected: [], bulkValidationMessage: ""});
    }

    return false;
  }

 render() {
    const {location, history, bulk, metadata, search} = this.props;
    const {workflows} = metadata;
    const {bulkProcessOperation, bulkValidationMessage, selected} = this.state;


    return (
      <div className="ui-content">
        <WorkflowSearch search={search} workflows={workflows} location={location} history={history}/>

        <WorkflowBulkAction isFetching={bulk.isFetching} selectedCount={size(selected)}
                            validationMessage={bulkValidationMessage}
                            successfulResults={bulk.successfulResults}
                            errorResults={bulk.errorResults}
                            bulkProcess={this.bulkProcess}
                            onChangeBulkProcessSelection={this.onChangeBulkProcessSelection}
                            bulkProcessOperation={bulkProcessOperation}/>

        <WorkflowTable results={search.results} selected={selected}
                       isFetching={search.isFetching}
                       totalHits={search.totalHits}
                       start={search.start}
                       handleSelect={this.handleSelect}
                       handleSelectAll={this.handleSelectAll}
                       nextPage={this.nextPage}
                       prevPage={this.prevPage}
        />

        <br />
        <br />
      </div>
    )
  }
}

Workflow.propTypes = {
  changeSearch: PropTypes.func.isRequired,
  fetchSearchResults: PropTypes.func.isRequired,
  performBulkOperation: PropTypes.func.isRequired,
  search: PropTypes.shape({
    isFetching: PropTypes.bool.isRequired,
    start: PropTypes.number.isRequired,
    types: PropTypes.array.isRequired,
    results: PropTypes.array.isRequired,
    totalHits: PropTypes.number.isRequired
  }),
  metadata: PropTypes.shape({
    workflows: PropTypes.array.isRequired
  }),
  bulk: PropTypes.shape({
    isFetching: PropTypes.bool.isRequired,
    error: PropTypes.string,
    successfulResults: PropTypes.arrayOf(PropTypes.string).isRequired,
    errorResults: PropTypes.objectOf(PropTypes.string).isRequired
  })
};

const getFilteredWorkflows = (results, states) => {
  if (isEmpty(states)) {
    return results;
  } else {
    return filter(results, r => includes(states, get(r, 'status')));
  }
};

const getStates = state => state.states;
const getResults = state => state.results;

const getFilteredWorkflowSelector = createSelector([getResults, getStates], getFilteredWorkflows);

function mapStateToProps({bulk, metadata, search}) {
  return {
    search: {...search,
      results: getFilteredWorkflowSelector(search)
    }, metadata, bulk
  };
}

const mapDispatchToProps = {changeSearch, fetchSearchResults, performBulkOperation};

export default connect(mapStateToProps, mapDispatchToProps)(Workflow);
