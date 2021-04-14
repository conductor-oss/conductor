import threading
import mock
import json
from conductor.conductor import TaskClient
from conductor.ConductorWorker import ConductorWorker


@mock.patch('requests.get')
def test_pollForTask(requests_get):
    task_client = TaskClient('base')
    task_client.pollForTask('fooType', 'barWorker')
    requests_get.assert_called_with('base/tasks/poll/fooType', params={'workerid': 'barWorker'})

    task_client.pollForTask('fooType', 'barWorker', 'bazDomain')
    requests_get.assert_called_with('base/tasks/poll/fooType',
                                    params={'workerid': 'barWorker', 'domain': 'bazDomain'})


@mock.patch('requests.get')
def test_pollForBatch(requests_get):
    task_client = TaskClient('base')
    task_client.pollForBatch('fooType', 20, 100, 'barWorker')
    requests_get.assert_called_with(
        'base/tasks/poll/batch/fooType',
        params={'workerid': 'barWorker', 'count': 20, 'timeout': 100})

    task_client.pollForBatch('fooType', 20, 100, 'barWorker', 'a_domain')
    requests_get.assert_called_with(
        'base/tasks/poll/batch/fooType',
        params={'workerid': 'barWorker', 'count': 20, 'timeout': 100, 'domain': 'a_domain'})


@mock.patch('requests.post')
def test_ackTask(requests_post):
    task_client = TaskClient('base')
    task_client.ackTask('42', 'myWorker')
    requests_post.assert_called_with(
        'base/tasks/42/ack',
        headers={'Content-Type': 'application/json', 'Accept': 'application/json'},
        params={'workerid': 'myWorker'})


@mock.patch('requests.post')
def test_updateTask(post):
    task_client = TaskClient('base')
    task_obj = {'task_id': '123', 'result': 'fail'}
    task_client.updateTask(task_obj)
    post.assert_called_with(
        'base/tasks/',
        data=json.dumps(task_obj),
        headers={'Accept': 'application/json', 'Content-Type': 'application/json'}, params=None)


def test_conductor_worker():
    num_threads = 2
    worker = ConductorWorker('http://server_url', num_threads, 0.1, 'wid')
    num_tasks = num_threads * 3
    id_range = range(123, 123 + num_tasks)
    events = [threading.Event() for _ in id_range]
    return_val = {'status': '', 'output': 'out', 'logs': []}

    tasks = [{'taskId': str(n)} for n in id_range]

    # output is named outputData in the resulting task
    out_tasks = [{'status': '', 'outputData': 'out', 'logs': [], 'taskId': task['taskId']} for task in tasks]

    def exec_function(task):
        assert task in tasks
        tasks.remove(task)
        for ev in events:
            if not ev.is_set():
                ev.set()
                break
        return return_val

    # verify conductor worker call the appropriate method in TaskClient, acks the task, and updates the output
    poll = mock.Mock()
    ack = mock.Mock()
    update = mock.Mock()
    with mock.patch.multiple('conductor.conductor.TaskClient', pollForTask=poll, updateTask=update, ackTask=ack):
        poll.side_effect = tasks + [None] * num_threads
        worker.start('task_a', exec_function, False, 'my_domain')
        for ev in events:
            assert ev.wait(2) is True

        poll.assert_has_calls([mock.call('task_a', 'wid', 'my_domain')] * num_tasks)
        ack.assert_has_calls([mock.call(str(i), 'wid') for i in id_range])
        update.assert_has_calls([mock.call(t) for t in out_tasks])

