import 'babel-polyfill';
import express from 'express';
import Bunyan from 'bunyan';
import MiddlewareIndex from './api/middleware';

let log = Bunyan.createLogger({ src: true, name: 'Conductor UI' });

const wfeAPI = require('./api/wfe');
const sysAPI = require('./api/sys');
const eventsAPI = require('./api/events');

class Main {
  init() {
    const app = express();
    const middlewareIndex = new MiddlewareIndex();

    this.preMiddlewareConfig(app, middlewareIndex);
    this.routesConfig(app);
    this.postMiddlewareConfig(app, middlewareIndex);
    this.startServer(app);
  }

  preMiddlewareConfig(app, middlewareIndex) {
    middlewareIndex.before(app);
  }

  routesConfig(app) {
    log.info('Serving static ' + process.cwd());
    app.use(express.static('public'));

    app.use('/api/wfe', wfeAPI);
    app.use('/api/sys', sysAPI);
    app.use('/api/events', eventsAPI);
  }

  postMiddlewareConfig(app, middlewareIndex) {
    middlewareIndex.after(app);
  }

  startServer(app) {
    let server = app.listen(process.env.NODE_PORT || 5000, function() {
      var host = server.address().address;
      var port = server.address().port;
      log.info('Workflow UI listening at http://%s:%s', host, port);
      if (process.send) {
        process.send('online');
      }
    });
  }
}

const main = new Main();

main.init();
