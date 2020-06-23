import { Router } from 'express';
import http from '../core/HttpClientServerSide';
import moment from 'moment';
import filter from 'lodash/fp/filter';
import forEach from 'lodash/fp/forEach';
import map from 'lodash/fp/map';
import transform from 'lodash/transform';
import identity from 'lodash/identity';

const router = new Router();

const baseURL = process.env.WF_SERVER;
const baseURL2 = baseURL + 'workflow/';
const baseURLMeta = baseURL + 'metadata/';
const baseURLTask = baseURL + 'tasks/';

router.get('/', async (req, res, next) => {
  try {
    let freeText = [];
    if (req.query.freeText != '') {
      freeText.push(req.query.freeText);
    } else {
      freeText.push('*');
    }

    let h = '-1';
    if (req.query.h != 'undefined' && req.query.h != '') {
      h = req.query.h;
    }
    if (h != '-1') {
      freeText.push('startTime:[now-' + h + 'h TO now]');
    }
    let start = 0;
    if (!isNaN(req.query.start)) {
      start = req.query.start;
    }

    let query = req.query.q;

    const url =
      baseURL2 +
      'search?size=100&sort=startTime:DESC&freeText=' +
      encodeURIComponent(freeText.join(' AND ')) +
      '&start=' +
      start +
      '&query=' +
      encodeURIComponent(query);
    const result = await http.get(url, req.token);
    const hits = result.results;
    res.status(200).send({ result: { hits: hits, totalHits: result.totalHits } });
  } catch (err) {
    next(err);
  }
});

const LOG_DATE_FORMAT = 'MM/DD/YY, HH:mm:ss:SSS';

router.get('/search-by-task/:taskId', async (req, res, next) => {
  try {
    let freeText = [];
    if (req.query.freeText != '') {
      if(req.query.entire === "t"){
        freeText.push(`"${req.params.taskId}"`)
      } else {
        freeText.push(req.params.taskId);
      }
    } else {
      freeText.push('*');
    }

    let h = '-1';
    if (req.query.h !== undefined && req.query.h != 'undefined' && req.query.h != '') {
      h = req.query.h;
    }
    if (h != '-1') {
      freeText.push('startTime:[now-' + h + 'h TO now]');
    }
    let start = 0;
    if (!isNaN(req.query.start)) {
      start = req.query.start;
    }

    let query = req.query.q || '';

    const url =
      baseURL2 + 'search-by-tasks?size=100&sort=startTime:DESC&freeText=' + encodeURIComponent(freeText.join(' AND ')) + '&start=' + start;
    const result = await http.get(url, req.token);

    const hits = result.results;
    res.status(200).send({ result: { hits: hits, totalHits: result.totalHits } });
  } catch (err) {
    next(err);
  }
});

router.get('/id/:workflowId', async (req, res, next) => {

  try {
    const result = await http.get(baseURL2 + req.params.workflowId + '?includeTasks=true', req.token);

    let meta = result.workflowDefinition;
    if (!meta) {
      meta = await http.get(
          baseURLMeta + 'workflow/' + result.workflowType + '?version=' + result.version,
          req.token
      );
    }

    const subs = filter(identity)(
      map(task => {
        if (task.taskType === 'SUB_WORKFLOW') {
          const subWorkflowId = task.subWorkflowId;

          if (subWorkflowId != null) {
            return {
              name: task.inputData.subWorkflowName,
              version: task.inputData.subWorkflowVersion,
              referenceTaskName: task.referenceTaskName,
              subWorkflowId: subWorkflowId
            };
          }
        }
      })(result.tasks || [])
    );

    const logs = map(task => Promise.all([task, http.get(baseURLTask + task.taskId + '/log')]))(result.tasks);

    await Promise.all(logs).then(result => {
      forEach(([task, logs]) => {
        if (logs) {
          task.logs = map(({ createdTime, log }) => `${moment(createdTime).format(LOG_DATE_FORMAT)} : ${log}`)(logs);
        }
      })(result);
    });

    const promises = map(({ name, version, subWorkflowId, referenceTaskName }) =>
      Promise.all([
        referenceTaskName,
        http.get(baseURLMeta + 'workflow/' + name + '?version=' + version),
        http.get(baseURL2 + subWorkflowId + '?includeTasks=true')
      ])
    )(subs);

    const subworkflows = await Promise.all(promises).then(result => {
      return transform(
        result,
        (result, [key, meta, wfe]) => {
          result[key] = { meta, wfe };
        },
        {}
      );
    });

    res.status(200).send({ result, meta, subworkflows: subworkflows });
  } catch (err) {
    next(err);
  }
});

