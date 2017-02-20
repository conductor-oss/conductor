import { join } from 'path';
import { Router } from 'express';
import Bunyan from 'bunyan';
import http from '../core/HttpClient';

var log = Bunyan.createLogger({src : true, name: 'wfe-api'});

const router = new Router();
const baseURL = process.env.WF_SERVER;
const baseURL2 = baseURL + 'workflow/';
const baseURLMeta = baseURL + 'metadata/';


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

    let query = req.query.q;
    const url = baseURL2 + 'search?start=0&size=100&sort=startTime:DESC&freeText=' + freeText.join(' AND ') + '&query=' + query;
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
    result.tasks.forEach(task=>{
      if(task.taskType == 'SUB_WORKFLOW'){
        subs.push({name: task.inputData.subWorkflowName, version: task.inputData.subWorkflowVersion, referenceTaskName: task.referenceTaskName, subWorkflowId: task.inputData.subWorkflowId});
      }
    });
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
module.exports = router;
