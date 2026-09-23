# Research — 032

Ngày truy cập: 2026-09-22. Researcher Luna/medium chỉ đọc; main agent kiểm tra bổ sung package Vue plugin, Lucide, lint và visual testing. Đây là dữ kiện bên ngoài và quyết định đề xuất, không phải bằng chứng migration đã chạy.

## Nguồn và kết luận

| Nguồn chính thức | Dữ kiện sử dụng | Ảnh hưởng thiết kế |
|---|---|---|
| [Vue TypeScript overview](https://vuejs.org/guide/typescript/overview.html) | SFC hỗ trợ TypeScript; `vue-tsc` kiểm tra template/script; Vite build không thay thế type-check | Vue 3, Composition API, `<script setup lang="ts">`; thêm typecheck riêng |
| [Vite Vue plugin package](https://github.com/vitejs/vite-plugin-vue/blob/main/packages/plugin-vue/package.json) | Source main lúc khảo sát khai báo plugin 6.0.9, peer Vite bao gồm `^8.0.0`, Vue `^3.2.25`, Node `^20.19.0 || >=22.12.0` | Có hướng tương thích Vite 8/Node 24; vẫn xác minh bản published và chạy spike trước khi khoá dependency |
| [Vue advanced reactivity](https://vuejs.org/api/reactivity-advanced.html) | `shallowRef`/`markRaw` hỗ trợ đối tượng ngoài hệ reactive, tránh deep conversion | Không bọc Leaflet map/layer/marker, DOM, EventSource vào deep reactive |
| [Vue watchers](https://vuejs.org/guide/essentials/watchers.html), [lifecycle](https://vuejs.org/api/composition-api-lifecycle.html) | Watcher tạo đồng bộ trong setup tự dừng theo component; async work cần cleanup riêng; `onWatcherCleanup` có giới hạn đồng bộ và yêu cầu Vue 3.5+ | Dùng watcher explicit dependency, AbortController/request identity và `onUnmounted`; không dịch useEffect máy móc |
| [Vue Router guards](https://router.vuejs.org/guide/advanced/navigation-guards.html), [history mode](https://router.vuejs.org/guide/essentials/history-mode.html) | Router có guard và HTML5 history; server cần fallback cho deep link | Giữ URL; port auth/role redirect, bảo toàn fallback SPA của Caddy |
| [vue-tsc package](https://raw.githubusercontent.com/vuejs/language-tools/master/packages/tsc/package.json) | Peer TypeScript range không thay thế kiểm chứng tích hợp | TypeScript 7 đang dùng phải qua spike thực tế với vue-tsc/parser; chưa tuyên bố tương thích chỉ từ range |
| [Oxlint support](https://oxc.rs/docs/guide/usage/linter), [plugins](https://oxc.rs/docs/guide/usage/linter/plugins) | Oxlint nhận `.vue` nhưng lint script block, không đủ để chứng minh template đã lint | Giữ lint script nhanh; bổ sung ESLint Vue template, bỏ rule React sau cutover |
| [eslint-plugin-vue guide](https://eslint.vuejs.org/user-guide/) | Có flat config essential/recommended; Vue parser và TypeScript parser phối hợp, không thay parser SFC bằng TS parser | Thêm lint template correctness; tránh autoformat hàng loạt làm sai whitespace giao diện |
| [Lucide Vue](https://lucide.dev/guide/vue), [getting started](https://lucide.dev/guide/vue/getting-started) | Package Vue chính thức hiện dùng `@lucide/vue`; tài liệu cũ `lucide-vue-next` không phải mặc định hiện tại | Ưu tiên `@lucide/vue`, đối chiếu từng SVG/export/alias/size/stroke với bản React đang khoá; không đổi bộ icon |
| [Playwright visual comparisons](https://playwright.dev/docs/test-snapshots) | Screenshot baseline phụ thuộc môi trường render; hỗ trợ so sánh ảnh | Cố định browser/OS/font/viewport/fixture trước so sánh; kiểm tra ảnh cả dev và production |
| [Vue testing guide](https://vuejs.org/guide/scaling-up/testing.html) | Phân biệt unit/component/E2E; giới thiệu Vitest và Vue Test Utils cho Vue | Test lifecycle/composable không chỉ chụp ảnh; thiết lập runner explicit trong Plan |

## Quyết định đề xuất

1. Giữ SPA Vite, TypeScript và Leaflet; đổi React sang Vue 3 + Vue Router tương thích Vue 3. Không thêm Nuxt/SSR/UI kit/Tailwind hoặc map wrapper trong cùng migration.
2. State page giữ ở component/composable; auth app-wide dùng state factory + provide/inject typed. Chưa cần Pinia: đây là lựa chọn giảm thay đổi, không phải khẳng định Vue không cần store.
3. Giữ CSS global và DOM contract. Không tự chuyển style sang scoped CSS/CSS Modules vì selector Leaflet, descendant selector và cascade cần giữ nguyên.
4. Đề xuất Vitest + Vue Test Utils cho composable/component lifecycle và Playwright cho UI/visual/HTTP contract; tất cả là dev dependency được thiết lập trong Plan, chưa có sẵn thành runner chuẩn của frontend.
5. Chọn phiên bản ổn định được khoá trong package-lock sau spike. Không cài `latest` mù, không dùng `--force`/`--legacy-peer-deps` để bỏ qua xung đột. Nếu TypeScript 7 không được vue-tsc hỗ trợ thực tế, báo blocker và xin duyệt phương án đổi compiler; không âm thầm downgrade.

## Rủi ro / chưa kiểm chứng

- Ở lượt planning chưa chạy bộ toolchain. Sau khi được duyệt, đã chạy spike Vue/plugin/vue-tsc với TS7 và TS6: xem cập nhật dưới đây; bộ lint/unit/browser runner đầy đủ vẫn chưa qua G1.
- Bộ icon Vue có thể khác tên export/class mặc định hoặc path SVG theo phiên bản; phải có inventory và kiểm tra parity, không thay icon “gần giống”.
- Cùng CSS không đảm bảo cùng render: template whitespace, root wrapper, `v-if`/`v-show`, attribute fallthrough, form binding và Teleport đều có thể đổi layout/hành vi.
- Visual test không thay thế kiểm tra provider/backend thật; map tile động không thể dùng ảnh mạng không cố định để kết luận sai khác do framework.

## Cập nhật compatibility sau phê duyệt — 2026-09-22

Thực nghiệm local: Vue 3.5.43 + plugin-vue 6.0.9 + Vite 8.2.2 + vue-tsc 3.3.11 cài thành công. Với TypeScript 7.0.2, Vite build pass nhưng vue-tsc lỗi `ERR_PACKAGE_PATH_NOT_EXPORTED` ở `typescript/lib/tsc`; bản TS6.0.3 đối chứng typecheck/build pass. Đây chỉ là SFC fixture, chưa là ứng dụng đã migrate.

[Issue chính thức Vue #6124](https://github.com/vuejs/language-tools/issues/6124) ghi nhận cùng lỗi với TS7; [Microsoft TypeScript 7 announcement](https://devblogs.microsoft.com/typescript/announcing-typescript-7-0/#running-side-by-side-with-typescript-6-0) giải thích giới hạn API và cung cấp compatibility package TypeScript6. `vue-tsgo` tra được trên npm không phải CLI chính thức của Vue; không dùng dự án bên thứ ba này để lách G1.

Đề xuất đơn giản nhất: khoá compiler frontend ở TypeScript 6.0.3 trong giai đoạn Vue migration. Cần người dùng duyệt trước thay package ứng dụng. Phương án side-by-side TS7/TS6 cũng có nhưng thêm cấu hình/đường typecheck, chưa cần nếu chỉ có một frontend; chưa áp dụng phương án nào.
