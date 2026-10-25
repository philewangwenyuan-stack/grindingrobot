"""Validate paired raw samples and export human-readable summaries plus a raw-data workbook."""
from pathlib import Path
from collections import defaultdict
import csv
import hashlib
import json
import math
import statistics
import zipfile
from openpyxl import Workbook, load_workbook
from openpyxl.styles import Font, PatternFill, Alignment
from openpyxl.comments import Comment
from openpyxl.worksheet.table import Table, TableStyleInfo

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "artifacts/path-performance"
RAW = ["jvm-geometry-raw.csv", "jvm-receive-raw.csv", "android-render-raw.csv"]
DESCRIPTIONS = {
    "group_cold": ("首次分组", "JVM纯分组；路径和运行环境已预热，每次重新构建分组"),
    "geometry_cold": ("首次几何准备", "JVM分组及投影；新版仅准备Bitmap坐标，不包含原生Path构建/矩阵绘制"),
    "geometry_repeat": ("重复几何准备", "旧版重新分组投影，新版缓存查询；新版2000次/批归一化，不含绘制"),
    "render_cold": ("首次路径绘制", "Android模拟器1200×800软件离屏Canvas；包含分组/投影/原生Path构建/清屏/绘制"),
    "render_repeat": ("缓存后路径绘制", "Android模拟器软件离屏Canvas；新版复用几何，包含清屏及旋转/缩放/平移绘制"),
    "receive_block": ("JSON对接收调用方的占用", "已完整组包；旧版同步解析，新版提交后接收调用方可继续；非真实ACK RTT"),
    "json_result_ready": ("JSON路径结果就绪", "已完整组包到解析返回/发布回调入口；不含网络、组包和页面显示"),
}

def read_csv(name):
    with (OUT / name).open(encoding="utf-8-sig", newline="") as source:
        return list(csv.DictReader(source))

def write_csv(name, rows, fields):
    with (OUT / name).open("w", encoding="utf-8-sig", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)

groups = defaultdict(lambda: defaultdict(list))
all_samples = []
for name in RAW:
    rows = read_csv(name)
    assert rows, name
    for row in rows:
        row = dict(row)
        for key in ("points", "segments", "iteration", "batch_ops", "duration_ns"):
            row[key] = int(row[key])
        assert row["duration_ns"] > 0 and row["batch_ops"] > 0
        row["json_bytes"] = int(row.get("json_bytes", 0))
        row["source_file"] = name
        all_samples.append(row)
        groups[(row["environment"], row["stage"], row["points"], row["segments"], row["json_bytes"])][row["variant"]].append(row)

def p95(values):
    return sorted(values)[math.ceil(0.95 * len(values)) - 1]

summaries = []
for (environment, stage, points, segments, byte_count), variants in sorted(groups.items()):
    assert set(variants) == {"old", "new"}
    values = {}
    for variant, rows in variants.items():
        assert len(rows) == 21 and sorted(row["iteration"] for row in rows) == list(range(21))
        values[variant] = [row["duration_ns"] / row["batch_ops"] / 1e6 for row in rows]
    old = statistics.median(values["old"])
    new = statistics.median(values["new"])
    summaries.append(dict(environment=environment, stage=stage, points=points, segments=segments,
        json_bytes=byte_count, samples=21, old_median_ms=old, new_median_ms=new,
        reduction_pct=(1 - new / old) * 100, speedup=old / new,
        old_p95_ms=p95(values["old"]), new_p95_ms=p95(values["new"]),
        old_min_ms=min(values["old"]), new_min_ms=min(values["new"]),
        old_max_ms=max(values["old"]), new_max_ms=max(values["new"])))

assert len(all_samples) == 1092 and len(summaries) == 26, (len(all_samples), len(summaries))
write_csv("summary.csv", summaries, list(summaries[0]))
labels = ["环境", "指标", "路径点数", "摘要段数", "JSON字节数", "每版样本数", "旧版中位数ms", "新版中位数ms",
          "中位耗时降低百分比", "加速倍数", "旧版P95ms", "新版P95ms", "旧版最小ms", "新版最小ms", "旧版最大ms", "新版最大ms"]
translated = []
for summary in summaries:
    row = list(summary.values())
    row[1] = DESCRIPTIONS[summary["stage"]][0]
    translated.append(dict(zip(labels, row)) | {"测量口径": DESCRIPTIONS[summary["stage"]][1]})
write_csv("收益汇总.csv", translated, labels + ["测量口径"])

def at(stage, points=50000):
    return next(row for row in summaries if row["stage"] == stage and row["points"] == points)

