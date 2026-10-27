import sys, os, ast, pathlib, time, json, re
sys.dont_write_bytecode = True
sys.path.insert(0, '/opt/ros/noetic/lib/python3/dist-packages')
sys.path.append('/usr/lib/python3/dist-packages')
os.nice(15)
root = pathlib.Path('/home/ztl/work/Grinder')
sys.path.insert(0, str(root/'catkin_ws/src/grinder_scheduler/src'))
import cv2, numpy as np, rospy
from nav_msgs.msg import OccupancyGrid
from grinder_scheduler.map_service import MapService
rospy.rostime.set_rostime_initialized(True)
tree = ast.parse((root/'catkin_ws/src/grinder_scheduler/src/grinder_scheduler/scheduler_node.py').read_text())
names = {'_load_raw_grid_yaml', '_load_saved_raw_grid_map', '_build_offline_map_service_for_map'}
methods = [node for cls in tree.body if isinstance(cls, ast.ClassDef) for node in cls.body
           if isinstance(node, ast.FunctionDef) and node.name in names]
assert len(methods) == 3
cls = ast.ClassDef(name='Harness', bases=[], keywords=[], body=methods, decorator_list=[])
module=ast.fix_missing_locations(ast.Module(body=[cls], type_ignores=[]))
exec(compile(module, 'deployed-readonly-methods', 'exec'), globals())
for yaml in sorted((root/'temp/grinder_scheduler_state/map_states').glob('*/raw_grid/map.yaml')):
    if not any(key in str(yaml) for key in ['20261026_184506','20261027_185925']): continue
    h=Harness()
    h._saved_raw_grid_paths_for_map=lambda map_id: (str(yaml),str(yaml.parent/'map.pgm'))
    h._map_state_dir=lambda map_id: str(yaml.parent.parent)
    h._draw_region_id_on_preview=False; h._draw_region_label_on_preview=False
    h._preview_rga_enabled=False; h._preview_rga_backend='auto'; h._preview_rga_available=False
    for i in range(2):
        started=time.perf_counter()
        service=h._build_offline_map_service_for_map(yaml.parent.parent.name)
        elapsed=(time.perf_counter()-started)*1000
        print(json.dumps(dict(map_id=yaml.parent.parent.name,iteration=i,offline_build_ms=elapsed,
            note='read-only replay, nice=15, RGA disabled; not an in-process production timing')),flush=True)
