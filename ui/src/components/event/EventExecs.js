import React from 'react';
import { connect } from 'react-redux';
import Typeahead from 'react-bootstrap-typeahead';
import { Table, Grid, Row, Col } from 'react-bootstrap';
import { getEventHandlers } from '../../actions/WorkflowActions';

class EventExecs extends React.Component {
  state = {
    events: [],
    eventTypes: []
  };

  componentWillMount() {
    this.props.dispatch(getEventHandlers());
  }

  componentWillReceiveProps({ events }) {
    this.setState({ events });
  }

  render() {
    const { events } = this.state;
    const eventTypes = [];
    events.forEach(event => {
      eventTypes.push(event.event);
    });

    return (
      <div className="ui-content">
        <h1>Event Executions</h1>
        <h4>Search for Event Executions</h4>
        <Grid fluid>
          <Row className="show-grid">
            <Col md={8}>
              <Typeahead
                ref="eventTypes"
                options={eventTypes}
                placeholder="Filter by Event"
                multiple
                selected={this.state.eventTypes}
              />
            </Col>
            <Col md={4}>
              <Input type="text" ref="q" placeholder="search..." />
            </Col>
          </Row>
        </Grid>
        <br />
        <Table responsive striped hover condensed bordered>
          <thead>
            <tr>
              <th>Something here</th>
            </tr>
          </thead>
        </Table>
      </div>
    );
  }
}

export default connect(state => state.workflow)(EventExecs);
