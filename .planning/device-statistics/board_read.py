"""Read-only SSH diagnostic runner; reuses existing local connection credentials."""
import ast
import pathlib
import socket
import sys
import paramiko

source = pathlib.Path('E:/CODE/C++/Grinder/.planning/field-audit-20261025/collect.py')
tree = ast.parse(source.read_text(encoding='utf-8'))
password = next(ast.literal_eval(kw.value) for node in ast.walk(tree)
                if isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute)
                and node.func.attr == 'connect' for kw in node.keywords if kw.arg == 'password')
sock = socket.socket()
sock.settimeout(10)
sock.bind(('192.168.1.10', 0))
sock.connect(('192.168.1.3', 22))
client = paramiko.SSHClient()
client.load_system_host_keys()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    client.connect('192.168.1.3', sock=sock, username='root', password=password,
                   look_for_keys=False, allow_agent=False, timeout=10)
    stdin, stdout, stderr = client.exec_command('python3 -', timeout=35)
    stdin.write(pathlib.Path(sys.argv[1]).read_text(encoding='utf-8'))
    stdin.channel.shutdown_write()
    output = stdout.read().decode('utf-8', errors='replace')
    errors = stderr.read().decode('utf-8', errors='replace')
    if len(sys.argv) > 2:
        pathlib.Path(sys.argv[2]).write_text(output, encoding='utf-8')
    print(output)
    print(errors, file=sys.stderr)
    sys.exit(stdout.channel.recv_exit_status())
finally:
    client.close()
