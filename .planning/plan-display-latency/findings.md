# 结论与边界

- 现场 task_config_auto_plan_enabled=false，未见一次操作计算两遍。新路径单分片约1510–1677B，JSON解析已用Default有界队列，没有固定分片等待。
- 用户给出的1.5s是内部规划计时，不是完整点击链路：开始研磨还有区域刷新→配置ACK→规划→响应解析→首绘。
- 一条实读链路：区域请求1793098363.939→处理完1793098367.207，合计3267ms；内部规划1885.6ms。配置接受后至规划日志相隔1177.6ms（包含客户端往返、排队和板端前置，不可直接当作纯APP时间）。日志RX抽样，不能把所有请求时间精确配对。
- handle_path_plan_request在进入内部total计时之前构建离线MapService。只读AST重放实际部署的3个函数，两个地图各2次耗时173.95–178.74ms。单独低优先级进程、RGA关闭，不是生产进程实测；只用于确认遗漏阶段有实际开销。
- 前端Step4/开始研磨底图曾在主线程解码且用整页loading遮住地图。已统一复用后台底图解码，并改为保留地图、顶部规划提示。启动门禁、区域重读、请求ID校验与forceReplan保持原逻辑，未牺牲新障碍正确性。
- 新增PlanTiming按requestId串联preview_click/regions_ready/config_ack/path_request/request_written/all_chunks_received/json_parsed/path_parsed_ui_publish/first_draw_submitted。mono_ns全部来自APP同进程System.nanoTime；解析有parse_ms。first_draw_submitted为CPU绘制提交，不冒充屏幕真实呈现时间。
- 未用运行中的任务做规划重放、未安装覆盖用户APP/重启板端。现场剩余时间仍需新版真实请求日志量化，不声称已降到某个秒数。
