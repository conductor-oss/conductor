import PreMiddleware from './pre';
import PostMiddleware from './post';

export default class MiddlewareIndex {
  before(app) {
    new PreMiddleware().init(app);
  }

  after(app) {
    new PostMiddleware().init(app);
  }
}
