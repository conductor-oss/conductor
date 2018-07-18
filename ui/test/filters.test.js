/* eslint-disable no-undef */
import assert from 'assert';
import AuthFilter from '../src/api/middleware/filters/authFilter';

describe('Filters', () => {
  describe('Pre Middleware', () => {
    const authFilter = new AuthFilter();

    it('should add token to req and call next', () => {
      const middleware = [];

      // Create Mock App
      const app = {
        use: f => {
          middleware.push(f);
        }
      };

      // Add the middleware
      authFilter.init(app);

      // Create the mock request
      const req = {
        headers: {
          authorization: 'Bearer header.body.signature'
        }
      };

      const res = {};

      // Execute the auth middleware
      middleware[0](req, res, () => {
        assert.equal(req.headers.authorization, req.token);
      });
    });

    it('should bypass add auth token if auth header not present and call next', () => {
      const middleware = [];

      // Create Mock App
      const app = {
        use: f => {
          middleware.push(f);
        }
      };

      // Add the middleware
      authFilter.init(app);

      // Create the mock request
      const req = {
        headers: {
          foo: 'bar'
        }
      };

      const res = {};

      // Execute the auth middleware
      middleware[0](req, res, () => {
        assert.equal(req.headers.authorization, null);
        assert.equal(req.token, null);
      });
    });
  });
});
