import React from 'react';
import { connect } from 'react-redux';
import Footer from './common/Footer';
import ErrorPage from './common/Error';
import LeftMenu from './common/LeftMenu';
import packageJSON from '../../package.json';

class App extends React.Component {
  state = {
    minimize: false
  };

  componentDidMount() {
    window.addEventListener('resize', this.handleResize);
  }

  componentWillUnmount() {
    window.removeEventListener('resize', this.handleResize);
  }

  handleResize = () => {
    this.setState({ minimize: window.innerWidth < 600 });
  };

  render() {
    const { version } = packageJSON;
    const marginLeft = this.state.minimize ? '52px' : '177px';
    return !this.props.error ? (
      <div style={{ height: '100%' }}>
        <div style={{ height: '100%' }}>
          <LeftMenu version={version} minimize={this.state.minimize} />
          <ErrorPage />
          <div
            className="appMainBody"
            style={{
              width: document.body.clientWidth - 180,
              marginLeft,
              marginTop: '10px',
              paddingRight: '20px'
            }}
          >
            {this.props.children}
          </div>
        </div>
        <Footer />
      </div>
    ) : (
      this.props.children
    );
  }
}

export default connect(state => state.global)(App);
