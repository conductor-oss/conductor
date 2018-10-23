import React from 'react';
import { render } from 'react-dom';
import { Router, browserHistory } from 'react-router';
import { Provider } from 'react-redux';
import { createStore, applyMiddleware, compose } from 'redux';
import createSagaMiddleware from 'redux-saga';
import thunkMiddleware from 'redux-thunk';
import routeConfig from './routes';
import reducers from './reducers';
import rootSaga from './sagas';

const sagaMiddleware = createSagaMiddleware();

let store;

const middlewares = [thunkMiddleware, sagaMiddleware];

if (process.env.NODE_ENV === "development") {
  // eslint-disable-next-line no-underscore-dangle
  const composeEnhancers = window.__REDUX_DEVTOOLS_EXTENSION_COMPOSE__ || compose;

  store = createStore(reducers, composeEnhancers(applyMiddleware(...middlewares)));
} else {
  store = createStore(reducers, applyMiddleware(...middlewares));
}

sagaMiddleware.run(rootSaga);

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
