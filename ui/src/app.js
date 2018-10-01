import React from 'react';
import { render } from 'react-dom';
import { Router, browserHistory } from 'react-router';
import { Provider } from 'react-redux';
import { createStore, applyMiddleware, compose } from 'redux';
import thunkMiddleware from 'redux-thunk';
import routeConfig from './routes';
import workflowApp from './reducers';

// eslint-disable-next-line no-underscore-dangle
const composeEnhancers = window.__REDUX_DEVTOOLS_EXTENSION_COMPOSE__ || compose;

const store = createStore(workflowApp, composeEnhancers(applyMiddleware(thunkMiddleware)));

function updateLocation() {
  store.dispatch({
    type: 'LOCATION_UPDATED',
    location: this.state.location.key
  });
}

render(
  <Provider store={store}>
    <Router history={browserHistory} routes={routeConfig} onUpdate={updateLocation} />
  </Provider>,
  document.getElementById('content')
);
