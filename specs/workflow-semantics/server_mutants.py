#!/usr/bin/env python3
"""Shows that the step-by-step server check catches a server that breaks the spec.

Each mutant breaks one rule in the server's code. For each, this script applies it, builds and
starts the server with tracing on, runs traced server_check.py tests, and reverts it. A mutant is
caught when at least one test does not match the spec. At the end the server is built again
without mutants. It needs the conductor repository, Java to build it, and a free port.

    TLA2TOOLS=/path/to/tla2tools.jar python3 server_mutants.py --start-script run.sh \\
        --trace-dir /tmp/trace --server http://localhost:8099/api [--only name-regex]

--start-script starts the server in the foreground with -Dconductor.trace.dir set to --trace-dir;
the script starts it in the background, and stops whatever listens on the server's port first.
"""
import argparse, os, re, signal, subprocess, sys, time, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
CORE = os.path.join(REPO, 'core', 'src', 'main', 'java', 'com', 'netflix', 'conductor', 'core', 'execution')

# (name, file under core/execution, text, replacement, server_check arguments)
MUTANTS = [
    ('a fork schedules its JOIN before its branches', 'mapper/ForkJoinTaskMapper.java',
     '        tasksToBeScheduled.addAll(joinTask);\n        return tasksToBeScheduled;',
     '        tasksToBeScheduled.addAll(1, joinTask);\n        return tasksToBeScheduled;',
     ['--tests', '30', '--size', '3', '--ops', '0', '--seed', '101']),
    ('a switch schedules its case before itself', 'mapper/SwitchTaskMapper.java',
     '            tasksToBeScheduled.addAll(caseTasks);',
     '            tasksToBeScheduled.addAll(0, caseTasks);',
     ['--tests', '30', '--size', '3', '--ops', '0', '--seed', '102']),
    ('a failure leaves running tasks running', 'WorkflowExecutorOps.java',
     '    List<String> cancelNonTerminalTasks(WorkflowModel workflow, boolean raiseFinalized) {\n',
     '    List<String> cancelNonTerminalTasks(WorkflowModel workflow, boolean raiseFinalized) {\n'
     '        if (workflow != null) return new ArrayList<>();   // MUTANT\n',
     ['--tests', '30', '--size', '3', '--ops', '0', '--seed', '103']),
    ('an end starts two failure workflows', 'WorkflowExecutorOps.java',
     '                    startWorkflow(startWorkflowInput);\n',
     '                    startWorkflow(startWorkflowInput);\n'
     '                    startWorkflowInput.setWorkflowId(idGenerator.generate());   // MUTANT\n'
     '                    startWorkflow(startWorkflowInput);\n',
     ['--tests', '30', '--size', '3', '--ops', '0', '--seed', '104']),
    ('a retry after a TERMINATE schedules what was ready', 'DeciderService.java',
     '        if (hasSuccessfulTerminateTask) {\n            pendingTasks.clear();\n        }',
     '        if (hasSuccessfulTerminateTask) {\n            // MUTANT: pending work is still scheduled\n        }',
     ['--tests', '60', '--size', '4', '--ops', '1', '--seed', '105']),
]


def up(server):
    try:
        with urllib.request.urlopen(server + '/metadata/workflow', timeout=2) as r:
            return r.status == 200
    except Exception:  # noqa
        return False


def stop(port):
    pids = subprocess.run(['lsof', '-ti', f'tcp:{port}', '-sTCP:LISTEN'], capture_output=True, text=True).stdout.split()
    for p in pids:
        os.kill(int(p), signal.SIGTERM)
    for _ in range(60):
        if not subprocess.run(['lsof', '-ti', f'tcp:{port}', '-sTCP:LISTEN'], capture_output=True, text=True).stdout.strip():
            return
        time.sleep(1)
    for p in pids:
        os.kill(int(p), signal.SIGKILL)
    time.sleep(2)


def build(java_home):
    env = dict(os.environ, JAVA_HOME=java_home)
    r = subprocess.run(['./gradlew', '-q', ':conductor-server:bootJar', '-x', 'test'], cwd=REPO, env=env,
                       capture_output=True, text=True)
    if r.returncode:
        sys.exit('build failed:\n' + r.stdout[-2000:] + r.stderr[-2000:])


def restart(args):
    port = re.search(r':(\d+)', args.server.split('//', 1)[1]).group(1)
    stop(port)
    subprocess.Popen(['bash', args.start_script], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                     start_new_session=True)
    for _ in range(180):
        if up(args.server):
            return
        time.sleep(1)
    sys.exit('the server did not start')


def run_checks(args, extra):
    cmd = [sys.executable, os.path.join(HERE, 'server_check.py'), '--server', args.server, '--jobs', '6',
           '--settle', '1', '--deadline', '30', '--trace-dir', args.trace_dir] + extra
    r = subprocess.run(cmd, capture_output=True, text=True)
    lines = [l for l in r.stdout.splitlines() if re.match(r'^s\d+x\d+: ', l)]
    bad = [l for l in lines if not l.split(': ', 1)[1].startswith('reached')]
    return len(lines), bad, r.returncode


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--server', default='http://localhost:8099/api')
    ap.add_argument('--start-script', required=True)
    ap.add_argument('--trace-dir', required=True)
    ap.add_argument('--java-home', default=os.environ.get('JAVA_HOME', ''))
    ap.add_argument('--only', default=None)
    a = ap.parse_args()
    if ':8080' in a.server:
        sys.exit('refusing port 8080')
    missed = 0
    chosen = [m for m in MUTANTS if not a.only or re.search(a.only, m[0])]
    try:
        for name, rel, old, new, extra in chosen:
            path = os.path.join(CORE, rel)
            src = open(path).read()
            if src.count(old) != 1:
                print(f'mutant "{name}": text to change not found exactly once')
                missed += 1
                continue
            open(path, 'w').write(src.replace(old, new))
            try:
                build(a.java_home)
                restart(a)
                n, bad, _ = run_checks(a, extra)
            finally:
                open(path, 'w').write(src)
            if bad:
                print(f'mutant "{name}": caught by {len(bad)} of {n} tests, e.g. {bad[0][:150]}', flush=True)
            else:
                missed += 1
                print(f'mutant "{name}": NOT caught ({n} tests all matched)', flush=True)
    finally:
        build(a.java_home)
        restart(a)
    print(f'{len(chosen) - missed}/{len(chosen)} server mutants caught')
    sys.exit(1 if missed else 0)


if __name__ == '__main__':
    main()
