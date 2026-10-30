import json
import pathlib
import xmlrpc.client
import subprocess

master = xmlrpc.client.ServerProxy('http://127.0.0.1:11311')
names = master.getParamNames('/codex_readonly')[2]
wanted = [n for n in names if any(s in n for s in ('send_interval_sec', 'tick_hz', 'stream_push_hz', 'manual_command_timeout', 'status_indicator/backend'))]
print('PARAMS', json.dumps({n: master.getParam('/codex_readonly', n)[2] for n in wanted}, ensure_ascii=False))
base = pathlib.Path('/home/ztl/work/Grinder/catkin_ws/src/grinder_scheduler/src/grinder_scheduler')
for filename, terms in [('scheduler_node.py', ['def _publish_status', 'def _publish_control_activity', 'control_activity_pub.publish', 'self._publish_status(', 'tick_hz =']), ('status_indicator_node.py', ['self.interval =', 'self._stop.wait(self.interval)', 'def _update', 'def _run'])]:
    lines = (base / filename).read_text().splitlines()
    for i, line in enumerate(lines):
        if any(t in line for t in terms):
            print(filename, i + 1, '\n'.join(lines[max(0, i-2):i+7]))
result = subprocess.run(['bash', '-lc', 'source /opt/ros/noetic/setup.bash && timeout 5 rostopic echo -n 3 /indicator/state'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, timeout=8)
print('INDICATOR', result.stdout, result.stderr)
