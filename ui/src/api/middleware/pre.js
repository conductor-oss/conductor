const { AuthFilter } = require('./filters/authFilter');

class PreMiddleware {
  init(app) {
    new AuthFilter().init(app);
  }
}

module.exports.PreMiddleware = PreMiddleware;
