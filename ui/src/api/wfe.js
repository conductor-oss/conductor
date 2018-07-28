import {Router} from 'express';
import http from '../core/HttpClient';
import moment from 'moment';
import filter from "lodash/fp/filter";
import forEach from "lodash/fp/forEach";
import map from "lodash/fp/map";
import transform from "lodash/transform";
import identity from "lodash/identity";

const router = new Router();
const baseUrl = process.env.WF_SERVER;
const baseWorkflowUrl = baseUrl + 'workflow/';
const baseWorkflowUrlByTasks = baseWorkflowUrl + 'search-by-task';
const baseMetadataUrl = baseUrl + 'metadata/';
const baseTaskUrl = baseUrl + 'tasks/';


router.get('/', async (req, res, next) => {
  try {

    let freeText = [];
    if(req.query.freeText != '') {
      freeText.push(req.query.freeText);
    }else {
      freeText.push('*');
    }

    let h = '-1';
    if(req.query.h != 'undefined' && req.query.h != ''){
      h = req.query.h;
    }
    if(h != '-1'){
      freeText.push('startTime:[now-' + h + 'h TO now]');
    }
    let start = 0;
    if(!isNaN(req.query.start)){
      start = req.query.start;
    }

    let query = req.query.q;
    const url = baseWorkflowUrl + 'search?size=100&sort=startTime:DESC&freeText=' + freeText.join(' AND ') + '&start=' + start + '&query=' + query;
    const result = await http.get(url);
    const hits = result.results;
    res.status(200).send({result: {hits:hits, totalHits: result.totalHits}});
  } catch (err) {
    next(err);
  }
});

const LOG_DATE_FORMAT = 'MM/DD/YY, HH:mm:ss:SSS';

router.get('/search-by-task/:taskId', async (req, res, next) => {
  try {

    let freeText = [];
    if(req.query.freeText != '') {
      freeText.push(req.params.taskId);
    }else {
      freeText.push('*');
    }

    let h = '-1';
    if(req.query.h !== undefined && req.query.h != 'undefined' && req.query.h != ''){
      h = req.query.h;
    }
    if(h != '-1'){
      freeText.push('startTime:[now-' + h + 'h TO now]');
    }
    let start = 0;
    if(!isNaN(req.query.start)){
      start = req.query.start;
    }

    let query = req.query.q || "";
    const url = baseWorkflowUrl + 'search-by-tasks?size=100&sort=startTime:DESC&freeText=' + freeText.join(' AND ') + '&start=' + start;
    const result = await http.get(url);
    const hits = result.results;
    res.status(200).send({result: {hits:hits, totalHits: result.totalHits}});
  } catch (err) {
    next(err);
  }
})

