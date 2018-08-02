import React from 'react';
import { connect } from 'react-redux';

const Header = () => (
  <div>
    <header style={{ marginLeft: '180px', top: '10px', position: 'fixed' }}>
      <input type="search" style={{ height: '30px', border: 'none' }} placeholder="search" />
    </header>
  </div>
);

export default connect(state => state.workflow)(Header);
