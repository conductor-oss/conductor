const { PreMiddleware } = require('./pre');
const { PostMiddleware } = require('./post');

class MiddlewareIndex {
  before(app) {
    new PreMiddleware().init(app);
  }

  after(app) {
    new PostMiddleware().init(app);
  }
}

module.exports.MiddlewareIndex = MiddlewareIndex;
