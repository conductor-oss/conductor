import { join } from 'path';
import { Router } from 'express';
import Bunyan from 'bunyan';
import http from '../core/HttpClient';

const router = new Router();
const wfServer = process.env.WF_SERVER;

router.get('/', async (req, res, next) => {

  try {
    const result = {
      server: wfServer,
      version: '1.0',
      env: process.env
    };

    res.status(200).send({sys: result});
  } catch (err) {
    next(err);
  }
});
module.exports = router;
