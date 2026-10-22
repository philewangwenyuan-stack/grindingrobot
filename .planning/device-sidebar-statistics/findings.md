# Findings
- DeviceStatusUiState todayAreaText/totalAreaText were placeholders; today work duration was hardcoded in UI.
- 0x0504 reports current task planned/remain area and estimated remaining seconds.
- TaskExecutionHistoryPayload provides actual executedAreaM2 and start/finish epoch seconds for all records.
- Existing history consumers share an internal TaskHistoryRequestGate because the assembler is singleton. Move ownership to core:model with a delegating compatibility wrapper.
- Lifetime statistics from retained history are not immutable device lifetime counters. Deleted records cannot be reconstructed.
- Daily duration can be clipped at local midnight; recorded elapsed duration includes pauses.
