import React, { Component } from 'react';
import { Link } from 'react-router'
import { Navbar, Nav, NavItem, NavDropdown, MenuItem, Input, Button, Tabs, Tab } from 'react-bootstrap';
import http from '../../core/HttpClient';
import d3 from 'd3';
import update from 'react-addons-update';
import { connect } from 'react-redux';

const Header = React.createClass({
  getInitialState: function () {
    return {};
  },
  render: function () {
    return (

      <div>
        <header style={{marginLeft: '180px', top: '10px', position: 'fixed'}}>
          <input type="search" style={{height: '30px', border: 'none'}} placeholder="search"></input>
        </header>
      </div>
    );
  }
});
export default connect(state => state.workflow)(Header);
