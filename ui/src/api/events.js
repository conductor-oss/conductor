import { Router } from 'express';
import http from '../core/HttpClientServerSide';

const router = new Router();
const baseURL = process.env.WF_SERVER;
const baseURL2 = baseURL + 'event';

router.get('/', async (req, res, next) => {
  try {
    const result = await http.get(baseURL2, req.token);
    res.status(200).send(result);
  } catch (err) {
    next(err);
  }
});

router.get('/executions', async (req, res, next) => {
  try {
    const result = await http.get(baseURL2 + '/' + req.params.event + '?activeOnly=false', req.token);
    res.status(200).send(result);
  } catch (err) {
    next(err);
  }
});

module.exports = router;
