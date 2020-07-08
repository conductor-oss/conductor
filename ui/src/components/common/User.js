import React, { Component } from 'react';
import http from "../../core/HttpClientClientSide";

class User extends Component {
  constructor(props) {
    super(props);

    this.state = {};

    http.get('/api/me/').then((data) => {
      this.setState(data);
    });
  }

  render() {
    if (!this.state.user || !this.state.user.name)
      return null;

    return (
      <div className="user-text">
        <span>
          <i className="fa fa-user" />
          <span title={`${this.state.user.email} (${this.state.user.roles})`}>
            {this.state.user.displayName || this.state.user.name}
          </span>
          {" "}
          <a href={this.state.logoutPath}>(Logout)</a>
        </span>
      </div>
    );
  }
}

export default User;
