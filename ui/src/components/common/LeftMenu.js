import React from 'react';
import { Link } from 'react-router';
import { connect } from 'react-redux';

const menuPaths = {
  Workflow: [
    {
      header: true,
      label: 'Executions',
      href: '/events',
      icon: 'fa-star'
    },
    {
      label: 'All',
      href: '/workflow',
      icon: 'fa-circle-thin'
    },
    {
      label: 'Running',
      href: '/workflow?status=RUNNING',
      icon: 'fa-play-circle'
    },
    {
      label: 'Failed',
      href: '/workflow?status=FAILED&h=48',
      icon: 'fa-warning'
    },
    {
      label: 'Timed Out',
      href: '/workflow?status=TIMED_OUT&h=48',
      icon: 'fa-clock-o'
    },
    {
      label: 'Terminated',
      href: '/workflow?status=TERMINATED&h=48',
      icon: 'fa-ban'
    },
    {
      label: 'Completed',
      href: '/workflow?status=COMPLETED&h=48',
      icon: 'fa-bullseye'
    },
    {
      header: true,
      label: 'Metadata',
      href: '/events',
      icon: 'fa-star'
    },
    {
      label: 'Workflow Defs',
      href: '/workflow/metadata',
      icon: 'fa-code-fork'
    },
    {
      label: 'Tasks',
      href: '/workflow/metadata/tasks',
      icon: 'fa-tasks'
    },
    {
      header: true,
      label: 'Workflow Events',
      href: '/events',
      icon: 'fa-star'
    },
    {
      label: 'Event Handlers',
      href: '/events',
      icon: 'fa-star'
    },
    {
      header: true,
      label: 'Task Queues',
      href: '/events',
      icon: 'fa-star'
    },
    {
      label: 'Poll Data',
      href: '/workflow/queue/data',
      icon: 'fa-exchange'
    }
  ]
};

class LeftMenu extends React.Component {
  state = {
    minimize: false,
    loading: false
  };

  componentDidMount() {
    window.addEventListener('resize', this.handleResize);
  }

  componentWillReceiveProps({ fetching: loading, minimize }) {
    this.setState({ loading, minimize });
  }

  componentWillUnmount() {
    window.removeEventListener('resize', this.handleResize);
  }

  handleResize = () => {
    this.setState({ minimize: window.innerWidth < 600 });
  };

  render() {
    const { minimize } = this.state;
    const { appName = 'Workflow' } = this.props;

    const width = minimize ? '50px' : '176px';

    const display = minimize ? 'none' : '';
    const menuItems = [];
    let keyVal = 0;

    // eslint-disable-next-line array-callback-return
    menuPaths[appName].map(cv => {
      const iconClass = `fa ${cv.icon}`;
      if (cv.header === true) {
        menuItems.push(
          <div className="" key={`key-${(keyVal += 1)}`}>
            <div className="menuHeader">
              <i className="fa fa-angle-down" />&nbsp;{cv.label}
            </div>
          </div>
        );
      } else {
        menuItems.push(
          <Link to={cv.href} key={`key-${(keyVal += 1)}`}>
            <div className="menuItem">
              <i className={iconClass} style={{ width: '20px' }} />
              <span style={{ marginLeft: '10px', display }}>{cv.label}</span>
            </div>
          </Link>
        );
      }
    });

    return (
      <div className="left-menu" style={{ width }}>
        <div className="logo textual pull-left">
          <a href="/" title="Conductor">
            <h4>
              <i className={this.state.loading ? 'fa fa-bars fa-spin fa-1x' : 'fa fa-bars'} />{' '}
              {this.state.loading || minimize ? '' : 'Conductor'}
            </h4>
          </a>
        </div>
        <div className="menuList">{menuItems}</div>
      </div>
    );
  }
}

export default connect(state => state.workflow)(LeftMenu);
