# UI Authentication / Authorization / Audit

To add authentication, authorization & audit to the UI you will need to:
1. Supply a configuration file through absolute path in the environment variable `AUTH_CONFIG_PATH`
2. Choose an authentication/authorization method

* Audit logs are written to the JSON log under the namespace `Conductor UI Auth` (using Bunyan).
* The UI's left menu will be added with an account label (with the user's display name) and a logout link.
  - Hovering over the user's name will show their email and roles in a tooltip. 
* Persistence is done client-side thorugh a signed cookie (the value is the user's object) 

## Configuration File

The configuration file should look like this: 

| Configuration      | Type   |Required|Default Value|Description
|--------------------|--------|-----|------------|-----------
| `strategy`         | string | Yes |            | Authentication type (see table below for options)
| `strategyOptions`  | Object | Yes |            | The object passed to the constructor of the strategy
| `strategySettings` | Object | Yes |            | strategy specific settings, will probably contain mapping between authentication method to roles
| `cookieName`       | string |     | `"conductor_ui_auth"` | A name for the session cookie
| `cookieSecret`     | string |     | Will be extracted from the strategy's secret| A secret to encrypt the session cookie
| `cookieRoll`       | boolean|     | `true`     | Whether the expiration of the cookie will extend on every call
| `cookie`           | Object |     | `{httpOnly: true, sameSite: true, maxAge: 1200000, signed: true}` | Cookie options for "cookie-parser" middleware (value merged with defaults)
| `indexPath`        | string |     | `"/"`      | The default path to redirect after a successful login (default is /)
| `loginPath`        | string |     | `"/login"` | The path to the login ui (default is /login)
| `logoutPath`       | string |     | `"/logout"`| The path to the logout call (default is /logout)
| `audit`            | boolean|     | `false`    | Enable auditing of login, logout & access (failed and successful)
| `acl`              | `Array<string>`| |        | Access Control List. Path role-based access control, being checked in order using `path-to-regexp` (https://github.com/pillarjs/path-to-regexp). The format is `"<METHOD> <PATH-TO-REGEXP> <ROLES>"` (can have any amount of spaces between for readability). To make a path public put `*` as roles, otherwise put all required roles (separated by commas).


## Authentication Strategies
 
Setup one of the supported [Passport.js](http://www.passportjs.org/) strategies:

| Strategy name | Description                                      | Package Documentation
|---------------|--------------------------------------------------|----------------------
| `local`       | Local users file using PBKDF2 to store passwords | [`passport-local`](https://github.com/jaredhanson/passport-local)
| `ldapauth`    | LDAP / AD authentication                         | [`passport-ldapauth`](http://www.passportjs.org/packages/passport-ldapauth/)

### Local

#### Strategy Settings

* `salt` - Default salt to use if none specified by user (default is `""`)
* `digest` - Hash algorithm (default is `"sha256"`)
* `iterations` - Number of iterations for PBKDF2 (default is `1000`)
* `keylen` - Key length in bytes (default is `32`)
* `users` - User map, key is username, value is details and credentials 
  * Passwords `hash` should be kept as HEX and `salt` as ASCII
  * If user don't have the `salt` field, the default `salt` would be used

#### Example

See example of use:
 ```json
{
  "strategy": "local",
  "strategySettings":{
    "users": {
      "admin": {
        "hash": "05abce9e4b0ac5e07d4059422b3a1d68254ef9c2b92ed917c114a96158fb7c64",
        "salt": "salt",
        "displayName": "Admin",
        "email": "admin@example.com",
        "roles": [ "admin", "viewer" ]
      }
    }
  },
  "audit": true,
  "acl": [
    "POST /(.*)                         admin",
    "PUT /(.*)                          admin",
    "DELETE /(.*)                       admin",
    "GET /api/(.*)                      viewer",
    "GET /(.*)                          *"
  ]
}
```

\* - The password above was created using the node.js command: `crypto.pbkdf2Sync('admin', 'salt', 1000, 32, 'sha256')` (Making the user `admin` with password `admin`)

### LDAP

#### Strategy Settings

* `rolesRequirements` - Mapping between roles and the required AD memberships to have it


#### Example

See example of use:
 ```json
{
  "strategy": "ldapauth",
  "strategyOptions": {
    "server": {
      "url": "ldap://localhost:389",
      "bindDn": "cn='root'",
      "bindCredentials": "secret",
      "searchBase": "o=users,o=example.com",
      "searchFilter": "(uid={{username}})"
    }
  },
  "strategySettings":{
    "rolesRequirements": {
      "admin": [
        "cn=conductor_admins,o=users,o=example.com"
      ],
      "viewer": [
        "cn=conductor_users,o=users,o=example.com"
      ]
    }
  },
  "audit": true,
  "acl": [
    "POST /(.*)                         admin",
    "PUT /(.*)                          admin",
    "DELETE /(.*)                       admin",
    "GET /api/(.*)                      viewer",
    "GET /(.*)                          *"
  ]
}
```
 
## Other plugins

In order to add a passport.js strategy, create an implementation of the IAuthentication interface.
See `/auth/strategies/ldapauth.js` for reference.
