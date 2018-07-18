import React from 'react';
import { Button, ButtonGroup, OverlayTrigger, Popover } from 'react-bootstrap';
import { connect } from 'react-redux';
import {
  terminateWorkflow,
  restartWorfklow,
  retryWorfklow,
  pauseWorfklow,
  resumeWorfklow
} from '../../../actions/WorkflowActions';

class WorkflowAction extends React.Component {
  terminate = () => {
    this.props.dispatch(terminateWorkflow(this.props.workflowId));
  };

  restart = () => {
    this.props.dispatch(restartWorfklow(this.props.workflowId));
  };

  retry = () => {
    this.props.dispatch(retryWorfklow(this.props.workflowId));
  };

  pause = () => {
    this.props.dispatch(pauseWorfklow(this.props.workflowId));
  };

  resume = () => {
    this.props.dispatch(resumeWorfklow(this.props.workflowId));
  };

  render() {
    const ttTerm = (
      <Popover id="popover-trigger-hover-focus" title="Terminate Workflow">
        Terminate workflow execution. All running tasks will be cancelled.
      </Popover>
    );

    const ttRestart = (
      <Popover id="popover-trigger-hover-focus" title="Restart Workflow">
        Restart the workflow from the begining (First Task)
      </Popover>
    );

    const ttRetry = (
      <Popover id="popover-trigger-hover-focus" title="Retry Last Failed Task">
        Retry the last failed task and put workflow in running state
      </Popover>
    );

    const ttPause = (
      <Popover id="popover-trigger-hover-focus" title="Pause Workflow">
        Pauses workflow execution. No new tasks will be scheduled until workflow has been resumed.
      </Popover>
    );

    const ttResume = (
      <Popover id="popover-trigger-hover-focus" title="Resume Workflow">
        Resume workflow execution
      </Popover>
    );

    const { terminating, restarting, retrying, pausing, resuming } = this.props;

    if (this.props.workflowStatus === 'RUNNING') {
      return (
        <ButtonGroup>
          <OverlayTrigger placement="bottom" overlay={ttTerm}>
            <Button
              bsStyle="danger"
              bsSize="xsmall"
              disabled={terminating}
              onClick={!terminating ? this.terminate : null}
            >
              {terminating ? <i className="fa fa-spinner fa-spin" /> : 'Terminate'}
            </Button>
          </OverlayTrigger>
          <OverlayTrigger placement="bottom" overlay={ttPause}>
            <Button bsStyle="warning" bsSize="xsmall" disabled={pausing} onClick={!pausing ? this.pause : null}>
              {pausing ? <i className="fa fa-spinner fa-spin" /> : 'Pause'}
            </Button>
          </OverlayTrigger>
        </ButtonGroup>
      );
    }
    if (this.props.workflowStatus === 'COMPLETED') {
      return (
        <OverlayTrigger placement="bottom" overlay={ttRestart}>
          <Button bsStyle="default" bsSize="xsmall" disabled={restarting} onClick={!restarting ? this.restart : null}>
            {restarting ? <i className="fa fa-spinner fa-spin" /> : 'Restart'}
          </Button>
        </OverlayTrigger>
      );
    } else if (this.props.workflowStatus === 'FAILED' || this.props.workflowStatus === 'TERMINATED') {
      return (
        <ButtonGroup>
          <OverlayTrigger placement="bottom" overlay={ttRestart}>
            <Button bsStyle="default" bsSize="xsmall" disabled={restarting} onClick={!restarting ? this.restart : null}>
              {restarting ? <i className="fa fa-spinner fa-spin" /> : 'Restart'}
            </Button>
          </OverlayTrigger>
          <OverlayTrigger placement="bottom" overlay={ttRetry}>
            <Button bsStyle="default" bsSize="xsmall" disabled={retrying} onClick={!retrying ? this.retry : null}>
              {retrying ? <i className="fa fa-spinner fa-spin" /> : 'Retry'}
            </Button>
          </OverlayTrigger>
        </ButtonGroup>
      );
    } else if (this.props.workflowStatus === 'PAUSED') {
      return (
        <ButtonGroup>
          <OverlayTrigger placement="bottom" overlay={ttResume}>
            <Button bsStyle="success" bsSize="xsmall" disabled={resuming} onClick={!resuming ? this.resume : null}>
              {resuming ? <i className="fa fa-spinner fa-spin" /> : 'Resume'}
            </Button>
          </OverlayTrigger>
        </ButtonGroup>
      );
    }
    return (
      <ButtonGroup>
        <OverlayTrigger placement="bottom" overlay={ttRestart}>
          <Button bsStyle="default" bsSize="xsmall" disabled={restarting} onClick={!restarting ? this.restart : null}>
            {restarting ? <i className="fa fa-spinner fa-spin" /> : 'Restart'}
          </Button>
        </OverlayTrigger>
      </ButtonGroup>
    );
  }
}

export default connect(state => state.workflow)(WorkflowAction);
