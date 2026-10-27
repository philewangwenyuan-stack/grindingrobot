import subprocess, pathlib, hashlib

def run(label, command, timeout=5):
    print('\n=== ' + label + ' ===', flush=True)
    try:
        p = subprocess.run(['bash', '-lc', 'source /opt/ros/noetic/setup.bash; ' + command],
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout, text=True)
        print(p.stdout[-13000:], flush=True)
    except Exception as e: print(type(e).__name__ + ': ' + str(e), flush=True)

run('LIVE COLLISION DETAIL', 'rostopic echo -n 1 /rpp/collision_detail')
run('LIVE COLLISION BOOLEAN', 'rostopic echo -n 1 /rpp/collision_imminent')
run('FOOTPRINT PARAMETERS', 'rosparam get /move_base/local_costmap/footprint; rosparam get /move_base/global_costmap/footprint; rosparam get /move_base/local_costmap/robot_base_frame; rosparam get /move_base/base_local_planner')
run('SCAN HEADER', 'rostopic echo -n 1 /scan/header')
run('FILTER SETTINGS IN DEPLOYED CONFIG', "grep -R -n 'collision_self_filter' /home/ztl/work/Grinder/catkin_ws/src/2-dnavigation-package/2dnavigation/teb_local_planner_tutorials/cfg /home/ztl/work/Grinder/catkin_ws/src/grinder_scheduler/config /home/ztl/work/Grinder/catkin_ws/src/2-dnavigation-package/2dnavigation/teb_local_planner_tutorials/launch 2>/dev/null")
root = pathlib.Path('/home/ztl/work/Grinder/catkin_ws/src/2-dnavigation-package/2dnavigation/regulated_pure_pursuit_controller')
path = root / 'src/regulated_pure_pursuit_controller.cpp'
text = path.read_text()
print('\n=== DEPLOYED RPP SOURCE ===\nsha256=' + hashlib.sha256(path.read_bytes()).hexdigest())
lines=text.splitlines()
for index,line in enumerate(lines):
    if 'self_filter_invalid_' in line or 'void RegulatedPurePursuitController::publishCollisionDetail' in line:
        print('\n'.join('%d:%s'%(i+1,lines[i]) for i in range(max(0,index-4),min(len(lines),index+13))))
run('MATCHING ROSOUT ERRORS', "grep -iE 'RPP|collision|self.filter|fresh laser|TF.*scan' /home/ztl/.ros/log/latest/rosout.log | tail -65")