router.get('/id/:workflowId', async (req, res, next) => {
    try {
        const result = await http.get(baseWorkflowUrl + req.params.workflowId + '?includeTasks=true');

        let meta = result.workflowDefinition;
        if(!meta){
          meta = await http.get(baseMetadataUrl + 'workflow/' + result.workflowType + '?version=' + result.version);
        }

        const subs = filter(identity)(map(task => {
            if (task.taskType === 'SUB_WORKFLOW') {
                const subWorkflowId = task.inputData && task.inputData.subWorkflowId;

                if (subWorkflowId != null) {
                    return {
                        name: task.inputData.subWorkflowName,
                        version: task.inputData.subWorkflowVersion,
                        referenceTaskName: task.referenceTaskName,
                        subWorkflowId: subWorkflowId
                    };
                }
            }
        })(result.tasks));

        result.tasks.forEach(task => {
            if (task.taskType === 'SUB_WORKFLOW') {
                const subWorkflowId = task.inputData && task.inputData.subWorkflowId;

                if (subWorkflowId != null) {
                    subs.push({
                        name: task.inputData.subWorkflowName,
                        version: task.inputData.subWorkflowVersion,
                        referenceTaskName: task.referenceTaskName,
                        subWorkflowId: subWorkflowId
                    });
                }
            }
        });

        const logs = map(task => Promise.all([task, http.get(baseTaskUrl + task.taskId + '/log')]))(result.tasks);

        await Promise.all(logs).then(result => {
            forEach(([task, logs]) => {
                if (logs) {
                    task.logs = map(({createdTime, log}) => `${moment(createdTime).format(LOG_DATE_FORMAT)} : ${log}`)(logs)
                }
            })(result);
        });

        const promises = map(({name, version, subWorkflowId, referenceTaskName}) => Promise.all([
            referenceTaskName,
            http.get(baseMetadataUrl + 'workflow/' + name + '?version=' + version),
            http.get(baseWorkflowUrl + subWorkflowId + '?includeTasks=true')
        ]))(subs);

        const subworkflows = await Promise.all(promises).then(result => {
            return transform(result, (result, [key, meta, wfe]) => {
                result[key] = {meta, wfe};
            }, {});
        });

        res.status(200).send({result, meta, subworkflows: subworkflows});
    } catch (err) {
        next(err);
    }
});
router.delete('/terminate/:workflowId', async (req, res, next) => {
  try {
    const result = await http.delete(baseWorkflowUrl + req.params.workflowId);
    res.status(200).send({result: req.params.workflowId });
  } catch (err) {
    next(err);
  }
});
router.post('/restart/:workflowId', async (req, res, next) => {
  try {
    const result = await http.post(baseWorkflowUrl + req.params.workflowId + '/restart');
    res.status(200).send({result: req.params.workflowId });
  } catch (err) {
    next(err);
  }
});

router.post('/retry/:workflowId', async (req, res, next) => {
  try {
    const result = await http.post(baseWorkflowUrl + req.params.workflowId + '/retry');
    res.status(200).send({result: req.params.workflowId });
  } catch (err) {
    next(err);
  }
});

router.post('/pause/:workflowId', async (req, res, next) => {
  try {
    const result = await http.put(baseWorkflowUrl + req.params.workflowId + '/pause');
    res.status(200).send({result: req.params.workflowId });
  } catch (err) {
    next(err);
  }
});

router.post('/resume/:workflowId', async (req, res, next) => {
  try {
    const result = await http.put(baseWorkflowUrl + req.params.workflowId + '/resume');
    res.status(200).send({result: req.params.workflowId });
  } catch (err) {
    next(err);
  }
});

//metadata
router.get('/metadata/workflow/:name/:version', async (req, res, next) => {
  try {
    const result = await http.get(baseMetadataUrl + 'workflow/' + req.params.name + '?version=' + req.params.version);
    res.status(200).send({result});
  } catch (err) {
    next(err);
  }
});
router.get('/metadata/workflow', async (req, res, next) => {
  try {
    const result = await http.get(baseMetadataUrl + 'workflow');
    res.status(200).send({result});
  } catch (err) {
    next(err);
  }
});
router.get('/metadata/taskdef', async (req, res, next) => {
  try {
    const result = await http.get(baseMetadataUrl + 'taskdefs');
    res.status(200).send({result});
  } catch (err) {
    next(err);
  }
});
router.get('/task/log/:taskId', async (req, res, next) => {
  try {
    const logs = await http.get(baseTaskUrl + req.params.taskId + '/log');
    res.status(200).send({logs});
  } catch (err) {
    next(err);
  }
});
router.get('/queue/data', async (req, res, next) => {
  try {
    const sizes = await http.get(baseTaskUrl + 'queue/all');
    const polldata = await http.get(baseTaskUrl + 'queue/polldata/all');
    polldata.forEach(pd=>{
      var qname = pd.queueName;

      if(pd.domain != null){
        qname = pd.domain + ":" + qname;
      }
      pd.qsize = sizes[qname];
    });
    res.status(200).send({polldata});
  } catch (err) {
    next(err);
  }
});
module.exports = router;
