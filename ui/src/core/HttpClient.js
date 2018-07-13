/*! React Starter Kit | MIT License | http://www.reactstarterkit.com/ */

import request from 'superagent';
import { canUseDOM } from 'fbjs/lib/ExecutionEnvironment';

function getUrl(path) {
  if (path.startsWith('http') || canUseDOM) {
    return path;
  }

  return process.env.WEBSITE_HOSTNAME
    ? `http://${process.env.WEBSITE_HOSTNAME}${path}`
    : `http://127.0.0.1:${global.server.get('port')}${path}`;
}

const getAuthHeader = token => {
  const key = token ? 'Authorization' : '';
  const val = token ? token : '';
  return {
    key,
    val
  };
};

const HttpClient = {
  get: (path, token) =>
    new Promise((resolve, reject) => {
      const auth = getAuthHeader(token);
      request
        .get(getUrl(path))
        .accept('application/json')
        .set(auth.key, auth.val)
        .end((err, res) => {
          if (err) {
            reject(err);
          } else {
            resolve(res.body);
          }
        });
    }),
  post: (path, data, token) =>
    new Promise((resolve, reject) => {
      const auth = getAuthHeader(token);
      request
        .post(path, data)
        .set('Accept', 'application/json')
        .set(auth.key, auth.val)
        .end((err, res) => {
          if (err || !res.ok) {
            console.error('Error on post! ' + res);
            reject(err);
          } else {
            if (res.body) {
              resolve(res.body);
            } else {
              resolve(res);
            }
          }
        });
    }),

  put: (path, data, token) => {
    return new Promise((resolve, reject) => {
      const auth = getAuthHeader(token);
      request
        .put(path, data)
        .set('Accept', 'application/json')
        .set(auth.key, auth.val)
        .end((err, res) => {
          if (err || !res.ok) {
            console.error('Error on post! ' + res);
            reject(err);
          } else {
            resolve(res.body);
          }
        });
    });
  },

  delete: (path, data, token) =>
    new Promise((resolve, reject) => {
      const auth = getAuthHeader(token);
      request
        .del(path, data)
        .set('Accept', 'application/json')
        .set(auth.key, auth.val)
        .end((err, res) => {
          if (err || !res.ok) {
            console.error('Error on post! ' + err);
            reject(err);
          } else {
            resolve(res);
          }
        });
    })
};

export default HttpClient;
