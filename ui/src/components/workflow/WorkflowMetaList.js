import React from 'react';
import { Link } from 'react-router';
import { connect } from 'react-redux';
import { BootstrapTable, TableHeaderColumn } from 'react-bootstrap-table';
import { getWorkflowDefs } from '../../actions/WorkflowActions';

class WorkflowMetaList extends React.Component {
  state = {
    workflows: []
  };

  componentWillMount() {
    this.props.dispatch(getWorkflowDefs());
  }

  componentWillReceiveProps({ workflows }) {
    this.setState({ workflows });
  }

  render() {
    const { workflows } = this.state;

    function jsonMaker(cell) {
      return JSON.stringify(cell);
    }

    function taskMaker(cell) {
      if (cell == null) {
        return '';
      }
      return JSON.stringify(
        cell.map(task => {
          return task.name;
        })
      );
    }

    function nameMaker(cell, row) {
      return (
        <Link to={`/workflow/metadata/${row.name}/${row.version}`}>
          {row.name} / {row.version}
        </Link>
      );
    }

    return (
      <div className="ui-content">
        <h1>Workflows</h1>
        <BootstrapTable data={workflows} striped hover search exportCSV={false} pagination={false}>
          <TableHeaderColumn dataField="name" isKey dataAlign="left" dataSort dataFormat={nameMaker}>
            Name/Version
          </TableHeaderColumn>
          <TableHeaderColumn dataField="inputParameters" dataSort dataFormat={jsonMaker}>
            Input Parameters
          </TableHeaderColumn>
          <TableHeaderColumn dataField="tasks" hidden={false} dataFormat={taskMaker}>
            Tasks
          </TableHeaderColumn>
        </BootstrapTable>
      </div>
    );
  }
}

export default connect(state => state.workflow)(WorkflowMetaList);
