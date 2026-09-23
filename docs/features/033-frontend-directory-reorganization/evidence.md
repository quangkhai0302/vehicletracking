# 033 — Bằng chứng Kiểm thử và Xác minh Thực tế (Evidence)

Ngày thực hiện: 23/09/2026.
Môi trường thực thi: Node.js v24.16.0 (npm v11.13.0), Linux.

---

## 1. Bằng chứng kiểm tra tĩnh (Linting & TypeScript Typecheck)

### Lệnh 1: Linting
```bash
export NVM_DIR="$HOME/.nvm" && [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh" && nvm use 24 && npm run lint
```
**Kết quả:**
```text
Now using node v24.16.0 (npm v11.13.0)

> vehicletracking-frontend@0.0.0 lint
> oxlint && eslint "**/*.vue" --max-warnings 0

Found 0 warnings and 0 errors.
Finished in 42ms on 144 files with 71 rules using 12 threads.
```
*Trạng thái: PASS (0 warning, 0 error).*

---

### Lệnh 2: TypeScript Typecheck
```bash
export NVM_DIR="$HOME/.nvm" && [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh" && nvm use 24 && npm run typecheck
```
**Kết quả:**
```text
Now using node v24.16.0 (npm v11.13.0)

> vehicletracking-frontend@0.0.0 typecheck
> vue-tsc --noEmit
```
*Trạng thái: PASS (Exit code 0, không có bất kỳ lỗi biên dịch type nào).*

---

## 2. Bằng chứng kiểm thử đơn vị (Unit Tests)

### Lệnh:
```bash
export NVM_DIR="$HOME/.nvm" && [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh" && nvm use 24 && npm run test:unit
```
**Kết quả:**
```text
 ✓ tests/unit/tracking-panels.test.ts (2 tests) 113ms
 ✓ tests/unit/toolchain.test.ts (1 test) 202ms
 ✓ tests/unit/auth.test.ts (9 tests) 138ms
 ✓ tests/unit/fleet.test.ts (10 tests) 195ms
 ✓ tests/unit/map-state.test.ts (12 tests) 252ms
 ✓ tests/unit/cutover.test.mjs (4 tests) 29ms
 ✓ tests/unit/business-pages.test.ts (7 tests) 445ms
 ✓ tests/unit/routes.test.ts (5 tests) 463ms
 ✓ tests/unit/router.test.ts (4 tests) 205ms
 ✓ tests/unit/map-components.test.ts (12 tests) 541ms
 ✓ tests/unit/http-contract.test.ts (5 tests) 19ms
 ✓ tests/unit/page-workflows.test.ts (6 tests) 417ms
 ✓ tests/unit/preview-server.test.mjs (5 tests) 10ms
 ✓ tests/unit/map-integration.test.ts (3 tests) 1493ms

 Test Files  14 passed (14)
      Tests  85 passed (85)
   Start at  09:53:24
   Duration  4.92s
```
*Trạng thái: PASS (14/14 test suites, 85/85 tests passed 100%).*

---

## 3. Bằng chứng kiểm thử chuyển động xe (Vehicle Motion Native Test)

### Lệnh:
```bash
export NVM_DIR="$HOME/.nvm" && [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh" && nvm use 24 && npm run test:motion
```
**Kết quả:**
```text
> vehicletracking-frontend@0.0.0 test:motion
> node --test tests/vehicleMotion.test.ts

✔ buffer crosses snapshot boundaries continuously instead of stopping after 900ms (1.104932ms)
✔ irregular samples interpolate by time and hold when disconnected (0.177269ms)
✔ interpolated route samples retain progress for a delayed snapshot bridge (0.131708ms)
✔ route interpolation follows a corner rather than drawing a diagonal at 5x/10x (0.825535ms)
✔ heading takes the short turn through north and a repeated position stays still (0.15135ms)
ℹ tests 5
ℹ suites 0
ℹ pass 5
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 106.344715
```
*Trạng thái: PASS (5/5 assertions passed).*

---

## 4. Bằng chứng đóng gói sản xuất (Production Build)

### Lệnh:
```bash
export NVM_DIR="$HOME/.nvm" && [ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh" && nvm use 24 && npm run build
```
**Kết quả:**
```text
> vehicletracking-frontend@0.0.0 build
> vite build

vite v8.2.2 building client environment for production...
✓ 2018 modules transformed.
dist/index.html                                  0.47 kB │ gzip:  0.28 kB
dist/assets/RouteInspectionLayer-DZ2T5WZb.css    2.80 kB │ gzip:  0.97 kB
dist/assets/MapComponent-2MReJumJ.css           22.16 kB │ gzip:  5.00 kB
dist/assets/index-BcmlkbS4.css                 238.48 kB │ gzip: 43.86 kB
dist/assets/SimulationFleetLayer-CVo-HJVX.js     2.25 kB │ gzip:  1.20 kB
dist/assets/SimulationRoutesLayer-Ca3IylNT.js    2.83 kB │ gzip:  1.39 kB
dist/assets/SimulationFleetList-sPhMXOVw.js      3.42 kB │ gzip:  1.56 kB
dist/assets/RouteInspectionLayer-B5vW6nAb.js    13.47 kB │ gzip:  5.48 kB
dist/assets/index-Dl1cURhx.js                  246.72 kB │ gzip: 79.77 kB
dist/assets/MapComponent-CZZDq71l.js           287.97 kB │ gzip: 87.04 kB

✓ built in 954ms
```
*Trạng thái: PASS (Build thành công, đầy đủ chunks, không warning).*
