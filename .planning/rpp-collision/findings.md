# 发现

- 本地 RPP 有固定距离/扩展车体扫描碰撞和 costmap 预测碰撞两套机制；无新鲜扫描也会停。
- 新版可通过 /rpp/collision_detail 提供方向、点数、扫描年龄和 TF 信息；先确认现场部署版本。
- 已读板端 AGENTS.md；仅进行只读 SSH/ROS 订阅，不启停任务、不修改阈值。

- 现场 2026-10-26 17:36–17:37（板端 +08）运行扩展车体扫描检测；costmap 预测 use_collision_detection=false。
- /rpp/collision_detail 实读：reason=self_filter_invalid front_hits=0 side_hits=0 body_hits=0 self_hits=0 scan_age_s=0.0228667 latched=1；collision_imminent=True。零点数是提前退出的默认统计，不能证明物理环境无障碍。
- 现场 ROS 参数与 YAML 均 collision_self_filter_enabled=true，collision_self_filter_polygon=[]。
- 已部署源码 SHA256 d49322ebe4978e932064fad09d08f798a1cb6a734c9ff075346cd130298ade4a。初始化153–161行：启用时 xy.size()<6 标记非法；874行：非法直接 false，甚至不进入 TF/点分类。调用方按检查失败停机并锁存；清除定时器也因同样失败无法解除。
- 具体配置：/home/ztl/work/Grinder/catkin_ws/src/2-dnavigation-package/2dnavigation/teb_local_planner_tutorials/cfg/diff_drive/rpp_local_planner_params.yaml:65–66。本地同文件也 true+[]。
- 修复建议：没有实测车身自反射滤除多边形时设置 self_filter_enabled=false（保留扩展车体碰撞检测）；或提供实测至少3顶点的 base_link 扁平数组。不能拿导航 footprint 直接当自滤波范围，否则可能屏蔽真障碍。
- 自滤波参数仅初始化读取，rosparam set 不会更新已存在的 self_filter_invalid_，需要重新加载导航实例。此轮仅诊断，未执行更改/重启。
