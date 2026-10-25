"""Build a minimal patch against the freshly read deployed scheduler; never deploy it."""
import ast, difflib, hashlib, importlib.util, json, os, pathlib, re, subprocess, unittest, zipfile

app = pathlib.Path.cwd().resolve()
board = pathlib.Path('E:/CODE/C++/Grinder')
scope = app / '.planning/persistent-work-time'
output = app / 'artifacts/persistent-work-time'
output.mkdir(parents=True, exist_ok=True)
node_path = 'catkin_ws/src/grinder_scheduler/src/grinder_scheduler/scheduler_node.py'
module_path = 'catkin_ws/src/grinder_scheduler/src/grinder_scheduler/work_time_statistics.py'
test_path = 'catkin_ws/src/grinder_scheduler/test/test_work_time_statistics.py'
base = (scope / 'deployed-scheduler_node.py').read_text(encoding='utf-8')
changes = subprocess.run(['git','diff','--',node_path],cwd=board,check=True,capture_output=True,encoding='utf-8').stdout
patched = base
for hunk in re.split(r'^@@[^\n]*\n', changes, flags=re.MULTILINE)[1:]:
    lines = hunk.splitlines(keepends=True)
    before = ''.join(line[1:] for line in lines if line.startswith((' ', '-')))
    after = ''.join(line[1:] for line in lines if line.startswith((' ', '+')))
    if patched.count(before) != 1:
        raise RuntimeError('Local change does not uniquely match deployed source')
    patched = patched.replace(before, after, 1)
ast.parse(patched,feature_version=(3,8))
patch = ''.join(difflib.unified_diff(base.splitlines(keepends=True), patched.splitlines(keepends=True),
                                    fromfile='a/'+node_path, tofile='b/'+node_path))
for relative in (module_path, test_path):
    content = (board/relative).read_text(encoding='utf-8')
    patch += 'diff --git a/{0} b/{0}\nnew file mode 100644\n'.format(relative)
    patch += ''.join(difflib.unified_diff([],content.splitlines(keepends=True),fromfile='/dev/null',tofile='b/'+relative))
patch_file = output / 'field-work-time.patch'
patch_file.write_bytes(patch.encode('utf-8'))

# Prevent discovery of the enclosing app repo: git apply supports standalone trees.
check = scope / 'field-patch-check'
check.mkdir(exist_ok=True)
target = check / node_path
target.parent.mkdir(parents=True,exist_ok=True)
target.write_bytes(base.encode('utf-8'))
environment = dict(os.environ,GIT_CEILING_DIRECTORIES=str(scope))
subprocess.run(['git','apply','--check',str(patch_file)],cwd=check,env=environment,check=True,capture_output=True)
subprocess.run(['git','apply',str(patch_file)],cwd=check,env=environment,check=True,capture_output=True)
assert target.read_text(encoding='utf-8') == patched

spec = importlib.util.spec_from_file_location('field_work_time_tests',board/test_path)
tests = importlib.util.module_from_spec(spec); spec.loader.exec_module(tests)
tests.PACKAGE = check / 'catkin_ws/src/grinder_scheduler'
module_spec = importlib.util.spec_from_file_location('field_work_time_implementation',check/module_path)
implementation = importlib.util.module_from_spec(module_spec); module_spec.loader.exec_module(implementation)
tests.WorkTimeStatistics = implementation.WorkTimeStatistics
with (scope/'field-patch-tests.log').open('w',encoding='utf-8') as log:
    result=unittest.TextTestRunner(stream=log,verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(tests))
if not result.wasSuccessful():raise RuntimeError('Patched field source regression failed')

manifest = dict(deployed_scheduler_sha256=hashlib.sha256(base.encode('utf-8')).hexdigest(),
                files={path:hashlib.sha256((check/path).read_bytes()).hexdigest() for path in (node_path,module_path,test_path)},
                patch_sha256=hashlib.sha256(patch_file.read_bytes()).hexdigest(),
                offline_field_patch_tests=result.testsRun, remote_deployment=False)
(output/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
instructions = '''# 独立累计作业时间板端更新包

本包根据2026-10-26只读取得的现场调度源码制作，只增加独立计时接入、新计时模块及离线测试。
不包含整份调度器覆盖、其他未部署改动、运行时记录或配置凭据。原始源码哈希见manifest.json。
补丁已在现场源码副本执行git apply --check、应用，并通过16项离线计时/历史删除测试。
本次尚未向现场写文件、部署、安装APP或重启服务。

## 配套部署

确认任务停止、设备停稳后，备份现场源码及整个persist_state_dir（包含task_registry.json）。
首次启动新代码时，从仍保留且起止有效的历史迁入累计值；升级前清历史会丢失迁移来源。
在设备源码仓库根目录先核对scheduler_node.py原始哈希，再运行：

    git apply --check /path/to/field-work-time.patch
    git apply /path/to/field-work-time.patch
    python3 -m unittest discover -s catkin_ws/src/grinder_scheduler/test -p test_work_time_statistics.py -v

源码版本不匹配时需适配补丁并重新验证。应用后按设备既有流程重启调度服务，安装配套新APP。
这是纯Python及现有JSON响应扩展，没有新增ROS消息、protobuf布局或依赖包。
不要删除、覆盖或用旧备份还原新生成的work_time_statistics.json来回退累计值。

## 生效行为

今日/累计时间独立保存，删除任何或全部任务历史均保留时间。计时包含任务暂停，
不含空闲、手动遥控和服务停机。面积统计仍沿用历史来源。
首次迁移仅包含保留记录，已删除任务或缺失结束时间不可补准；界面显示下界和不完整标记。
检查点每5秒及开始/结束时后台保存，突然掉电可能丢失最后尚未落盘尾段并标为不完整。
独立文件损坏时显示异常并保留原文件，禁止通过历史删除进一步损失来源；不会静默清零。
备份/迁移设备时同时保存work_time_statistics.json。

APP缺少配套板端响应时显示“需更新板端”，不会继续用历史合计冒充机器累计时间。
'''
(output/'README.md').write_text(instructions,encoding='utf-8')
archive=output/'board-work-time-update.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for name in ('field-work-time.patch','manifest.json','README.md'):z.write(output/name,name)
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
print(json.dumps(dict(archive=str(archive),bytes=archive.stat().st_size,tests=result.testsRun,manifest=manifest),ensure_ascii=False))
