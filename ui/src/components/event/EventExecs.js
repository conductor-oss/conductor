import React, { Component } from 'react';
import { Link, browserHistory } from 'react-router';
import { Breadcrumb, BreadcrumbItem, Input, Well, Button, Panel, DropdownButton, MenuItem, Popover, OverlayTrigger, ButtonGroup, Table, Grid, Row, Col } from 'react-bootstrap';
import {BootstrapTable, TableHeaderColumn} from 'react-bootstrap-table';
import { connect } from 'react-redux';
import { getEvents, getEventHandlers } from '../../actions/WorkflowActions';
import Typeahead from 'react-bootstrap-typeahead';

const EventExecs = React.createClass({

  getInitialState() {
    return {
      events: [],
      executions: [],
      eventTypes: []
    }
  },
  componentWillMount(){
    this.props.dispatch(getEventHandlers());
    //this.props.dispatch(getEvents());
  },
  componentWillReceiveProps(nextProps) {
    this.state.executions = nextProps.executions;
    this.state.events = nextProps.events;
  },

  render() {
    let executions = this.state.executions;
    let events = this.state.events;
    let eventTypes = [];
    events.forEach(event => {
      eventTypes.push(event.event);
    });


    return (
      <div className="ui-content">
        <h1>Event Executions</h1>
        <h4>Search for Event Executions</h4>
        <Grid fluid={true}>
          <Row className="show-grid">
            <Col md={8}>
              <Typeahead ref="eventTypes"  options={eventTypes} placeholder="Filter by Event" multiple={true} selected={this.state.eventTypes}/>
            </Col>
            <Col md={4}>
              <Input type="text" ref="q" placeholder="search..."></Input>
            </Col>
          </Row>
        </Grid>
        <br/>
        <Table responsive={true} striped={true} hover={true} condensed={false} bordered={true}>
        <thead>
          <tr>
            <th>Something here</th>
          </tr>
        </thead>
        </Table>
      </div>
    );
  }
});
export default connect(state => state.workflow)(EventExecs);
