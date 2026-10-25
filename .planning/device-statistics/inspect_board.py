import datetime, json, pathlib, subprocess
print('time', datetime.datetime.now().astimezone().isoformat())
print('hostname', pathlib.Path('/etc/hostname').read_text().strip())
print('processes', subprocess.check_output(['pgrep', '-af', 'scheduler_node.py'], text=True))
root = pathlib.Path('/home/ztl/work/Grinder')
source = root / 'catkin_ws/src/grinder_scheduler/src/grinder_scheduler/scheduler_node.py'
lines = source.read_text().splitlines()
for i, line in enumerate(lines):
    if any(key in line for key in ['_persist_state_dir =', '_persist_state_enabled =', '_task_registry_state_file =', 'task_executions', 'def build_task_execution_history_chunks']):
        print('source', i+1, '\n'.join(lines[max(0,i-2):i+5]))
for path in [root/'temp/grinder_scheduler_state/task_registry.json', root/'catkin_ws/temp/grinder_scheduler_state/task_registry.json']:
    print('registry_candidate', str(path), path.exists())
