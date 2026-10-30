import json
import os
import statistics
import sys
import time
sys.path[:0] = ['/opt/ros/noetic/lib/python3/dist-packages', '/usr/lib/python3/dist-packages']
import rospy
from std_msgs.msg import String
rospy.init_node('codex_indicator_latency_readonly', anonymous=True, disable_signals=True)
samples = {'activity': [], 'indicator': []}
states = []
def receive(key, msg):
    samples[key].append(time.monotonic())
    if key == 'indicator':
        states.append(json.loads(msg.data))
subscribers = [rospy.Subscriber('/scheduler/control_activity', String, lambda m: receive('activity', m), queue_size=10),
               rospy.Subscriber('/indicator/state', String, lambda m: receive('indicator', m), queue_size=10)]
time.sleep(4)
for key, values in samples.items():
    intervals = [(b-a)*1000 for a,b in zip(values, values[1:])]
    print(key, json.dumps({'samples': len(values), 'interval_min_ms': min(intervals) if intervals else None,
                           'interval_mean_ms': statistics.mean(intervals) if intervals else None,
                           'interval_max_ms': max(intervals) if intervals else None}))
print('states', json.dumps(states))
rospy.signal_shutdown('read-only sample complete')
