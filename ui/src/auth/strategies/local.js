const crypto = require('crypto');
const LocalStrategy = require('passport-local');

const defaultSettings = {
  salt: '',
  digest: 'sha256',
  iterations: 1000,
  keylen: 32
};

const validatePBKDF2 = (settings, password, hash, salt, callback) => {
  try {
    const expected = Buffer.from(hash, 'hex');

    crypto.pbkdf2(password, salt, settings.iterations, settings.keylen, settings.digest, (err, actual) => {
      console.log(expected, actual)
      callback(err, !err && expected.compare(actual) === 0);
    });
  }
  catch (e) {
    callback(e, false);
  }
};

/** @type IAuthentication */
const LocalAuthentication = {

  getStrategy(options) {
    const settings = Object.assign({}, defaultSettings, options.strategySettings);
    return new LocalStrategy(
      Object.assign({ session: false }, options.strategyOptions),
      (username, password, done) => {
        try {
          const user = options.strategySettings.users[username];
          if (!user) return done(null, false, { message: 'Wrong username/password' });
          validatePBKDF2(settings, password, user.hash, user.salt || settings.salt, (err, success) =>
            done(err, success && {
              name: username,
              displayName: user.displayName,
              email: user.email,
              roles: user.roles
            }, { message: err ? err.toString() : 'Wrong username/password' })
          );
        } catch (e) {
          done(e, false, e);
        }
      }
    );
  },

  getDefaultCookieSecret(options) {
    // no special secret in this strategy, please use options.cookieSecret instead
    return 'LOCAL_SECRET';
  },

  getCookieValueFromUser(req, user, options) {
    return user;
  }
}

module.exports = LocalAuthentication;