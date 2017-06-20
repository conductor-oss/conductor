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
import sys
from conductor import conductor
import json


def main():
    if(len(sys.argv) < 3):
        print("Usage - python conductor server_url command parameters...")
        return None

    wfc = conductor.WorkflowClient(sys.argv[1])
    command = sys.argv[2]
    if command == 'start':
        if len(sys.argv) < 5:
            print('python conductor server_url start workflow_name input_json [version] [correlationId]')
            return None
        wfName = sys.argv[3]
        input = json.loads(sys.argv[4])
        workflowId = wfc.startWorkflow(wfName, input, 1, None)
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
