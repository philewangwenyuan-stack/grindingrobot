# Findings

TaskPathGeometry currently groups inside DrawScope on each draw; every segment scans all points at least once, then transforms each coordinate with repeated trig and draws one line per edge. Step4 image decode is already remembered. Git working tree was clean at investigation start.

Receive source confirmed: SlLinkManager.handleReceivedData processes frames sequentially; last path chunk directly parses JSON before next frame. TcpManager owns Default/IO-independent receiving callback and provides connect/disconnect/failure hooks. TaskPathChunkAssembler lacks clear; add reset so new session cannot complete an old assembly. Step4 already has separate robot marker; split its path/anchor Canvas. Replay combines static planned path with dynamic trail and should separate those layers too. Preserve actual green planned path and blue connector colors; existing red expectations are obsolete baseline tests.

Synthetic evidence: 50,000 points/500 summaries =208,848 point reads versus at least25,000,000 for old per-segment full scans; first host grouping31.2345ms (not device/frame latency). Cache queried500 times reads no points. Random150 cases against old full-scan oracle passed. Current projection still has one-time construction cost; bitmap decode on new bytes stays synchronous; do not claim all stalls eliminated.
