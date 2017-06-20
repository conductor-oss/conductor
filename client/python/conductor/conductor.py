#
#  Copyright 2017 Netflix, Inc.
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
from __future__ import print_function
import requests
import json
import sys
import time
import socket


hostname = socket.gethostname()


class BaseClient:
    printUrl = False
    headers = {'Content-Type': 'application/json', 'Accept': 'application/json'}
    def __init__(self, baseURL, baseResource):
        self.baseURL = baseURL
        self.baseResource = baseResource

    def get(self, resPath, queryParams=None):
        theUrl = "{}/{}".format(self.baseURL, resPath)
        resp = requests.get(theUrl, params=queryParams)
        self.__checkForSuccess(resp)
        if(resp.content == b''):
            return None
        else:
            return resp.json()

    def post(self, resPath, queryParams, body, headers=None):
        theUrl = "{}/{}".format(self.baseURL, resPath)
        theHeader = self.headers
        if headers is not None:
            theHeader = dict(self.headers.items() + headers.items())
        if body is not None:
            jsonBody = json.dumps(body, ensure_ascii=False)
            resp = requests.post(theUrl, params=queryParams, data=jsonBody, headers=theHeader)
        else:
            resp = requests.post(theUrl, params=queryParams, headers=theHeader)

        self.__checkForSuccess(resp)
        return self.__return(resp, theHeader)

    def put(self, resPath, queryParams=None, body=None, headers=None):
        theUrl = "{}/{}".format(self.baseURL, resPath)
        theHeader = self.headers
        if headers is not None:
            theHeader = dict(self.headers.items() + headers.items())

        if body is not None:
            jsonBody = json.dumps(body, ensure_ascii=False)
            resp = requests.put(theUrl, params=queryParams, data=jsonBody, headers=theHeader)
        else:
            resp = requests.put(theUrl, params=queryParams, headers=theHeader)

        self.__print(resp)
        self.__checkForSuccess(resp)


    def delete(self, resPath, queryParams):
        theUrl = "{}/{}".format(self.baseURL, resPath)
        resp = requests.delete(theUrl, params=queryParams)
        self.__print(resp)
        self.__checkForSuccess(resp)

    def makeUrl(self, urlformat, *argv):
        url = self.baseResource + '/' + urlformat.format(*argv)
        return url

    def __print(self, resp):
        if self.printUrl:
            print(resp.url)

    def __return(self, resp, header):
        retval = ''
        if len(resp.text) > 0:
            if header['Accept'] == 'text/plain':
                retval = resp.text
            elif header['Accept'] == 'application/json':
                retval = resp.json()
            else:
                retval = resp.text
        return retval

    def __checkForSuccess(self, resp):
        try:
            resp.raise_for_status()
        except:
            print("ERROR: " + resp.text)
            raise

class MetadataClient(BaseClient):
    BASE_RESOURCE = 'metadata'
    def __init__(self, baseURL):
        BaseClient.__init__(self, baseURL, self.BASE_RESOURCE)

    def getWorkflowDef(self, wfname, version=1):
        url = self.makeUrl('workflow/{}', wfname)
        params = {}
        params['version']=version
        return self.get(url, params)

    def createWorkflowDef(self, wfdObj):
        url = self.makeUrl('workflow')
        return self.post(url, None, wfdObj)

    def updateWorkflowDefs(self, listOfWfdObj):
        url = self.makeUrl('workflow')
        self.put(url, None, listOfWfdObj)

    def getAllWorkflowDefs(self):
        url = self.makeUrl('workflow')
        return self.get(url)

    def getTaskDef(self, tdName):
        url = self.makeUrl('taskdefs/{}', tdName)
        return self.get(url)

    def registerTaskDefs(self, listOfTaskDefObj):
        url = self.makeUrl('taskdefs')
        return self.post(url, None, listOfTaskDefObj)

    def registerTaskDef(self, taskDefObj):
        url = self.makeUrl('taskdefs')
        self.put(url, None, taskDefObj)

    def unRegisterTaskDef(self, tdName):
        url = self.makeUrl('taskdefs/{}', tdName)
        self.delete(url)

    def getAllTaskDefs(self):
        url = self.makeUrl('taskdefs')
        return self.get(url)

