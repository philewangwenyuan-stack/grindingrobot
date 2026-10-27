import os, subprocess, glob, json

def run(label, command, timeout=7):
    print('\n=== ' + label + ' ===', flush=True)
    try:
        p = subprocess.run(['bash', '-lc', command], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout, text=True)
        print(p.stdout[-18000:], flush=True)
    except Exception as e:
        print(type(e).__name__ + ': ' + str(e), flush=True)

run('TIME', 'date -Iseconds; uptime')
run('STACK PROCESSES', "ps -eo pid,lstart,args | grep -E 'rosmaster|move_base|scheduler_node|cloud_to|super_lio|chassis_driver' | grep -v grep")
run('ROS TOPICS AND COLLISION PARAMS', "source /opt/ros/noetic/setup.bash; rosnode list; rostopic list | grep -E 'rpp|scan|costmap|task_status'; rosparam get /move_base/RegulatedPurePursuitController", 10)
run('LATEST ROS LOGS', "readlink -f /root/.ros/log/latest; readlink -f /home/ztl/.ros/log/latest; find /home/ztl/work/Grinder/temp -maxdepth 3 -type f -name '*.log' -printf '%T@ %p\\n' 2>/dev/null | sort -nr | head -12")
run('RECENT COLLISION LOGS', "for r in /root/.ros/log/latest /home/ztl/.ros/log/latest; do if [ -d \"$r\" ]; then find -L \"$r\" -maxdepth 1 -type f \\( -name '*move_base*' -o -name '*rosout*' -o -name '*scheduler*' \\) -print -exec tail -n 250 {} \\;; fi; done", 8)
