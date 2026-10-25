import collections, datetime, json, pathlib, time, xmlrpc.client
root = pathlib.Path('/home/ztl/work/Grinder')
path = root/'temp/grinder_scheduler_state/task_registry.json'
data = json.loads(path.read_text())
records = data.get('task_executions', [])
now = int(time.time())
local_now = datetime.datetime.now().astimezone()
day_start = int(local_now.replace(hour=0, minute=0, second=0, microsecond=0).timestamp())
print('clock', local_now.isoformat(), 'epoch', now, 'registry_mtime', path.stat().st_mtime, 'saved_at', data.get('saved_at'))
print('record_count', len(records), 'map_tasks_count', len(data.get('map_tasks', {})))
selected=[]; daily=collections.defaultdict(lambda: {'count':0, 'seconds':0})
total=today=0
for r in records:
    start=int(r.get('started_at',0) or 0); finish=int(r.get('finished_at',0) or 0)
    state=str(r.get('final_state','')).upper()
    if state.startswith('TASK_STATE_'): state=state[len('TASK_STATE_'):]
    end=min(finish,now) if finish>0 else now if state in ('RUNNING','PAUSED','PLANNING') else 0
    duration=max(0,end-start) if start>0 and end>=start else 0
    overlap=max(0,min(end,day_start+86400)-max(start,day_start)) if duration else 0
    date=datetime.datetime.fromtimestamp(start).astimezone().isoformat() if start>0 else 'invalid'
    daily[date[:10]]['count']+=1; daily[date[:10]]['seconds']+=duration
    total+=duration; today+=overlap
    selected.append({k:r.get(k) for k in ('execution_id','map_id','task_id','final_state','stop_reason','started_at','finished_at','executed_area_m2','progress')})
    selected[-1].update(start_local=date,duration_seconds=duration,today_seconds=overlap)
print('by_start_day', json.dumps(dict(sorted(daily.items())),ensure_ascii=False))
print('app_equivalent', json.dumps({'today_seconds':today,'total_seconds':total,'difference_seconds':total-today}))
print('records',json.dumps(selected,ensure_ascii=False))
master=xmlrpc.client.ServerProxy('http://127.0.0.1:11311')
for key in ('/grinder_scheduler/persist_state_enabled','/grinder_scheduler/persist_state_dir','/grinder_scheduler/persist_runtime_task_state'):
    print('param',key,master.getParam('/readonly_statistics_audit', key))
print('runtime_start',pathlib.Path('/proc/2672/stat').read_text().split()[21])
source=root/'catkin_ws/src/grinder_scheduler/src/grinder_scheduler/scheduler_node.py'
lines=source.read_text().splitlines()
for func in ('_load_task_registry_state','build_task_execution_history_chunks','_delete_task_execution_record'):
    for i,l in enumerate(lines):
        if l.startswith('    def '+func+'('):
            end=next((j for j in range(i+1,len(lines)) if lines[j].startswith('    def ')),len(lines))
            print('method',func,'\n'.join(lines[i:end]))
