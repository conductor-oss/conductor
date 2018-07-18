import React from 'react';
import { connect } from 'react-redux';
import { Panel, Button } from 'react-bootstrap';

class ErrorPage extends React.Component {
  state = {
    alertVisible: false,
    status: '',
    details: ''
  };

  componentWillReceiveProps(nextProps) {
    let status = '';
    let details = '';
    if (nextProps.exception != null && nextProps.exception.response != null) {
      status = `${nextProps.exception.response.status} - ${nextProps.exception.response.statusText}`;
      details = JSON.stringify(nextProps.exception.response.text);
    } else {
      details = nextProps.exception;
    }
    this.setState({
      alertVisible: nextProps.error,
      status,
      details
    });
  }

  handleAlertDismiss = () => {
    this.setState({ alertVisible: false });
  };

  render() {
    if (this.state.alertVisible) {
      return (
        <span className="error">
          <Panel header={this.state.status} bsStyle="danger">
            <code>{this.state.details}</code>
          </Panel>
          <Button bsStyle="danger" onClick={this.handleAlertDismiss}>
            Close
          </Button>
          &nbsp;&nbsp;If you think this is not expected, file a bug with workflow admins.
        </span>
      );
    }
    return <span />;
  }
}

export default connect(state => state.workflow)(ErrorPage);
