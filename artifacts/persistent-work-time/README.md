# 独立累计作业时间板端更新包

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
