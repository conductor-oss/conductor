import React, { Component } from 'react';
import http from '../../core/HttpClient';

export default class Footer extends Component {
  constructor(props) {
    super(props);

    this.state = {
      sys: {}
    };

    http.get('/api/sys/').then((data) => {
      this.setState({
        sys: data.sys
      });

      window.sys = this.state.sys;
    });
  }

  render() {
    return (
      <div className="Footer navbar-fixed-bottom">
        <div className="Footer-container left">
          <span className="Footer-text">Server: </span><a href={this.state.sys.server} target="_new" className="small" style={{color:'white'}}>{this.state.sys.server}</a>
          <span style={{float:'right'}}>
          <span className="Footer-text">Version: {this.state.sys.version} | Build Date: {this.state.sys.buildDate}</span>
          </span>
        </div>
      </div>
    );
  }
}
