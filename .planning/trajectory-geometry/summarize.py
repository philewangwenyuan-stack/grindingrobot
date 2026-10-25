import collections, csv, hashlib, json, math, pathlib, platform, statistics, subprocess, xml.etree.ElementTree as ET, zipfile

root=pathlib.Path.cwd();out=root/'artifacts/trajectory-geometry';scope=root/'.planning/trajectory-geometry'
groups=collections.defaultdict(list)
for filename,environment in [('jvm-update-raw.csv','Windows_JDK17'),('android-render-raw.csv','Android12_x86_64_software')]:
    rows=list(csv.DictReader((out/filename).open(encoding='utf-8')))
    assert len(rows)==(126 if filename.startswith('jvm') else 378)
    for row in rows:groups[(environment,row.get('stage','update_all_samples'),int(row['samples']),row['variant'])].append(row)
summary=[]
for (environment,stage,size,variant),rows in groups.items():
    assert len(rows)==21 and sorted(int(r['iteration']) for r in rows)==list(range(21))
    values=sorted(int(r['duration_ns'])/1e6 for r in rows)
    item=dict(environment=environment,stage=stage,samples=size,variant=variant,n=21,
              median_ms=statistics.median(values),p95_ms=values[math.ceil(.95*len(values))-1])
    for key in ('allocated_bytes','retained_vertices','coordinate_payload_bytes','vertices','draw_calls'):
        item[key]=statistics.median(int(r[key]) for r in rows) if key in rows[0] else ''
    summary.append(item)
with (out/'summary.csv').open('w',encoding='utf-8-sig',newline='') as f:
    writer=csv.DictWriter(f,fieldnames=summary[0].keys());writer.writeheader();writer.writerows(summary)
def get(stage,variant):return next(r for r in summary if r['stage']==stage and r['variant']==variant and r['samples']==50000)
old=get('update_all_samples','old500');new=get('update_all_samples','new_full')
oldPan=get('render_pan','old500');newPan=get('render_pan','new_full');oldFull=get('render_pan','old_full')
percent=lambda a,b:100*(1-b/a)
test_xml=[ET.parse(p).getroot() for p in (root/'feature/device/build/test-results/testDevDebugUnitTest').glob('TEST-*.xml')]
test_totals={k:sum(int(x.attrib[k]) for x in test_xml) for k in ('tests','failures','errors','skipped')}
assert test_totals['failures']==0 and test_totals['errors']==0 and test_totals['tests']==65 and test_totals['skipped']==1
assert 'OK (4 tests)' in (scope/'native-correctness-isolated.log').read_text()
assert 'OK (1 test)' in (scope/'native-benchmark.log').read_text()
base=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()
sources=[
 'feature/device/src/main/java/com/sinelynx/grindingrobot/feature/device/viewmodel/TaskTrajectoryGeometry.kt',
 'feature/device/src/main/java/com/sinelynx/grindingrobot/feature/device/ui/TaskTrajectoryDrawing.kt',
 'feature/device/src/main/java/com/sinelynx/grindingrobot/feature/device/ui/DeviceStatusScreen.kt',
 'feature/device/src/main/java/com/sinelynx/grindingrobot/feature/device/viewmodel/DeviceStatusViewModel.kt',
 'feature/device/src/test/java/com/sinelynx/grindingrobot/feature/device/viewmodel/TaskTrajectoryStateTest.kt',
 'feature/device/src/test/java/com/sinelynx/grindingrobot/feature/device/viewmodel/TaskTrajectoryPerformanceBenchmarkTest.kt',
 'feature/device/src/androidTest/java/com/sinelynx/grindingrobot/feature/device/ui/TaskTrajectoryRenderTest.kt',
 'feature/device/src/androidTest/java/com/sinelynx/grindingrobot/feature/device/ui/TaskTrajectoryRenderBenchmarkTest.kt',
 'feature/device/build.gradle.kts','scripts/path-performance/benchmark.init.gradle','docs/首页全程几何轨迹.md']
