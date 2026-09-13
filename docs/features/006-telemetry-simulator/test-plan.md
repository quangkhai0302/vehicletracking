# Kiểm tra 006

- Decoder: example HERE, 2D/3D, invalid/truncated/overflow, bounds; nhiều segment/section trên một leg, dwell, zero duration, tuyến vòng A→B→A, đầu/cuối và ETA.
- Ingestion: validation, source, duplicate retry/collision, old/equal/future timestamps, inactive/wrong vehicle/terminal trip, GPS vs simulator, latest/history persist.
- Simulator: begin/resume/pause/speed, physical speed vs multiplier, complete/stop/reset, idempotent reset, corrupt geometry, recover restart paused, concurrent play/tick, manual lifecycle stops scheduler.
- SSE: initial and reconnect snapshots, two subscribers committed position, cleanup, no progression without clock advance in pause.
- UI: realtime source/freshness/offline, trip selection/route, two tabs see same run and position, pause/resume/speed/reset, stale detail not replacing selection, errors and disabled controls, mobile no overflow, regressions trạm/tuyến/xe/chuyến.
- Commands: Maven full suite; frontend lint/tsc/build; Edge Playwright scripts in local docs. Results and environmental limits recorded in verification.md.
