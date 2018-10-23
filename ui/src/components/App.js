import React, {Component} from 'react';
import Footer from './common/Footer';
import ErrorPage from './common/Error';
import LeftMenu from './common/LeftMenu';
import packageJSON from '../../package.json';

import debounce from 'lodash/debounce';

class App extends Component {
  constructor(props) {
    super(props);

    this.state = {
      minimize: false
    };

    // make resize more performant
    this.handleResize = debounce(this.handleResize.bind(this), 250);
  }

  componentDidMount() {
    window.addEventListener('resize', this.handleResize);
  }

  componentWillUnmount() {
    window.removeEventListener('resize', this.handleResize);
  }

  handleResize () {
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

export default App;
