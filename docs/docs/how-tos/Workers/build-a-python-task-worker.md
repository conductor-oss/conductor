---
sidebar_position: 1
---

# Build a Python Task Worker
## Install the python client
```shell 
   virtualenv conductorclient
   source conductorclient/bin/activate
   cd ../conductor/client/python
   python setup.py install
```

## Implement a Task Worker
[ConductorWorker](https://github.com/Netflix/conductor/blob/main/polyglot-clients/python/conductor/ConductorWorker.py#L36) 
class is used to implement task workers.
The following script shows how to bring up two task workers named `book_flight` and `book_car`:

```python
from __future__ import print_function
from conductor.ConductorWorker import ConductorWorker

def book_flight_task(task):
	return {'status': 'COMPLETED', 'output': {'booking_ref': 2341111, 'airline': 'delta'}, 'logs': ['trying delta', 'skipping aa']}

def book_car_task(task):
	return {'status': 'COMPLETED', 'output': {'booking_ref': "84545fdfd", 'agency': 'hertz'}, 'logs': ['trying hertz']}

def main():
	print('Starting Travel Booking workflows')
	cc = ConductorWorker('http://localhost:8080/api', 1, 0.1)
    cc.start('book_flight', book_flight_task, False)
    cc.start('book_car', book_car_task, True)

if __name__ == '__main__':
    main()
```
### `ConductorWorker` parameters
```python
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
```
### `start` method parameters
```pythhon
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
```

See 
[https://github.com/Netflix/conductor/tree/main/polyglot-clients/python](https://github.com/Netflix/conductor/tree/main/polyglot-clients/python)
for the source code.
