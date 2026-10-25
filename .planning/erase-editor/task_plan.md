# 擦除区域性能与手势

1. [complete] 核对撤销状态、Step2/3 绘制和提交链路。
2. [complete] 合并编辑/双指缩放，保留框外选区，加入可撤销涂抹。
3. [complete] 缓存区域路径、减少下一步首帧重复计算；保留服务端确认顺序。
4. [complete] 22 单测、6 原生测试、模块编译、APK 已通过；验证报告完成，临时模拟器已清理。

约束：无障碍区改动，无板端写入；涂抹必须下发真实擦除多边形，不能仅作本地视觉。

错误记录：PowerShell 不支持 Bash 的 {ui,viewmodel} 路径展开，改成父目录搜索；JDK 不在 Program Files/Java，使用已安装运行环境。

删除未使用 import 的脚本误删 Compose getValue/setValue 运算符导入，已修复并通过编译。原 emulator-5554 拒绝 test APK 安装（prepare session dir），无包变更；只读备份原测试包。改用新建隔离 MuMu1，created_timestamp=1792952397470893，ADB127.0.0.1:16416，结束后仅清理这个实例。
