"""Fetch only deployed scheduler source, using the previously authorized connection."""
import ast, hashlib, pathlib, socket
import paramiko

tree = ast.parse(pathlib.Path('E:/CODE/C++/Grinder/.planning/field-audit-20261025/collect.py').read_text(encoding='utf-8'))
password = next(ast.literal_eval(kw.value) for node in ast.walk(tree)
                if isinstance(node,ast.Call) and isinstance(node.func,ast.Attribute)
                and node.func.attr=='connect' for kw in node.keywords if kw.arg=='password')
sock=socket.socket();sock.settimeout(10);sock.bind(('192.168.1.10',0));sock.connect(('192.168.1.3',22))
client=paramiko.SSHClient();client.load_system_host_keys();client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    client.connect('192.168.1.3',sock=sock,username='root',password=password,look_for_keys=False,allow_agent=False,timeout=10)
    sftp=client.open_sftp()
    target=pathlib.Path('.planning/persistent-work-time/deployed-scheduler_node.py')
    sftp.get('/home/ztl/work/Grinder/catkin_ws/src/grinder_scheduler/src/grinder_scheduler/scheduler_node.py',str(target))
    print('read-only deployed source',len(target.read_bytes()),hashlib.sha256(target.read_bytes()).hexdigest())
    sftp.close()
finally:client.close()
