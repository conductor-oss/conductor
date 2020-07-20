const LdapStrategy = require("passport-ldapauth");

const defaultOptions = {
  server: {
    searchAttributes: ["displayName", "mail", "memberOf"]
  }
};

/** @type IAuthentication */
const LDAPAuthentication = {
  getStrategy(options) {
    const config = {
      ...defaultOptions,
      ...options.strategyOptions,
      server: { ...defaultOptions.server, ...options.strategyOptions.server }
    };
    // allow recursively reading all security groups
    if (!config.server.groupSearchBase) {
      const LDAP_MATCHING_RULE_IN_CHAIN = "1.2.840.113556.1.4.1941";
      config.server.groupSearchBase = config.server.searchBase;
      config.server.groupSearchFilter = `(&(objectclass=group)(member:${LDAP_MATCHING_RULE_IN_CHAIN}:={{dn}}))`;
    }
    return new LdapStrategy(config);
  },

  getDefaultCookieSecret(options) {
    // use authenticated bind credentials as secret
    return options.strategyOptions.server.bindCredentials;
  },

  getCookieValueFromUser(req, user, options) {
    const groups = user._groups.map(g => g.dn);
    return {
      name: req.body.username,
      displayName: user.displayName,
      email: user.mail,
      roles: Object.keys(options.strategySettings.rolesRequirements).filter(role =>
          options.strategySettings.rolesRequirements[role].every(requirement =>
              groups.includes(requirement)
          )
      )
    };
  }
};

module.exports = LDAPAuthentication;
