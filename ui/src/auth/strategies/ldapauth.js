const LdapStrategy = require('passport-ldapauth');

/** @type IAuthentication */
const LDAPAuthentication = {

  getStrategy(options) {
    return new LdapStrategy(options.strategyOptions);
  },

  getDefaultCookieSecret(options) {
    // use authenticated bind credentials as secret
    return options.strategyOptions.server.bindCredentials;
  },

  getCookieValueFromUser(req, user, options) {
    return {
      name: req.body.username,
      displayName: user.displayName,
      email: user.mail,
      roles: Object.keys(options.strategySettings.rolesRequirements).filter(role =>
        options.strategySettings.rolesRequirements[role].every(requirement => user.memberOf.includes(requirement)))
    };
  }
}

module.exports = LDAPAuthentication;