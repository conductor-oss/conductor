import 'babel-polyfill';
import express from 'express';
import Bunyan from 'bunyan';
import MiddlewareIndex from './api/middleware';
import bodyParser from "body-parser";

const log = Bunyan.createLogger({ src: true, name: 'Conductor UI' });

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

  preMiddlewareConfig = (app, middlewareIndex) => {
    middlewareIndex.before(app);
  };

  routesConfig = app => {
    log.info(`Serving static ${process.cwd()}`);
    app.use(express.static('public'));

    app.use('/api/wfe', bodyParser.json(), wfeAPI);
    app.use('/api/sys', sysAPI);
    app.use('/api/events', eventsAPI);
  };

  postMiddlewareConfig = (app, middlewareIndex) => {
    middlewareIndex.after(app);
  };

  startServer = app => {
    const server = app.listen(process.env.NODE_PORT || 5000, process.env.NODE_HOST || "0.0.0.0", function () {
      const { address, port } = server.address();
      log.info('Workflow UI listening at http://%s:%s', address, port);
      if (process.send) {
        process.send('online');
      }
    });
  };
}

const main = new Main();

main.init();
