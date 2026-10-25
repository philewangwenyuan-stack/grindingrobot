# 发现

- 初始 git status 干净，上一轮未跟踪的障碍区文件已经移除。
- Step2 框外 tap 无条件 createArea，activeAreaId 被替换，旧未确认框不显示。
- Step2 Gesture 与 Erase 模式互斥；区域拖动子层与父层识别器会冲突。
- Step2/3 每次 draw 对所有回显点重新世界坐标投影、构造 Path。
- 下一步串行提交每个擦除多边形并等 ACK，然后 Step3 查询最新区域。不能假装立即成功或并发无关联 ACK。
- TCP 现有擦除接口接受任意闭合多边形；可把涂抹编译为逐段四边形并合并连续直线段，保持原协议。
