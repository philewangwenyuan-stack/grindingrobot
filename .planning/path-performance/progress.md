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