def table(stages):
    result = ["| 指标 | 点数/段数 | 旧中位数 ms | 新中位数 ms | 降低 | 旧 P95 ms | 新 P95 ms |",
              "|---|---:|---:|---:|---:|---:|---:|"]
    for row in sorted((r for r in summaries if r["stage"] in stages), key=lambda r: (r["points"], r["stage"])):
        result.append(f"| {DESCRIPTIONS[row['stage']][0]} | {row['points']:,}/{row['segments']:,} | "
                      f"{row['old_median_ms']:.4f} | {row['new_median_ms']:.4f} | {row['reduction_pct']:.2f}% | "
                      f"{row['old_p95_ms']:.4f} | {row['new_p95_ms']:.4f} |")
    return "\n".join(result)

counts = read_csv("operation-counts.csv")
sample_counts = next(r for r in counts if r["points"] == "50000")
legacy_reads, new_reads = int(sample_counts["legacy_group_point_reads"]), int(sample_counts["new_group_point_reads"])
env = json.loads((OUT / "environment.json").read_text(encoding="utf-8-sig"))
manifest = json.loads((OUT / "source-manifest.json").read_text(encoding="utf-8"))
for name, expected in manifest["current_source_sha256"].items():
    assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == expected, f"Source changed: {name}"
benchmark_sources = [
    "scripts/path-performance/generate_fixtures.py", "scripts/path-performance/summarize.py",
    "scripts/path-performance/benchmark.init.gradle",
    "feature/map/src/test/java/com/sinelynx/grindingrobot/feature/map/ui/PathPerformanceFixtures.kt",
    "feature/map/src/test/java/com/sinelynx/grindingrobot/feature/map/ui/PathPerformanceBenchmarkTest.kt",
    "feature/map/src/androidTest/java/com/sinelynx/grindingrobot/feature/map/ui/PathRenderPerformanceBenchmarkTest.kt",
    "core/tcp/src/test/java/com/sinelynx/grindingrobot/core/tcp/TaskPathReceivePerformanceBenchmarkTest.kt",
]
source_checksums = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest() for name in benchmark_sources}
(OUT / "benchmark-source-checksums.json").write_text(json.dumps(source_checksums, indent=2), encoding="utf-8")

report = f'''# 路径优化收益量化报告

测试日期：2026-10-25（Asia/Shanghai）。合成数据；没有现场平板、机器人或网络测试。

## 主要结果：5万点、500段

{table(["group_cold", "render_cold", "render_repeat", "receive_block", "json_result_ready"]).splitlines()[0]}
{table(["group_cold", "render_cold", "render_repeat", "receive_block", "json_result_ready"]).splitlines()[1]}
'''
for stage in ("group_cold", "render_cold", "render_repeat", "receive_block", "json_result_ready"):
    row = at(stage)
    report += f"| {DESCRIPTIONS[stage][0]} | 50,000/500 | {row['old_median_ms']:.4f} | {row['new_median_ms']:.4f} | {row['reduction_pct']:.2f}% | {row['old_p95_ms']:.4f} | {row['new_p95_ms']:.4f} |\n"
