import { join } from 'path';
import { Router } from 'express';
import Bunyan from 'bunyan';
import http from '../core/HttpClient';

var log = Bunyan.createLogger({src : true, name: 'events-api'});

const router = new Router();
const baseURL = process.env.WF_SERVER;
const baseURL2 = baseURL + 'event';


router.get('/', async (req, res, next) => {

  try {

    const result = await http.get(baseURL2);
    res.status(200).send(result);

  } catch (err) {
    next(err);
  }
});



router.get('/executions', async (req, res, next) => {

  try {
    let event = req.params.event;
    const result = await http.get(baseURL2 + '/' + req.params.event + '?activeOnly=false');
    res.status(200).send(result);

  } catch (err) {
    next(err);
  }
});


module.exports = router;
