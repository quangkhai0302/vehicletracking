# Review — 003-route-eta

## Thông tin review

| Thuộc tính | Giá trị |
|---|---|
| Feature ID | `003-route-eta` |
| Reviewer | Codex |
| Implementer | Gemini |
| Thời điểm | 2026-09-04 11:14:17 Asia/Ho_Chi_Minh |
| Commit/diff được review | `0cc54fe` + worktree feature 003 chưa commit |
| Requirement/Spec/Test-Plan/Plan/Evidence version | 2026-09-04 |
| Lần review | 4 — re-review sau khi Gemini xử lý `REV-005`, `REV-006` |

## Kết luận

```text
APPROVED
```

**Lý do ngắn:** Mọi finding bắt buộc (`REV-001`..`REV-006`) đã được xử lý. Diff source tuân thủ Plan, telemetry foreign được kiểm chứng end-to-end bằng STOMP client ngoài bundle → browser callback → UI không đổi, và frontend hiện chỉ còn bốn warning baseline đã được phân loại chính xác. Không còn Requirement MUST ở trạng thái `INCONCLUSIVE`.

## Phạm vi và phương pháp re-review

- Đã đọc lại toàn bộ tài liệu feature, Evidence Matrix, EVD-010..EVD-015, test-plan TC-007..TC-011, review trước và git diff hiện tại.
- Đã kiểm tra source application: `websocket.ts` giữ contract subscribe-only; `App.tsx` derive `matchingTelemetry` tại lines 25-31 và chỉ truyền dữ liệu khớp Trip cho `MapComponent`/`TimelinePanel` (`lines 429-460`). Không còn `publishTelemetry`, `window.wsService` hoặc topic/endpoint mới.
- Đã kiểm tra evidence isolation: `artifacts/send-foreign-telemetry.js`, sender log, browser log và screenshot. Script STOMP kết nối `/ws-raw`, nhận `CONNECTED`, gửi foreign payload `tripId:99999`; browser log sau đó ghi callback của `App.tsx` đã bỏ đúng payload đó; screenshot xác nhận Trip 1 không đổi.
- Đã kiểm tra EVD-014 với diff `App.tsx`: `setTelemetry` trong effect cũ đã bỏ, thay bằng derived value; lint chỉ còn 4 warnings baseline.
- Đã chạy lại `npm run lint`, `npx tsc --noEmit`, Vite build bằng Node 24.16.0, `git diff --check` và Node syntax check cho script artifact. Không chạy lại backend suite vì source backend không đổi trong lần re-review; EVD-010 vẫn có full-suite artifact `73` tests, 0 failures/errors đã được đối chiếu ở review trước.
- Không thay đổi source code ứng dụng; chỉ cập nhật review này.

## Trạng thái findings

### REV-001 — Simulator kết thúc khi check-in còn `PENDING`

**Severity ban đầu:** `HIGH`  
**Trạng thái:** `RESOLVED`

`SimulatorService.java:249-275` duyệt waypoint trung gian và chỉ terminal khi toàn bộ check-in hoàn thành. `SimulatorServiceTest.java:305-355,417-448`; `EVD-012` và Maven artifact xác nhận các nhánh pending-at-end/multi-step.

**Traceability:** `REQ-003/REQ-005 → SPEC-003/BR-003,BR-004 → TC-005 → EVD-012 → PASS`.

### REV-002 — Telemetry Trip khác có thể ghi đè state trước khi có `currentTrip`

**Severity ban đầu:** `HIGH`  
**Trạng thái:** `RESOLVED`

`App.tsx:91-99` bỏ mọi telemetry không có `activeTrip` hoặc sai `tripId`; derived `matchingTelemetry` tại lines 25-31 và `TimelinePanel` guard tiếp tục ngăn display foreign data.

**Traceability:** `REQ-004 → SPEC-004/BR-006 → TC-007 → EVD-013 → PASS`.

### REV-003 — Thiếu coverage/evidence cho completion động và isolation

**Severity ban đầu:** `MEDIUM`  
**Trạng thái:** `RESOLVED`

`SimulatorServiceTest#tickSingleSimulation_NonTerminalTick_DerivesCompletionEtaFromFinalStop` assert completion ETA/time của telemetry non-terminal bằng stop cuối; EVD-011..EVD-014 có test/manual/command artifact tương ứng.

**Traceability:** `REQ-003/REQ-005 → SPEC-003..SPEC-005 → TC-003,TC-005,TC-008 → EVD-011,EVD-012,EVD-014 → PASS`.

### REV-004 — Hook phát telemetry giả trong production WebSocket service ngoài Plan

**Severity ban đầu:** `MEDIUM`  
**Trạng thái:** `RESOLVED`

`vehicletracking-frontend/src/services/websocket.ts:1-102` không còn publish API hoặc assignment lên `window`; `git diff --name-only` không còn file này. Script STOMP phục vụ evidence nằm trong `docs/features/.../artifacts`, không được bundle vào ứng dụng.

**Traceability:** `REQ-005/REQ-006 → SPEC-005/BR-005, SPEC-006 → TC-007,TC-011 → EVD-013,EVD-015 → PASS`.

### REV-005 — EVD-013 không chứng minh payload Trip khác đã tới dashboard

**Severity ban đầu:** `MEDIUM`  
**Trạng thái:** `RESOLVED`

EVD-013 hiện trỏ đầy đủ tới:

