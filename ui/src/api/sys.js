import { Router } from 'express';
import http from '../core/HttpClientServerSide';

const router = new Router();
const wfServer = process.env.WF_SERVER;

router.get('/', async (req, res, next) => {
  try {
    const result = {
      server: wfServer,
      env: process.env
    };
    const config = await http.get(wfServer + 'admin/config', req.token);
    result.version = config.version;
    result.buildDate = config.buildDate;
    res.status(200).send({ sys: result });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
