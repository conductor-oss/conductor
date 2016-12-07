/*! React Starter Kit | MIT License | http://www.reactstarterkit.com/ */

import request from 'superagent';
import { canUseDOM } from 'fbjs/lib/ExecutionEnvironment';

function getUrl(path) {
  if (path.startsWith('http') || canUseDOM) {
    return path;
  }

  return process.env.WEBSITE_HOSTNAME ?
    `http://${process.env.WEBSITE_HOSTNAME}${path}` :
    `http://127.0.0.1:${global.server.get('port')}${path}`;
}

const HttpClient = {

  get: (path) => new Promise((resolve, reject) => {
    request
      .get(getUrl(path))
      .accept('application/json')
      .end((err, res) => {
        if (err) {
          //if (err.status === 404) {
          //  resolve(null);
          //} else {
            reject(err);
          //}
        } else {
          resolve(res.body);
        }
      });
  }),

  post: (path, data) => new Promise((resolve, reject) => {
    request
      .post(path, data)
      .set('Accept', 'application/json')
      .end((err, res) => {
        if (err || !res.ok) {
          console.error('Error on post! ' + res);
          reject(err);
        } else {
          if(res.body){
            resolve(res.body);
          }else{
            resolve(res);
          }

        }
      });
  }),

  put: (path, data) => new Promise((resolve, reject) => {
    request
      .put(path, data)
      .set('Accept', 'application/json')
      .end((err, res) => {
        if (err || !res.ok) {
          console.error('Error on post! ' + res);
          reject(err);
        } else {
          resolve(res.body);
        }
      });
  }),

  delete: (path, data) => new Promise((resolve, reject) => {
    request
      .del(path, data)
      .set('Accept', 'application/json')
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
