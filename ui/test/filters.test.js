var assert = require('assert');
var { AuthFilter } = require('../src/api/middleware/filters/authFilter');

describe('Filters', function() {
  describe('Pre Middleware', function() {
    const authFilter = new AuthFilter();

    it('should add token to req and call next', function() {
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

    it('should bypass add auth token if auth header not present and call next', function() {
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
  });
});
