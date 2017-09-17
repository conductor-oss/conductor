import 'babel-polyfill';
import express from 'express';
import React from 'react';
import ReactDOM from 'react-dom/server';
import Bunyan from 'bunyan';

let log = Bunyan.createLogger({src : true, name: 'Conductor UI'});

const wfeAPI = require('./api/wfe');
const sysAPI = require('./api/sys');
const eventsAPI = require('./api/events');

let app = express();
log.info('Serving static ' + process.cwd());
app.use(express.static('public'));

app.use('/api/wfe', wfeAPI);
app.use('/api/sys', sysAPI);
app.use('/api/events', eventsAPI);

let server = app.listen(process.env.NODE_PORT || 5000, function () {
  var host = server.address().address;
  var port = server.address().port;
  log.info('Workflow UI listening at http://%s:%s', host, port);
  if (process.send) {
    process.send('online');
  }
});