report += f'''
“首次”指预热后的缓存未命中，**不是APP冷启动**。绘制指Android模拟器离屏软件Canvas的CPU与光栅化时间，**不是屏幕帧时间或FPS**。JVM分组、Android绘制、JSON是独立实验，不能相加作为端到端节省时间。

路径JSON解析工作没有消失；它被移到后台。结果就绪一行若出现负收益，表示本次样本中新版结果更晚就绪，可能包含调度/GC抖动，不能认定为固定退化。接收调用方占用减少，不能换算为真实网络ACK往返时延，也不能证明板端规划等待缩短。

小规模不保证每个子阶段都更快：1千点首次分组0.4383→0.4784ms，增加0.0401ms（9.15%）；相同规模Android首次绘制7.7494→4.0933ms。JSON的1万点结果就绪8.5611→10.1563ms，也保留在完整表中，没有删除负收益项。

结构计数同样使用5万点/500段：旧分组实测{legacy_reads:,}次点读取，新分组{new_reads:,}次，减少{(1-new_reads/legacy_reads)*100:.2f}%。同一路径缓存查询500次，额外点读取为0。按每段100点、无断点的固定输入计算，每次路径绘制API调用由49,500次逐边画线变为500次折线绘制（减少98.99%）；API次数是结构计算，点读取是计数器实测，均不是GPU工作或耗时降低98.99%的保证。

## 各规模完整对照

### JVM首次分组

{table(["group_cold"])}

### Android模拟器离屏软件绘制

{table(["render_cold", "render_repeat"])}

### JSON接收占用与结果就绪

{table(["receive_block", "json_result_ready"])}

JSON字节大小以及额外的JVM几何准备/缓存查询数据见`收益汇总.csv`。重复几何查询的新版本每批2000次再归一化；不能把这个极短的查询耗时当成整次地图绘制耗时。

## 方法与可复现性

- 旧版：提交`{manifest['baseline_commit']}`中的原分组和原逐边绘制函数，只重命名函数；源文件与新版本哈希在`source-manifest.json`。标量投影助手与旧版一致，已自动校验。
- 数据：1千、1万、5万、10万点，每段100点，顺序蛇形路径，摘要不重叠，每10段一段蓝色连接，其余绿色，所有点有效。它不代表所有现场路径形状；正确性另有乱序、断段、孤立点和越界回归验证。
- 每版每指标先预热5次，再采样21次；旧/新执行顺序逐次交替。中位数用第11个排序值；P95采用nearest rank，即21个样本中的第20个。
- 计时使用System.nanoTime，原始纳秒和批次数均保留；CSV的中位数降低为`(1-新中位数/旧中位数)×100%`，加速倍数为旧/新中位数之比。
- 开发机：{env['cpu']}，{env['physical_cores']}核/{env['logical_processors']}线程，{int(env['memory_bytes'])/1024**3:.1f}GiB，{env['os']} {env['os_version']}，JDK17。JVM阶段单Gradle工作者，没有安排并行编译或模拟器测试；不能保证用户电脑没有其他后台负载。
- Android：隔离MuMu Android12 x86_64，DevDebug测试构建；1200×800软件Bitmap Canvas。清屏、路径准备和绘制计入，图片解码、Compose布局、GPU提交、显示刷新不计入。视角每轮按确定规律改变旋转、倍率和平移。
- JSON：从完整字节已组包开始；旧版同步解析，新版实际TaskPathDecodeQueue单工作者、无积压提交。结果时间截至解析返回/发布回调入口，不含StateFlow消费者或页面首帧。未用人为sleep放大收益。
- 原始有效采样共{len(all_samples)}条，汇总{len(summaries)}组。没有按耗时删去样本；预热样本按预设方案不写入有效采样。

## 文件

- `收益汇总.csv`：中文汇总，含中位数、P95、范围、比例和口径；Excel可打开。
- `summary.csv`：相同结果的英文机器可读版本。
- `jvm-geometry-raw.csv`、`jvm-receive-raw.csv`、`android-render-raw.csv`：逐次原始记录。
- `operation-counts.csv`：点读取为计数器实测，绘制API次数按固定输入结构计算；不是耗时。工作簿的操作次数页只保留实测读取计数及输入规模。
- `原始数据.xlsx`：原始测量工作簿、字段解释和环境；只存原始记录，没有公式或衍生计算单元格。统计结果以CSV和本报告为准。
- `environment.json`、`source-manifest.json`、`data-checksums.json`：环境、代码和数据校验信息。数据包另含基准测试及生成脚本源码，源码哈希在`benchmark-source-checksums.json`。

## 结论的边界

本实验能量化这几处APP热点的局部收益，不能据此推断现场整页帧率、完整规划耗时、整机CPU/电量或峰值内存节省。要定位剩余多秒等待，仍需在同一条真实路径上记录请求、设备响应、组包、解析完成和首帧显示时间。

## 复跑入口（项目根目录）

1. `python scripts/path-performance/generate_fixtures.py`校验旧投影助手并生成固定基线。
2. 先编译两个模块的单测及`feature:map:assembleDevDebugAndroidTest`，避免测量中并行编译。
3. PowerShell设置`$env:ROBOT_PATH_BENCHMARK='1'`及`$env:ROBOT_PATH_BENCHMARK_OUTPUT=(Resolve-Path artifacts/path-performance).Path`。
4. Gradle选择`core:tcp:testDevDebugUnitTest --tests '*TaskPathReceivePerformanceBenchmarkTest'`和`feature:map:testDevDebugUnitTest --tests '*PathPerformanceBenchmarkTest'`，加`--init-script scripts/path-performance/benchmark.init.gradle --max-workers=1 --no-daemon`。该初始化脚本只使选定测试重跑，不强制重编译生产代码。
5. 在隔离Android模拟器安装map测试APK，运行`PathRenderPerformanceBenchmarkTest`并传runner参数`-e robotPathBenchmark true`；输出在测试宿主外部文件目录`path-performance/android-render-raw.csv`，拉取到本目录。默认普通回归会跳过这些性能测试。
6. `python scripts/path-performance/summarize.py`校验1092条样本并重新生成汇总与数据包。
'''
(OUT / "收益报告.md").write_text(report, encoding="utf-8")