class TaskClient(BaseClient):
    BASE_RESOURCE = 'tasks'
    def __init__(self, baseURL):
        BaseClient.__init__(self, baseURL, self.BASE_RESOURCE)

    def getTask(self, taskId):
        url = self.makeUrl('{}', taskId)
        return self.get(url)

    def updateTask(self, taskObj):
        url = self.makeUrl('')
        self.post(url, None, taskObj)

    def pollForTask(self, taskType, workerid):
        url = self.makeUrl('poll/{}', taskType)
        params = {}
        params['workerid']=workerid
        try:
            return self.get(url, params)
        except Exception as err:
            print('Error while polling ' + str(err))
            return None

    def ackTask(self, taskId, workerid):
        url = self.makeUrl('{}/ack', taskId)
        params = {}
        params['workerid']=workerid
        headers = {'Accept': 'text/plain'}
        value = self.post(url, params, None, headers)
        return value == 'true'

    def getTasksInQueue(self, taskName):
        url = self.makeUrl('queue/{}', taskName)
        return self.get(url)

    def removeTaskFromQueue(self, taskId):
        url = self.makeUrl('queue/{}', taskId)
        self.delete(url)

    def getTaskQueueSizes(self, listOfTaskName):
        url = self.makeUrl('queue/sizes')
        return self.post(url, None, listOfTaskName)


class WorkflowClient(BaseClient):
    BASE_RESOURCE = 'workflow'
    def __init__(self, baseURL):
        BaseClient.__init__(self, baseURL, self.BASE_RESOURCE)

    def getWorkflow(self, wfId, includeTasks=True):
        url = self.makeUrl('{}', wfId)
        params = {}
        params['includeTasks']=includeTasks
        return self.get(url, params)

    def getRunningWorkflows(self, wfName, version=1, startTime=None, endTime=None):
        url = self.makeUrl('running/{}', wfName)
        params = {}
        params['version']=version
        params['startTime']=startTime
        params['endTime']=endTime
        return self.get(url, params)

    def startWorkflow(self, wfName, inputjson, version=1, correlationId=None):
        url = self.makeUrl('{}', wfName)
        params = {}
        params['version']=version
        params['correlationId']=correlationId
        headers = {'Accept': 'text/plain'}
        return self.post(url, params, inputjson, headers)

    def terminateWorkflow(self, wfId, reason=None):
        url = self.makeUrl('{}', wfId)
        params = {}
        params['reason']=reason
        self.delete(url, params)

    def pauseWorkflow(self, wfId):
        url = self.makeUrl('{}/pause', wfId)
        self.put(url)

    def resumeWorkflow(self, wfId):
        url = self.makeUrl('{}/resume', wfId)
        self.put(url)

    def skipTaskFromWorkflow(self, wfId, taskRefName, skipTaskRequest):
        url = self.makeUrl('{}/skiptask/{}', wfId, taskRefName)
        self.post(url, None, skipTaskRequest)

    def rerunWorkflow(self, wfId, taskRefName, rerunWorkflowRequest):
        url = self.makeUrl('{}/rerun', wfId)
        return self.post(url, None, rerunWorkflowRequest)

    def restartWorkflow(self, wfId, taskRefName, fromTaskRef):
        url = self.makeUrl('{}/restart', wfId)
        params = {}
        params['from']=fromTaskRef
        self.post(url, params, None)


class WFClientMgr:
    def __init__(self, server_url='http://localhost:8080/api/'):
        self.workflowClient = WorkflowClient(server_url)
        self.taskClient = TaskClient(server_url)
        self.metadataClient = MetadataClient(server_url)

def main():
    if len(sys.argv) < 3 :
        print("Usage - python conductor server_url command parameters...")
        return None

    server_url = sys.argv[1]
    command = sys.argv[2]
    wfcMgr = WFClientMgr(server_url)
    wfc = wfcMgr.workflowClient
    if command == 'start':
        if len(sys.argv) < 7:
            print('python conductor server_url start workflow_name input_json [version] [correlationId]')
            return None;
        wfName = sys.argv[3]
        version = sys.argv[4]
        input = json.loads(sys.argv[5])
        correlationId = sys.argv[6]
        workflowId = wfc.startWorkflow(wfName, input, 1, correlationId)
        print(workflowId)
        return workflowId
    elif command == 'get':
        if len(sys.argv) < 4:
            print('python conductor server_url get workflow_id')
            return None
        wfId = sys.argv[3]
        wfjson = wfc.getWorkflow(wfId)
        print(json.dumps(wfjson, indent=True, separators=(',', ': ')))
        return wfjson
    elif command == 'terminate':
        if len(sys.argv) < 4:
            print('python conductor server_url terminate workflow_id')
            return None
        wfId = sys.argv[3]
        wfjson = wfc.terminateWorkflow(wfId)
        print('OK')
        return wfId
if __name__ == '__main__':
    main()
