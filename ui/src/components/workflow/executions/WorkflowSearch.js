import React, {Component} from "react";
import {connect} from "react-redux";
import PropTypes from "prop-types";
import {Button, Col, Grid, Input, Panel, Row} from 'react-bootstrap';
import Typeahead from 'react-bootstrap-typeahead';

import get from "lodash/get";
import filter from "lodash/filter";
import isEmpty from "lodash/isEmpty";
import negate from "lodash/negate";
import parseInt from "lodash/parseInt";
import split from "lodash/split";
import toString from "lodash/toString";

import {changeSearch, fetchSearchResults} from '../../../actions/search';
import {getWorkflowDefs} from '../../../actions/metadata';

const statusList = ['RUNNING', 'COMPLETED', 'FAILED', 'TIMED_OUT', 'TERMINATED', 'PAUSED'];

export function getSearchQuery(query) {
  const searchQuery = get(query, "q", "");
  const entirely = toString(get(query, "f", "false")) === "true";
  const types = filter(split(get(query, "workflowTypes", ""), ","), negate(isEmpty));
  const states = filter(split(get(query, "status", ""), ","), negate(isEmpty));
  const cutoff = get(query, "h", "");
  const start = parseInt(get(query, "start", "0"));

  return {query: searchQuery, entirely, types, states, cutoff, start};
}

class WorkflowSearch extends Component {
  constructor(props) {
    super(props);

    this.handleQueryChange = this.handleQueryChange.bind(this);
    this.handleEntirelyChange = this.handleEntirelyChange.bind(this);
    this.handleTypesChange = this.handleTypesChange.bind(this);
    this.handleStatesChange = this.handleStatesChange.bind(this);
    this.handleCutoffChange = this.handleCutoffChange.bind(this);
    this.handleSubmit = this.handleSubmit.bind(this);

    // pull out variables from the location onLoad
    const {location: {query}, changeSearch, getWorkflowDefs, fetchSearchResults} = props;

    const search = getSearchQuery(query);

    // update the state
    changeSearch(search);
    fetchSearchResults();

    // fetch workflow types
    getWorkflowDefs();
  }

  handleQueryChange({target: {value}}) {
    const {search, changeSearch} = this.props;

    changeSearch({...search, query: value});
  }

  handleEntirelyChange({target: {checked}}) {
    const {search, changeSearch} = this.props;

    changeSearch({...search, entirely: checked});
  }

  handleTypesChange(value) {
    const {search, changeSearch} = this.props;

    changeSearch({...search, types: value});
  }

  handleStatesChange(value) {
    const {search, changeSearch} = this.props;

    changeSearch({...search, states: value});
  }

  handleCutoffChange({target: {value}}) {
    const {search, changeSearch} = this.props;

    changeSearch({...search, cutoff: value});
  }

  handleSubmit(e)  {
    const {history, search, changeSearch, fetchSearchResults} = this.props;

    e.preventDefault();

    const types = encodeURIComponent(toString(search.types));
    const states = toString(search.states);

    // always reset start to 0 for a new search

    changeSearch({...search, start: 0});

    const newUrl = `/workflow?q=${search.query}&f=${search.entirely}` +
        `&h=${search.cutoff}&workflowTypes=${types}&status=${states}&start=0`;

    history.pushState(null, newUrl);
    fetchSearchResults();
  }

  render() {
    const {search: {query, entirely, types, states, cutoff, isFetching}, workflows} = this.props;

    return <Panel header="Filter Workflows (Press Enter to search)">
        <Grid fluid>
          <form className="commentForm" onSubmit={this.handleSubmit}>
          <Row className="show-grid">
            <Col md={4}>
              <Input type="input" placeholder="Search"
                  value={query} onChange={this.handleQueryChange}
              />
              &nbsp;<i className="fa fa-angle-up fa-1x" />&nbsp;&nbsp;
              <label className="small nobold">Free Text Query</label>
              &nbsp;&nbsp;
              <label className="small nobold">
                <input type="checkbox" checked={entirely} onChange={this.handleEntirelyChange}/>
                &nbsp;Search for entire string</label>
            </Col>
            <Col md={4}>
              <Typeahead onChange={this.handleTypesChange} options={workflows}
                         placeholder="Filter by workflow type" multiple selected={types}/>
              &nbsp;<i className="fa fa-angle-up fa-1x" />&nbsp;&nbsp;
              <label className="small nobold">
              Filter by Workflow Type
            </label>
            </Col>
            <Col md={2}>
              <Typeahead ref="status" onChange={this.handleStatesChange} options={statusList}
                         placeholder="Filter by status" selected={states} multiple/>
              &nbsp;<i className="fa fa-angle-up fa-1x" />&nbsp;&nbsp;
              <label className="small nobold">Filter by Workflow Status</label>
            </Col>
            <Col md={2}>
              <Input className="number-input" type="text" groupClassName="inline"
                  value={cutoff} onChange={this.handleCutoffChange}/>
              &nbsp;&nbsp;&nbsp;
              <Button disabled={isFetching} bsStyle="success" type="submit" className="search-label btn">
                {isFetching && <i className="fa fa-refresh fa-spin" /> || <i className="fa fa-search" />}&nbsp;&nbsp;Search
              </Button>
              <br />&nbsp;&nbsp;&nbsp;
              <i className="fa fa-angle-up fa-1x" />&nbsp;&nbsp;
              <label className="small nobold">Created (in past hours)</label>
            </Col>
          </Row>
          </form>
        </Grid>
        <form />
      </Panel>
  }
}

WorkflowSearch.propTypes = {
  changeSearch: PropTypes.func.isRequired,
  fetchSearchResults: PropTypes.func.isRequired,
  getWorkflowDefs: PropTypes.func.isRequired,
  history: PropTypes.object.isRequired,
  location: PropTypes.object.isRequired,
  workflows: PropTypes.array.isRequired,
  search: PropTypes.shape({
    isFetching: PropTypes.bool.isRequired,
    query: PropTypes.string.isRequired,
    entirely: PropTypes.bool.isRequired,
    types: PropTypes.arrayOf(PropTypes.string).isRequired,
    states: PropTypes.arrayOf(PropTypes.string).isRequired,
    cutoff: PropTypes.string.isRequired
  })
};

const mapDispatchToProps = {changeSearch, fetchSearchResults, getWorkflowDefs};


export default connect(null, mapDispatchToProps)(WorkflowSearch);
