import React from 'react';
import moment from 'moment';
import { BootstrapTable, TableHeaderColumn } from 'react-bootstrap-table';
import { connect } from 'react-redux';
import { getQueueData } from '../../../actions/WorkflowActions';

class QueueListList extends React.Component {
  state = {
    queueData: []
  };

  componentWillMount() {
    this.props.dispatch(getQueueData());
  }

  componentWillReceiveProps({ queueData }) {
    this.setState({ queueData });
  }

  render() {
    const { queueData } = this.state;

    function formatName(_, { queueName, domain }) {
      return domain != null ? `${queueName} (${domain})` : queueName;
    }

    function formatDate(_, row) {
      return moment(row.lastPollTime).fromNow();
    }

    return (
      <div className="ui-content">
        <h1>Queues</h1>
        <BootstrapTable data={queueData} striped hover search exportCSV={false} pagination={false}>
          <TableHeaderColumn dataField="queueName" isKey dataAlign="left" dataSort dataFormat={formatName}>
            Name (Domain)
          </TableHeaderColumn>
          <TableHeaderColumn dataField="qsize" dataSort>
            Size
          </TableHeaderColumn>
          <TableHeaderColumn dataField="lastPollTime" dataSort dataFormat={formatDate}>
            Last Poll Time
          </TableHeaderColumn>
          <TableHeaderColumn dataField="workerId" dataSort>
            Last Polled By
          </TableHeaderColumn>
        </BootstrapTable>
      </div>
    );
  }
}

export default connect(state => state.workflow)(QueueListList);
