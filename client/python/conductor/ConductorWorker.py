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
from __future__ import print_function, absolute_import
import sys
import time
from conductor.conductor import WFClientMgr
from threading import Thread
import socket
from enum import Enum

hostname = socket.gethostname()

class TaskStatus(Enum):
    IN_PROGRESS = 'IN_PROGRESS'
    FAILED = 'FAILED'
    FAILED_WITH_TERMINAL_ERROR = 'FAILED_WITH_TERMINAL_ERROR'
    COMPLETED = 'COMPLETED'

    def __str__(self):
        return str(self.value)



class ConductorWorker:
    """
    Main class for implementing Conductor Workers

    A conductor worker is a separate system that executes the various
    tasks that the conductor server queues up for execution. The worker
    can run on the same instance as the server or on a remote instance.

    The worker generally provides a wrapper around some function that
    performs the actual execution of the task. The function that is
    being executed must return a `dict` with the `status`, `output` and
    `log` keys. If these keys are not present, the worker will raise an
    Exception after completion of the task.

    The start method is used to begin continous polling and execution
    of the tasks that the conductor server makes available. The same
    script can run multiple workers using the wait argument. For more
    details, view the start method
    """
    def __init__(self, server_url, thread_count, polling_interval, worker_id=None):
        """
        Parameters
        ----------
        server_url: str
            The url to the server hosting the conductor api.
            Ex: 'http://localhost:8080/api'
        thread_count: int
            The number of threads that will be polling for and
            executing tasks in case of using the start method.
        polling_interval: float
            The number of seconds that each worker thread will wait
            between polls to the conductor server.
        worker_id: str, optional
            The worker_id of the worker that is going to execute the
            task. For further details, refer to the documentation
            By default, it is set to hostname of the machine
        """
        wfcMgr = WFClientMgr(server_url)
        self.workflowClient = wfcMgr.workflowClient
        self.taskClient = wfcMgr.taskClient
        self.thread_count = thread_count
        self.polling_interval = polling_interval
        self.worker_id = worker_id or hostname

    @staticmethod
    def task_result(status: TaskStatus, output=None, logs=None, reasonForIncompletion=None):
        """
        Get task result
        Parameters
        ----------
        status: TaskStatus
            The status of the task 
            Ex: TaskStatus.COMPLETED
        output: dict
            results of task processing 
        logs: list
            log list
        reasonForIncompletion: str, optional
            the reason for not completing the task if any
        """
        if logs is None:
            logs = []
        if output is None:
            output = {}
        ret = {
            'status': status.__str__(),
            'output': output,
            'logs': logs
        }
        if reasonForIncompletion:
            ret['reasonForIncompletion'] = reasonForIncompletion
        return ret

    def execute(self, task, exec_function):
        try:
            resp = exec_function(task)
            if type(resp) is not dict or not all(key in resp for key in ('status', 'output', 'logs')):
                raise Exception('Task execution function MUST return a response as a dict with status, output and logs fields')
            task['status'] = resp['status']
            task['outputData'] = resp['output']
            task['logs'] = resp['logs']
            if 'reasonForIncompletion' in resp:
                task['reasonForIncompletion'] = resp['reasonForIncompletion']
            self.taskClient.updateTask(task)
        except Exception as err:
            print(f'Error executing task: {exec_function.__name__} with error: {str(err)}')
            task['status'] = 'FAILED'
            self.taskClient.updateTask(task)

    def poll_and_execute(self, taskType, exec_function, domain=None):
        while True:
            time.sleep(float(self.polling_interval))
            polled = self.taskClient.pollForTask(taskType, self.worker_id, domain)
            if polled is not None:
                self.taskClient.ackTask(polled['taskId'], self.worker_id)
                self.execute(polled, exec_function)

    def start(self, taskType, exec_function, wait, domain=None):
        """
        start begins the continuous polling of the conductor server

        Parameters
        ----------
        taskType: str
            The name of the task that the worker is looking to execute
        exec_function: function
            The function that the worker will execute. The function
            must return a dict with the `status`, `output` and `logs`
            keys present. If this is not present, an Exception will be
            raised
        wait: bool
            Whether the worker will block execution of further code.
            Since the workers are being run in daemon threads, when the
            program completes execution, all the threads are destroyed.
            Setting wait to True prevents the program from ending.
            If multiple workers are being called from the same program,
            all but the last start call but have wait set to False.
            The last start call must always set wait to True. If a
            single worker is being called, set wait to True.
        domain: str, optional
            The domain of the task under which the worker will run. For
            further details refer to the conductor server documentation
            By default, it is set to None
        """
        print('Polling for task %s at a %f ms interval with %d threads for task execution, with worker id as %s' % (taskType, self.polling_interval * 1000, self.thread_count, self.worker_id))
        for x in range(0, int(self.thread_count)):
            thread = Thread(target=self.poll_and_execute, args=(taskType, exec_function, domain,))
            thread.daemon = True
            thread.start()
        if wait:
            while 1:
                time.sleep(1)


def exc(taskType, inputData, startTime, retryCount, status, callbackAfterSeconds, pollCount):
    print('Executing the function')
    return {'status': 'COMPLETED', 'output': {}, 'logs': []}


def main():
    cc = ConductorWorker('http://localhost:8080/api', 5, 0.1)
    cc.start(sys.argv[1], exc, False)
    cc.start(sys.argv[2], exc, True)


if __name__ == '__main__':
    main()
