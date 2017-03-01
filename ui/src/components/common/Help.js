import { Jumbotron } from 'react-bootstrap';
import React, { Component } from 'react';
import { connect } from 'react-redux'

class Introduction extends Component {

  constructor(props) {
    super(props);
  }

  render() {
    return (
        <div className="ui-content">
          <h1>Help & Documentation</h1>
            <h4>User Guide</h4>
            <ul><li><a href='http://netflix.github.io/conductor/' target='_new'>https://netflix.github.io/conductor/</a></li></ul>
            <h4>Frequently Asked Questions</h4>
            <ul><li><a href='http://netflix.github.io/conductor/' target='_new'>https://netflix.github.io/conductor/faq/</a></li></ul>
        </div>
    );
  }
}

export default connect(state => state)(Introduction);
