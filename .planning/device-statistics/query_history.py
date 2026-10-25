"""Read the live history endpoint and current task report; send no control commands."""
import datetime, json, pathlib, socket, sys, time
sys.path.insert(0,'E:/CODE/C++/Grinder/third_party/sl_linka/sdk/python')
from sl_link.frame import SlFrame,SlFrameParser
from sl_link.message_gen import sl_link_pb2 as pb
s=socket.socket();s.settimeout(3);s.bind(('192.168.1.10',0));s.connect(('192.168.1.3',8002))
parser=SlFrameParser();chunks={};reports=[];history=None
request=pb.TaskExecutionHistoryRequest(max_chunk_size=4096)
s.sendall(SlFrame(version=1,flags=0,seq=32001,ack_seq=0,src_id=1,dst_id=16,comp_id=0,msg_id=pb.MSG_ID_TASK_EXECUTION_HISTORY_REQUEST,payload=request.SerializeToString()).pack())
deadline=time.monotonic()+12
try:
    while time.monotonic()<deadline:
        try:data=s.recv(65536)
        except socket.timeout:
            if history:break
            continue
        if not data:break
        for frame in parser.parse(data):
            if frame.msg_id==pb.MSG_ID_TASK_STATUS_REPORT:
                report=pb.TaskStatusReport();report.ParseFromString(frame.payload)
                reports.append(dict(task_id=report.task_id,state=pb.TaskState.Name(report.state)))
            if frame.msg_id==pb.MSG_ID_TASK_EXECUTION_HISTORY_CHUNK:
                chunk=pb.TaskExecutionHistoryChunk();chunk.ParseFromString(frame.payload)
                chunks[chunk.chunk_index]=bytes(chunk.data)
                if len(chunks)==chunk.total_chunks:
                    history=json.loads(b''.join(chunks[i] for i in range(chunk.total_chunks)))
        if history and reports:break
finally:s.close()
if history is None:raise RuntimeError('No complete history response')
for r in history.get('records',[]):
    for key in list(r):
        if key not in ('execution_id','map_id','task_id','final_state','stop_reason','started_at','finished_at','planned_area_m2','executed_area_m2','progress','path_version','all_completed'):del r[key]
output=dict(captured_at=datetime.datetime.now().astimezone().isoformat(),current_reports=reports[-3:],history=history)
pathlib.Path('.planning/device-statistics/live-history.json').write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(output,ensure_ascii=False))
