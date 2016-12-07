import React from 'react';
import packageJSON from '../../package.json';
import Header from './common/Header';
import Footer from './common/Footer';
import ErrorPage from './common/Error'
import LeftMenu from './common/LeftMenu'
import { connect } from 'react-redux';

const App = React.createClass({

  getInitialState() {
    return {
      minimize: false
    };
  },
  handleResize(e) {
    this.setState({windowWidth: window.innerWidth, minimize: window.innerWidth < 600});
  },

  componentDidMount() {
   window.addEventListener('resize', this.handleResize);
 },

 componentWillUnmount() {
   window.removeEventListener('resize', this.handleResize);
 },

  render() {
    const version = packageJSON.version;
    const marginLeft = this.state.minimize?'52px':'177px';
    return !this.props.error ? (
      <div style={{height: '100%'}}>
        <div style={{height: '100%'}}>
          <LeftMenu version={version} minimize={this.state.minimize}/>
          <ErrorPage/>
          <div className="appMainBody" style={{width: document.body.clientWidth-180, marginLeft: marginLeft, marginTop: '10px', paddingRight:'20px'}}>
              {this.props.children}
          </div>
        </div>
        <Footer/>
      </div>
    ) : this.props.children;
  }

});


export default connect(state => state.global)(App);
