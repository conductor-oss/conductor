const { pathToRegexp } = require('path-to-regexp');

/**
 * @param options {ApplyAuthenticationOptions}
 * @returns {function(...[*]=)}
 */
const accessControlMiddleware = (options) => {

  const aclByMethod = options.acl.reduce((a, row) => {
    const parts = row.split(/\s+/); // [method, path-to-regexp, roles]
    if (parts.length !== 3) {
      console.error(`ACL Row ${row} is invalid, check the format`);
    }
    if (!a[parts[0]]) a[parts[0]] = [];
    a[parts[0]].push({
      pathRegex: pathToRegexp(parts[1]),
      roles: parts[2] === '*' ?  [] : parts[2].split(',')
    });
    return a;
  }, {})

  const getRequiredRoles = req => {
    if (!aclByMethod[req.method]) return null;
    const rule = aclByMethod[req.method].find(r => r.pathRegex.test(req.path));
    if (!rule) return null;
    return rule.roles;
  }

  return (req, res, next) => {

    const cookieValue = req.signedCookies[options.cookieName];
    if (cookieValue) {
      req.user = JSON.parse(cookieValue);
      // extend if needed
      if (options.cookieRoll) {
        res.cookie(options.cookieName, cookieValue, options.cookie);
      }
    }

    // skip for login/logout path
    if (req.path === options.loginPath ||
      req.path.startsWith(options.loginPath + "/") ||
      req.path === options.logoutPath ||
      req.path.startsWith(options.logoutPath + "/")
    ) return next();

    const requiredRoles = getRequiredRoles(req);
    if (!requiredRoles || requiredRoles.length === 0) return next();

    // validate
    if (!req.isAuthenticated()) {
      if (req.xhr || req.headers.accept.indexOf('json') > -1) { // XHR
        return res.status(401)
          .set("WWW-Authenticate", `Redirect realm="${options.strategy}"`)
          .set('Location', options.loginPath + "?error=" + encodeURIComponent("Your session has expired. Please login again."))
          .end();
      }
      // browser navigation
      return res.redirect(options.loginPath);
    }
    // check roles
    if (requiredRoles.some(role => !req.user.roles.includes(role))) {
      options.audit && options.audit(`User ${req.user.name} tried to access ${req.method} ${req.originalUrl} without expected roles ${requiredRoles} (User has the roles ${req.user.roles})`);
      if (req.xhr || req.headers.accept.indexOf('json') > -1) { // XHR
        return res.status(403).send({ message: 'Forbidden' });
      }
      // browser navigation
      return res.redirect(options.loginPath + "?error=" + encodeURIComponent("You don't have permission to access the requested page."));
    } else {
      options.audit && options.audit(`User ${req.user.name} accessed ${req.method} ${req.originalUrl}`);
      next();
    }
  };
}

module.exports = accessControlMiddleware;