class AuthFilter {
  init(app) {
    app.use((req, res, next) => {
      const { headers: { authorization = '' } = {} } = req;

      if (!authorization) {
        return next();
      }

      req.authHeader = token;

      next();
    });
  }
}

module.exports.AuthFilter = AuthFilter;
