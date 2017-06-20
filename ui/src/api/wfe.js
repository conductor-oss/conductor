import { join } from 'path';
import { Router } from 'express';
import http from '../core/HttpClient';
import moment from 'moment';

const router = new Router();
const baseURL = process.env.WF_SERVER;
const baseURL2 = baseURL + 'workflow/';
const baseURLMeta = baseURL + 'metadata/';
const baseURLTask = baseURL + 'tasks/';


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
    const url = baseURL2 + 'search?size=100&sort=startTime:DESC&freeText=' + freeText.join(' AND ') + '&start=' + start + '&query=' + query;
    const result = await http.get(url);
    const hits = result.results;
    res.status(200).send({result: {hits:hits, totalHits: result.totalHits}});
  } catch (err) {
    next(err);
  }
});

router.get('/id/:workflowId', async (req, res, next) => {
  try {
    let s = new Date().getTime();
    const result = await http.get(baseURL2 + req.params.workflowId + '?includeTasks=true');
    const meta = await http.get(baseURLMeta + 'workflow/' + result.workflowType + '?version=' + result.version);
    const subs = [];
    const subworkflows = {};
    result.tasks.forEach(task => {
      if(task.taskType == 'SUB_WORKFLOW'){
        let subWorkflowId = task.outputData && task.outputData.subWorkflowId;
        if(subWorkflowId == null) {
          subWorkflowId = task.inputData.subWorkflowId;
        }
        if(subWorkflowId != null) {
          subs.push({name: task.inputData.subWorkflowName, version: task.inputData.subWorkflowVersion, referenceTaskName: task.referenceTaskName, subWorkflowId: subWorkflowId});
        }
      }
    });
    for(let t = 0; t < result.tasks.length; t++) {
      let task = result.tasks[t];
      let logs = await http.get(baseURLTask + task.taskId + '/log');
      logs = logs || [];
      let logs2 = [];
      logs.forEach(log => {
        const dtstr = moment(log.createdTime).format('MM/DD/YY, HH:mm:ss:SSS');
        logs2.push(dtstr + ' : ' + log.log);
      });
      task.logs = logs2;
    }
    let submeta = {};
    for(let i = 0; i < subs.length; i++){
      let submeta = await http.get(baseURLMeta + 'workflow/' + subs[i].name + '?version=' + subs[i].version);
      let subes = await http.get(baseURL2 + subs[i].subWorkflowId + '?includeTasks=true');
      let prefix = subs[i].referenceTaskName;
      subworkflows[prefix] = {meta: submeta, wfe: subes};
    }
    let e = new Date().getTime();
    let time = e-s;

    res.status(200).send({result, meta, subworkflows:subworkflows});
  } catch (err) {
    next(err);
  }
});
router.delete('/terminate/:workflowId', async (req, res, next) => {
  try {
    const result = await http.delete(baseURL2 + req.params.workflowId);
    res.status(200).send({result: req.params.workflowId });
  } catch (err) {
    next(err);
  }
});
router.post('/restart/:workflowId', async (req, res, next) => {
  try {
    const result = await http.post(baseURL2 + req.params.workflowId + '/restart');
    res.status(200).send({result: req.params.workflowId });
  } catch (err) {
    next(err);
  }
});

router.post('/retry/:workflowId', async (req, res, next) => {
  try {
    const result = await http.post(baseURL2 + req.params.workflowId + '/retry');
    res.status(200).send({result: req.params.workflowId });
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
    const result = await http.put(baseURL2 + req.params.workflowId + '/resume');
    res.status(200).send({result: req.params.workflowId });
  } catch (err) {
    next(err);
  }
});

//metadata
router.get('/metadata/workflow/:name/:version', async (req, res, next) => {
  try {
    const result = await http.get(baseURLMeta + 'workflow/' + req.params.name + '?version=' + req.params.version);
    res.status(200).send({result});
  } catch (err) {
    next(err);
  }
});
router.get('/metadata/workflow', async (req, res, next) => {
  try {
    const result = await http.get(baseURLMeta + 'workflow');
    res.status(200).send({result});
  } catch (err) {
    next(err);
  }
});
router.get('/metadata/taskdef', async (req, res, next) => {
  try {
    const result = await http.get(baseURLMeta + 'taskdefs');
    res.status(200).send({result});
  } catch (err) {
    next(err);
  }
});
router.get('/task/log/:taskId', async (req, res, next) => {
  try {
    const logs = await http.get(baseURLTask + req.params.taskId + '/log');
    res.status(200).send({logs});
  } catch (err) {
    next(err);
  }
});
router.get('/queue/data', async (req, res, next) => {
  try {
    const sizes = await http.get(baseURLTask + 'queue/all');
    const polldata = await http.get(baseURLTask + 'queue/polldata/all');
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
