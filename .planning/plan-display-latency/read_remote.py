import subprocess, pathlib, re
print(subprocess.run(['bash','-lc','source /opt/ros/noetic/setup.bash; date -Iseconds; rosparam get /grinder_scheduler/task_config_auto_plan_enabled'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=8).stdout)
path=pathlib.Path('/home/ztl/.ros/log/latest/rosout.log')
with path.open('rb') as f:
    f.seek(0,2); size=f.tell(); f.seek(max(0,size-16000000)); text=f.read().decode('utf-8','replace')
pattern=re.compile(r'PathPlanRequest:|PathPointPlan|PathPlan perf|Planner.*perf|Planning base cache|TaskConfig.*(accepted|planning|replaced)|Navigation map.*(refresh|reload)|map_prepare|plan_total|MST27.*(time|elapsed|perf)',re.I)
for line in [s for s in text.splitlines() if pattern.search(s)][-65:]:
    print(re.sub(r' \[topics: [^\]]*\]', '', line))
