import datetime, json, pathlib, re, struct, sys
root=pathlib.Path('/home/ztl/work/Grinder')
sys.path.insert(0,str(root/'third_party/sl_linka/sdk/python'))
from sl_link.message_gen import sl_link_pb2 as pb
path=root/'temp/grinder_scheduler_state/task_registry.json'
records=json.loads(path.read_text()).get('task_executions',[])
for r in records:
    if r.get('finished_at') or r.get('final_state')!='RUNNING': continue
    trajectory=root/'temp/grinder_scheduler_state'/r.get('trajectory_path','')
    last=None; count=0; truncated=False
    if trajectory.is_file():
        with trajectory.open('rb') as f:
            while True:
                header=f.read(2)
                if not header:break
                if len(header)!=2:truncated=True;break
                length=struct.unpack('<H',header)[0]; data=f.read(length)
                if len(data)!=length:truncated=True;break
                last=pb.TaskTrajectoryPoint();last.ParseFromString(data);count+=1
    print('orphan_trajectory',json.dumps(dict(execution_id=r['execution_id'],count=count,stored_count=r.get('trajectory_point_count'),last_offset_ms=last.offset_ms if last else None,truncated=truncated,last_sample_epoch=((r.get('started_at_ms',r['started_at']*1000)+last.offset_ms)//1000 if last else None))))
logroot=pathlib.Path('/home/ztl/.ros/log')
for directory in sorted(logroot.iterdir()):
    if not directory.is_dir() or directory.name=='latest':continue
    for p in directory.glob('grinder_scheduler-*.log'):
        if p.stat().st_size>30_000_000:continue
        lines=p.read_text(errors='replace').splitlines()
        matches=[l for l in lines if any(key in l for key in ('Loaded task execution history','Task execution history query','Failed to finalize task result','Task execution record started','Task execution record finalized'))]
        print('log',str(p),'matches',len(matches))
        for line in matches[-50:]: print(line)
