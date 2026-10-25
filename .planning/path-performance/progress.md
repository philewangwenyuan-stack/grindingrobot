# Progress

2026-10-25: Read agent.md and planning workflow. Reviewing map geometry and receiving lifecycle before changes.

First implementation: indexed summary matching preserves original point order/breaks; single-entry per-overlay geometry cache projects once, builds round polylines and uses canvas matrices for viewport changes. Step4/start preview/replay now separate static path from moving overlays. Receive JSON moved to Default single worker, queue2 and64MiB budget, explicit busy result, connection reset fences old results. Adding lifecycle/concurrency and geometric equivalence tests before build.

Initial run: TCP94/94 passed; new cache tests5/5 and viewport2 passed, geometry8/12 passed with exactly the four known unchanged red-vs-green color failures. No new algorithm failures. Initial app packaging blocked by that expected failing test task. Added a display-alignment reuse case and strengthened cache-invalidation checks; final targeted tests/native APK/full app build now running.

Final targeted cache6 + viewport2 unit tests pass. Existing ManualDriveUiTest7 native tests pass on isolated Android12 MuMu index1 (created_timestamp1792926508809516), ADB127.0.0.1:16416, 1600x1000/density224. Production packaging still running. New render-reference test needs one test APK refresh after adding clipping to its side-by-side capture fixture; no production change.

2026-10-25本地验收完成：TCP94项、缓存/视口8项（新缓存6、原视口2）、原生UI8项（ManualDriveUiTest7 + TaskPathCacheRenderTest1）全部通过。新像素对比覆盖0/37/-90/180度和37度下2.3倍平移视图，与旧逐边绘制对比通过，保持断点和孤立点。完整生产/测试APK构建成功，final-build.log（4min）；native-fixture-build.log（55s）仅刷新新增测试的裁切边界，不改变生产代码。最终APK app/build/outputs/apk/dev/debug/app-dev-arm64-v8a-debug.apk，2026-10-25 19:16:36 +08。git diff --check通过。
初次较广几何组19项中4项为已知颜色断言失败（期望红色、产品绿色），其余15项通过；最终定向8项全部通过。没有更改板端调度器、图片解码、协议或现场机器人；尚无实机性能测量，因此不能认定此前多秒等待的主因。

隔离测试实例index1（created_timestamp1792926508809516）已恢复显示设置、关闭并删除，最终info all仅原index0。原实例磁盘及用户应用未修改。

2026-10-25: User requests quantitative benefit and raw data. Starting repeated controlled benchmark fixtures and downloadable summary/raw datasets. Production source remains unchanged this follow-up; new tests opt-in only.

Benchmark preparation compilation found missing MapGeo import in frozen baseline fixture header; generator fixed for both JVM/Android test sources. Initial measurements were not used. Final JVM measurements will run with one Gradle worker after compilation, avoiding concurrent deliberate compile load. LibreOffice is unavailable; deliver CSV summaries plus raw workbook without calculated cells, so no unavailable formula engine is required.

Controlled JVM benchmarks finished successfully (3m28s, single Gradle worker), raw504 geometry rows +252 JSON rows. Exact measured grouping reads:50k37525500→208848,100k150051000→418700;500 cached lookups read0 points at each size. Android software-render measurement pending; no Android/compile work was deliberately scheduled during JVM measurements.

Quantification JVM final run complete: 5 warmups then21 paired samples, alternating order. 50k grouping medians284.672ms old vs2.5686ms new; JSON receiver occupation37.5868ms vs0.061ms, result-ready37.5868ms vs37.965ms. Native software drawing completed through50k;100k benchmark still running. Temporary VM1 created_timestamp1792928103288176, Android12 SDK32 x86_64,4 vCPUs, heap growth192m,512m max; benchmark only, no production Application/robot connection.

Quantification completed2026-10-25: JVM build/test successful3m28s; Android software benchmark passed515.5s. Frozen old baseline source + actual optimized source compared at1k/10k/50k/100k; every variant5 warmups +21 recorded samples, order alternated. Total1092 raw records/26 summaries. 50k native first miss2049.160212→111.148805ms (94.5759% lower), cached redraw2063.122264→46.952229ms (97.7242% lower). Native100k cached7296.8875→92.4284ms; exact raw summary.csv authoritative. JVM50k grouping284.672→2.5686ms (99.0977% lower); receive occupation37.5868→0.061ms while result-ready37.5868→37.965ms. Small-path grouping +0.0401ms and10k JSON result-ready +1.5952ms retained explicitly; not all stages improve.
Delivered artifacts/path-performance: rawCSV3 + operation counters, Chinese/English summaries, methodology/report, raw Excel(no formulas; no recalculation engine required), environment/source checksums,84KB ZIP with7 benchmark/reproduction source files. QA validated1092 records,26 paired groups,21 samples/variant, arithmetic, P95 ranges,11 data hashes,7 archived source hashes,ZIP CRC and workbook0 formula/errors. Report opened in Codex (queued). This follow-up changes test fixtures/scripts/docs/data only; production logic and prior APK are unchanged. No real robot/device deployment or field FPS/network timing measurement.

Benchmark-only MuMu index1(created_timestamp1792928103288176) shut down and deleted; final info all retains only original index0. Original disk/apps untouched.

Audit-log stdout used Windows GBK; normalized audit JSON to UTF-8. Delivered CSV/report files use explicit UTF-8 and XLSX/ZIP are binary; their verified hashes are unaffected.
