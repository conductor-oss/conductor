import { Jumbotron } from 'react-bootstrap';
import React, { Component } from 'react';
import { connect } from 'react-redux'

class Introduction extends Component {

  constructor(props) {
    super(props);
  }

  render() {
    return (
        <Jumbotron className="jumbotron">
        <header className="codrops-header">
            <h1>Conductor User Interface</h1>
          </header>
          <div className="column">
            <info>Conductor User Interface</info>
          </div>
          <div className="column">
            <button className="button-pill">Conductor</button>
          </div>

        </Jumbotron>
    );
  }
}

export default connect(state => state)(Introduction);