manifest=dict(date='2026-10-26 Asia/Shanghai',baseline_commit=base,warmups=5,iterations=21,
 total_raw_samples=504,summary_groups=24,jvm='Adoptium17.0.20.1 Windows10 amd64 i7-10700/16logical',
 android='Isolated MuMu Android12/API32 x86_64, heapgrowth192m, software800x640 render, native map1000x800/res0.1',
 fixture='40 serpentine rows, world disc centers x10..90 y10..68.5m, heading0/localDiscOffset0 to isolate geometry',
 tests=test_totals,native_geometry_tests=4,
 source_sha256={name:hashlib.sha256((root/name).read_bytes()).hexdigest() for name in sources},
 remote_robot_operations=False,temporary_emulator_removed=True)
final=json.loads((scope/'emulator-final.json').read_text(encoding='utf-8-sig'))
assert set(final)=={'0'}
manifest['apk_sha256']={abi:hashlib.sha256((root/f'app/build/outputs/apk/dev/debug/app-dev-{abi}-debug.apk').read_bytes()).hexdigest()
                        for abi in ('arm64-v8a','armeabi-v7a')}
(out/'environment-and-sources.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
report=f'''# 首页全程几何轨迹与资源对照

2026-10-26，APP本地实现和合成基准。首页实际执行轨迹从“最近500个位姿+逐边/逐采样圆绘制”改为连续几何带；规划路径仍使用已有完整缓存路径。

## 行为

- 保留本次APP接收过程从起点至当前的轨迹，不按时间或总点数淘汰。直行/近直行使用最大1厘米的绝对几何误差合并，保留转弯、回头和颜色变化；不是简单删掉早期点或加大原500上限。
- 以原有0.9米磨盘宽度画连续圆角Path；颜色沿用研磨遍次，较新遍次的形状在交叉处覆盖较早遍次。初始/孤立位置画单个轮廓，不再给每个采样点画圆。图形是执行轨迹示意，不作为磨盘实际使能或已研磨面积判据。
- 分块上限128只是单块容量，没有总量128/500限制。完成块不可变并复用，只有当前小块更新；机器人最新位置/朝向单独保存。手势用矩阵处理，重复缩放/平移不逐点投影。
- 暂停保留；换任务、空闲/显式停止沿用原清理规则。READY只更新小车，不记录执行带。断线、超过3秒没有上报、无效/越界位置留断口，不补画未观测到的行走。
- 几何参数未读取时先保存有效位姿，参数到齐后转换；已记录部分保留当时校准，参数变化从新段开始。APP退出/重启后的旧轨迹未新增回放补取，离线未收到的数据不能保证补全。

## 测量结果

输入为40行往复合成路线，5万个采样点；新几何80顶点、1个绘制Path，旧产品只保留最后500个位姿。以下每组预热5次，21次交替测量，中位数：

| 测量 | 旧版最近500点 | 新版保留全程 | 变化 |
| --- | ---: | ---: | ---: |
| 开发机整批5万条位姿更新 | {old['median_ms']:.4f} ms | {new['median_ms']:.4f} ms | 降低{percent(old['median_ms'],new['median_ms']):.2f}% |
| 上述更新累计托管分配 | {old['allocated_bytes']/1024/1024:.2f} MiB | {new['allocated_bytes']/1024/1024:.2f} MiB | 降低{percent(old['allocated_bytes'],new['allocated_bytes']):.2f}% |
| Android缓存命中后离屏手势重绘 | {oldPan['median_ms']:.4f} ms | {newPan['median_ms']:.4f} ms | 降低{percent(oldPan['median_ms'],newPan['median_ms']):.2f}% |

该示例旧版每次绘图999个线/圆调用，新版1个Path。历史轨迹数值字段载荷从旧500个位姿约8000字节变为640字节XY（另保留1条当前小车位姿与元数据）。这不是完整进程内存/峰值RAM测量，也未测原生Path缓冲占用。

同覆盖范围的对照也保留：将原逐边逐点画法用于全部5万点，缓存命中后离屏绘制为{oldFull['median_ms']:.4f}ms，新版为{newPan['median_ms']:.4f}ms。`old_full`是同覆盖范围的原画法对照，旧产品本身不会保留5万点。

不能把开发机数据更新耗时与Android离屏绘图相加，也不能换算为实机FPS/整页响应速度。模拟器只运行独立库测试，未启动生产机器人页面或连接机器人。

## 适用边界

所有收到的有效路段参与构建，最终形状按1厘米容差近似，不会逐条永久保存原始上报。长曲线和大量转弯仍会增加几何复杂度与内存，不能保证任意完整路线都比旧版被截断的500点占用更小。合成往复路线展示的是常见直线密集路径的收益，不代表所有地图和设备。压缩仅用于显示，不改变任务规划、下发路径或运动指令。

## 图与数据

下图是原生代码离屏输出的合成轨迹，非现场UI截图。原500点只剩最后一小段；新版显示全程。

旧版：

![最近500点](old500.png)

新版：

![完整几何带](new_full.png)

`jvm-update-raw.csv`126行、`android-render-raw.csv`378行，共504条有效数据；`summary.csv`包含全部24组中位数/P95。
`environment-and-sources.json`记录环境、源文件和APK哈希。数据包包含当前实现、基准源码及冻结旧版两个源文件。

下载：[全程几何轨迹数据包.zip](全程几何轨迹数据包.zip)。

## 验证与复跑

设备模块64项常规测试通过，另1项选择性性能测试在基准运行中通过。14项轨迹状态测试包含10万采样直线保留起终点、5000非共线转弯全部保留、1厘米全样本误差检查、分块不可变、颜色、回头、缺失位置和晚到参数。4项Android原生测试通过，覆盖全程起点、重绘缓存、越界断口、交叉颜色、0/30/-45度及缩放平移的像素对照。生产APK及测试包打包成功。

JVM复跑：设置环境变量`ROBOT_TRAJECTORY_BENCHMARK=1`和`ROBOT_TRAJECTORY_BENCHMARK_OUTPUT=输出绝对目录`，运行`:feature:device:testDevDebugUnitTest --tests '*TaskTrajectoryPerformanceBenchmarkTest'`并使用`--init-script scripts/path-performance/benchmark.init.gradle`。
Android复跑：构建/安装`:feature:device:assembleDevDebugAndroidTest`，对测试包`com.sinelynx.grindingrobot.feature.device.test/androidx.test.runner.AndroidJUnitRunner`运行类`TaskTrajectoryRenderBenchmarkTest`，传`-e robotTrajectoryBenchmark true`；原始输出在测试包外部目录`files/trajectory-performance`。
测试临时MuMu实例已核对创建身份并删除，原实例0保留；没有部署到平板或操作现场机器人。
'''
(out/'收益与验证.md').write_text(report,encoding='utf-8')
names=['jvm-update-raw.csv','android-render-raw.csv','summary.csv','environment-and-sources.json','收益与验证.md','old500.png','new_full.png']
hashes={name:hashlib.sha256((out/name).read_bytes()).hexdigest() for name in names}
(out/'data-checksums.json').write_text(json.dumps(hashes,ensure_ascii=False,indent=2),encoding='utf-8')
archive=out/'全程几何轨迹数据包.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for name in names+['data-checksums.json']:z.write(out/name,name)
    for name in sources:z.write(root/name,'source/'+name)
    for name in sources[2:4]:z.writestr('baseline/'+name,subprocess.check_output(['git','show',base+':'+name]))
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
print(json.dumps(dict(rows=504,groups=24,tests=test_totals,archive=str(archive),bytes=archive.stat().st_size,
                    update_reduction_percent=percent(old['median_ms'],new['median_ms']),
                    allocation_reduction_percent=percent(old['allocated_bytes'],new['allocated_bytes']),
                    native_pan_reduction_percent=percent(oldPan['median_ms'],newPan['median_ms'])),ensure_ascii=False))