wb = Workbook()
guide = wb.active
guide.title = "说明"
guide.append(["条目", "说明"])
for key, value in [
    ("范围", "合成测试原始数据；未测现场平板、真实网络RTT或GPU帧时间。"),
    ("统计结果", "打开同目录的收益汇总.csv或收益报告.md；本工作簿仅保存原始记录，未存衍生计算或公式。"),
    ("采样", "每版预热5次（不保留预热耗时），有效21次，old/new顺序交替。"),
    ("时间单位", "duration_ns为整批纳秒；单次毫秒=duration_ns/batch_ops/1000000。"),
    ("汇总空值约定", "汇总CSV中JSON字节数为0表示该指标不是JSON实验，不表示路径JSON为空。"),
    ("批次", "JVM geometry_repeat的新版本为2000次查询一批；其余batch_ops=1。"),
    ("首次", "预热后新建缓存，非APP冷启动。"),
    ("环境", f"{env['cpu']}；{env['os']}；JDK17。Android为MuMu12 x86_64软件离屏绘制。"),
    ("颜色与几何", "当前绿色路径/蓝色连接段；有限有效蛇形路径。原有正确性回归另覆盖异常坐标和断段。"),
    ("数据来源", "同目录原始CSV与source-manifest.json；基准源码在项目scripts/path-performance及相应test/androidTest源码。"),
] + [(stage, text[1]) for stage, text in DESCRIPTIONS.items()]:
    guide.append([key, value])
guide.column_dimensions["A"].width = 26
guide.column_dimensions["B"].width = 105
for row in guide.iter_rows(min_row=2):
    row[1].alignment = Alignment(wrap_text=True, vertical="top")
    guide.row_dimensions[row[0].row].height = 38

for sheet_name, filename in [("JVM几何原始记录", RAW[0]), ("JSON原始记录", RAW[1]),
                              ("Android绘制原始记录", RAW[2]), ("操作次数", "operation-counts.csv")]:
    ws = wb.create_sheet(sheet_name)
    with (OUT / filename).open(encoding="utf-8-sig", newline="") as source:
        for index, row in enumerate(csv.reader(source)):
            if filename == "operation-counts.csv":
                row = row[:5]  # Keep only inputs and direct counters; derived API counts stay in CSV/report.
            ws.append([int(value) if index and value.lstrip("-").isdigit() else value for value in row])
    for column in ws.columns:
        ws.column_dimensions[column[0].column_letter].width = min(38, max(15, len(str(column[0].value)) + 3))
    tab = Table(displayName=f"Raw{wb.index(ws)}", ref=ws.dimensions)
    tab.tableStyleInfo = TableStyleInfo(name="TableStyleMedium2", showRowStripes=True)
    ws.add_table(tab)
    ws["A1"].comment = Comment(f"Source: {filename}; 2026-10-25; paired synthetic benchmark raw measurements. See source-manifest.json for code hashes.", "Codex")

for ws in wb:
    ws.freeze_panes = "A2"
    for row in ws:
        for cell in row:
            cell.font = Font(name="Arial", size=11)
    for cell in ws[1]:
        cell.fill = PatternFill("solid", fgColor="17365D")
        cell.font = Font(name="Arial", size=11, bold=True, color="FFFFFF")
    ws.row_dimensions[1].height = 25
book = OUT / "原始数据.xlsx"
wb.save(book)
check = load_workbook(book, data_only=False)
assert len(check.sheetnames) == 5
assert all(cell.data_type != "f" and cell.data_type != "e" for ws in check for row in ws for cell in row)
assert check["JVM几何原始记录"].max_row == 505
assert check["JSON原始记录"].max_row == 253
assert check["Android绘制原始记录"].max_row == 337
check.close()

checksums = {name: hashlib.sha256((OUT / name).read_bytes()).hexdigest()
             for name in RAW + ["operation-counts.csv", "summary.csv", "收益汇总.csv", "environment.json", "source-manifest.json", "benchmark-source-checksums.json", "原始数据.xlsx", "收益报告.md"]}
(OUT / "data-checksums.json").write_text(json.dumps(checksums, ensure_ascii=False, indent=2), encoding="utf-8")
with zipfile.ZipFile(OUT / "路径优化收益数据包.zip", "w", zipfile.ZIP_DEFLATED) as archive:
    for name in checksums:
        archive.write(OUT / name, name)
    archive.write(OUT / "data-checksums.json", "data-checksums.json")
    for name in benchmark_sources:
        archive.write(ROOT / name, "基准源码/" + name)
print(json.dumps({"samples": len(all_samples), "summaries": len(summaries), "workbook_formula_errors": 0,
    "headline_50k": {stage: at(stage) for stage in ("group_cold", "render_cold", "render_repeat", "receive_block", "json_result_ready")}}, ensure_ascii=False, indent=2))