- `artifacts/send-foreign-telemetry.js`: script độc lập, syntax check exit `0`.
- `artifacts/tc007-sender-foreign-telemetry.log`: STOMP `CONNECTED`, `SEND /topic/telemetry` cho `tripId:99999`, script exit `0`.
- `artifacts/tc007-browser-isolation.log`: dashboard đã nhận callback và ghi `[REALTIME ISOLATION] ... payload.tripId=99999, activeTrip.id=1`.
- `artifacts/tc007_04_trip_isolation_other_trip_ignored.png`: UI của Trip 1 vẫn giữ biển số, vận tốc, hướng và lịch trình cũ.

Chuỗi sender → broker → browser callback → UI chứng minh delivery và rejection thực tế, không cần hook production.

**Traceability:** `REQ-004/REQ-006 → SPEC-004/BR-006 → TC-007 step 4 → EVD-013 → PASS`.

### REV-006 — EVD-014 phân loại sai warning mới là warning baseline

**Severity ban đầu:** `MEDIUM`  
**Trạng thái:** `RESOLVED`

`App.tsx` không còn `setTelemetry` trong effect đồng bộ Trip; `matchingTelemetry` được derive trong render. EVD-014 và `frontend-verification.log` hiện ghi đúng bốn warnings baseline: `IncidentModal`, hai warning `MapComponent`, và `App.tsx:83`. Lần chạy lại của Codex khớp chính xác: 4 warnings, 0 errors.

**Traceability:** `REQ-006 → SPEC-006 → TC-010 → EVD-014 → PASS`.

## Kiểm tra Requirement

| Requirement/AC | Spec/Test | Implementation/Evidence đã kiểm tra | Kết quả |
|---|---|---|---|
| REQ-001 / AC-REQ-001-01 | SPEC-001, BR-001; TC-001/002 | `TripService`, `TripControllerTest`; EVD-010 | PASS |
| REQ-002 / AC-REQ-002-01/02 | SPEC-002, BR-002; TC-003/004 | `SimulatorService#calculateEtas`; EVD-011 | PASS |
| REQ-003 / AC-REQ-003-01 | SPEC-003, BR-003/004; TC-005/006 | completion/geofence/simulator; EVD-012 | PASS |
| REQ-004 / AC-REQ-004-01 | SPEC-004, BR-006; TC-007 | `App`, `TimelinePanel`; sender/browser/UI artifacts EVD-013 | PASS |
| REQ-005 / AC-REQ-005-01 | SPEC-005, BR-004/005/007; TC-004/006/008 | Clock, DTO additive, completion/type checks; EVD-011/012/014 | PASS |
| REQ-006 / AC-REQ-006-01 | SPEC-006; TC-009..011 | test/log/manual/frontend verification artifacts; EVD-010/013/014/015 | PASS |

## Kiểm tra Plan, regression và architecture

| Hạng mục | Kết quả | Ghi chú |
|---|---|---|
| Plan Step 1, 2, 4 | PASS | Backend implementation/tests và EVD-010..EVD-012 đã được kiểm tra ở review trước; không có backend diff mới. |
| Plan Step 3 | PASS | App derives and passes matching telemetry to both map/timeline; frontend types/API remain additive. |
| Plan Step 5 | PASS | Artifacts có commands/log/screenshot thật, mọi EVD Matrix row là PASS. |
| WebSocket/public contract | PASS | Không có endpoint/topic/client API production mới; existing subscription cleanup giữ nguyên. |
| Dependency/schema/API REST | PASS | Không có dependency, migration, hay REST endpoint mới. |
| New source issues/regression | Không phát hiện | Console diagnostic ở `App.tsx:95` chỉ ghi khi nhận foreign telemetry; không thay contract/state và hỗ trợ trace manual verification. |

## Verification do Codex chạy lại

| Command | Working directory | Exit code | Kết quả | Thời điểm |
|---|---|---:|---|---|
| `npm run lint` | `vehicletracking-frontend` | 0 | PASS, 4 warnings baseline/0 errors | 2026-09-04 11:13 +07 |
| `npx tsc --noEmit` | `vehicletracking-frontend` | 0 | PASS | 2026-09-04 11:13 +07 |
| `/home/khainq/.nvm/versions/node/v24.16.0/bin/node node_modules/vite/bin/vite.js build` | `vehicletracking-frontend` | 0 | PASS, 1831 modules transformed | 2026-09-04 11:13 +07 |
| `git diff --check` | repository root | 0 | PASS, không output | 2026-09-04 11:13 +07 |
| `/home/khainq/.nvm/versions/node/v24.16.0/bin/node --check docs/features/003-route-eta/artifacts/send-foreign-telemetry.js` | repository root | 0 | PASS | 2026-09-04 11:14 +07 |

## Kết luận cuối cùng

Feature đáp ứng traceability hoàn chỉnh:

```text
Requirement → Spec → Test-Plan → Implementation → Evidence → Review
```

Mọi Requirement MUST đều có Evidence PASS; toàn bộ findings bắt buộc đã đóng, không có regression hoặc thay đổi ngoài phạm vi cần xử lý. Feature `003-route-eta` được **APPROVED**.

## Checklist đóng review

- [x] Git diff mới và source contract đã được kiểm tra.
- [x] `REV-001`..`REV-006` đã được xác minh và đóng.
- [x] Evidence Matrix, sender/browser/UI artifacts và verification logs đã đối chiếu.
- [x] Lint, type-check, production build, diff hygiene và script syntax đã chạy lại.
- [x] Không sửa source code ứng dụng.
- [x] Không còn Requirement quan trọng `INCONCLUSIVE` hoặc finding bắt buộc `OPEN`.
