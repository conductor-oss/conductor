import AuthFilter from './filters/authFilter';

export default class PreMiddleware {
  init(app) {
    new AuthFilter().init(app);
  }
}
