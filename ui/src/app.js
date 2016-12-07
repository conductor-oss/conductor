import React from 'react';
import {render} from 'react-dom';
import { Router, browserHistory } from 'react-router'
import routeConfig from './routes';
import { Provider } from 'react-redux'
import { createStore, applyMiddleware } from 'redux'
import thunkMiddleware from 'redux-thunk'
import workflowApp from './reducers'
import { syncHistoryWithStore, routerReducer } from 'react-router-redux'

let store = createStore(workflowApp, applyMiddleware(
  thunkMiddleware
));

function updateLocation() {
  store.dispatch({
    type: 'LOCATION_UPDATED',
    'location' : this.state.location.key
  });
}

render(
  <Provider store={store}>
    <Router history={browserHistory} routes={routeConfig} onUpdate={updateLocation}/>
  </Provider>,
  document.getElementById('content'))