router.put('/bulk/pause', async (req, res, next) => {
  try {
    const result = await http.put(baseURL2 + "bulk/pause", req.body, req.token);
    res.status(200).send(result);
  } catch (err) {
    next(err);
  }
});

router.put('/bulk/resume', async (req, res, next) => {
  try {
    const result = await http.put(baseURL2 + "bulk/resume", req.body, req.token);
    res.status(200).send(result);
  } catch (err) {
    next(err);
  }
})

router.post('/bulk/retry', async (req, res, next) => {
  try {
    const result = await http.post(baseURL2 + "bulk/retry", req.body, req.token);
    res.status(200).send(result);
  } catch (err) {
    next(err);
  }
});

router.post('/bulk/restart', async (req, res, next) => {
  try {
    const result = await http.post(baseURL2 + "bulk/restart", req.body, req.token);
    res.status(200).send(result);
  } catch (err) {
    next(err);
  }
})

router.post('/bulk/restart_with_latest_definition', async (req, res, next) => {
  try {
    const result = await http.post(baseURL2 + "bulk/restart?useLatestDefinition=true", req.body, req.token);
    res.status(200).send(result);
  } catch (err) {
    next(err);
  }
})

router.post('/bulk/restart_with_current_definition', async (req, res, next) => {
  try {
    const result = await http.post(baseURL2 + "bulk/restart", req.body, req.token);
    res.status(200).send(result);
  } catch (err) {
    next(err);
  }
})

router.delete('/bulk/terminate', async (req, res, next) => {
  try {
    const result = await http.delete(baseURL2 + "bulk/terminate", req.body, req.token);
    res.status(200).send(result);
  } catch (err) {
    next(err);
  }
});

router.delete('/terminate/:workflowId', async (req, res, next) => {
  try {
    const result = await http.delete(baseURL2 + req.params.workflowId, {}, req.token);
    res.status(200).send({ result: req.params.workflowId });
  } catch (err) {

    next(err);
  }
});

router.post('/restart/:workflowId', async (req, res, next) => {
  try {
    const result = await http.post(baseURL2 + req.params.workflowId + '/restart?useLatestDefinitions=' + (req.query && req.query.useLatestDefinitions || false), {}, req.token);
    res.status(200).send({ result: req.params.workflowId });
  } catch (err) {
    next(err);
  }
});

router.post('/retry/:workflowId', async (req, res, next) => {
  try {
    const result = await http.post(baseURL2 + req.params.workflowId + '/retry', {}, req.token);
    res.status(200).send({ result: req.params.workflowId });
  } catch (err) {
    next(err);
  }
});

router.post('/pause/:workflowId', async (req, res, next) => {
  try {
    const result = await http.put(baseURL2 + req.params.workflowId + '/pause');
    res.status(200).send({result: req.params.workflowId });
  } catch (err) {
    next(err);
  }
});

router.post('/resume/:workflowId', async (req, res, next) => {
  try {
    const result = await http.put(baseURL2 + req.params.workflowId + '/resume', {}, req.token);
    res.status(200).send({ result: req.params.workflowId });
  } catch (err) {
    next(err);
  }
});

//metadata
router.get('/metadata/workflow/:name/:version', async (req, res, next) => {
  try {
    const result = await http.get(
      baseURLMeta + 'workflow/' + req.params.name + '?version=' + req.params.version,
      req.token
    );
    res.status(200).send({ result });
  } catch (err) {
    next(err);
  }
});

router.get('/metadata/workflow', async (req, res, next) => {
  try {
    const result = await http.get(baseURLMeta + 'workflow', req.token);
    res.status(200).send({ result });
  } catch (err) {
    next(err);
  }
});

router.get('/metadata/taskdef', async (req, res, next) => {
  try {
    const result = await http.get(baseURLMeta + 'taskdefs', req.token);
    res.status(200).send({ result });
  } catch (err) {
    next(err);
  }
});

router.get('/task/log/:taskId', async (req, res, next) => {
  try {
    const logs = await http.get(baseURLTask + req.params.taskId + '/log', req.token);
    res.status(200).send({ logs });
  } catch (err) {
    next(err);
  }
});

router.get('/queue/data', async (req, res, next) => {
  try {
    const sizes = await http.get(baseURLTask + 'queue/all', req.token);
    const polldata = await http.get(baseURLTask + 'queue/polldata/all', req.token);
    polldata.forEach(pd => {
      var qname = pd.queueName;

      if (pd.domain != null) {
        qname = pd.domain + ':' + qname;
      }
      pd.qsize = sizes[qname];
    });
    res.status(200).send({ polldata });
  } catch (err) {
    next(err);
  }
});

module.exports = router;
