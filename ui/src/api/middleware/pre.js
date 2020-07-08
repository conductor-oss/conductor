import fs from 'fs';
import AuthFilter from './filters/authFilter';
import applyAuthentication from '../../auth/applyAuthentication';
import Bunyan from 'bunyan';

export default class PreMiddleware {
  init(app) {
    new AuthFilter().init(app);

    if (process.env.AUTH_CONFIG_PATH) {
      const authLog = Bunyan.createLogger({src: true, name: 'Conductor UI Auth'});
      if (!fs.existsSync(process.env.AUTH_CONFIG_PATH)) {
        authLog.error(`Could not find file in path ${process.env.AUTH_CONFIG_PATH}`);
        return;
      }
      try {
        const authConfigContents = fs.readFileSync(process.env.AUTH_CONFIG_PATH, 'utf8');
        const authConfig = JSON.parse(authConfigContents);
        if (authConfig.audit === true) {
          authConfig.audit = msg => authLog.info(msg);
        }
        applyAuthentication(app, authConfig);
        authLog.info('Authentication module is running');
      }
      catch (e) {
        authLog.error(e);
      }
    }

  }
}
