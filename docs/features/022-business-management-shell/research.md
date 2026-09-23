# Research 022 — Business Management Shell

Ngày truy cập: 2026-09-18. Chỉ dùng nguồn chính thức/sơ cấp.

## Dữ kiện

- React gắn state với vị trí component trong render tree; shell ổn định qua route change giúp state cấp shell không reset ngoài ý muốn. Nguồn: https://react.dev/learn/preserving-and-resetting-state
- React Router hỗ trợ layout routes, URL thật và `NavLink`; `NavLink` cung cấp active/current-page semantics. Nguồn: https://reactrouter.com/start/declarative/routing, https://reactrouter.com/start/declarative/navigating, https://reactrouter.com/how-to/accessibility
- Browser history cần `pushState`/`popstate` semantics để Back/Forward khôi phục view; deep link SPA cần server fallback về entry HTML. Nguồn: https://developer.mozilla.org/en-US/docs/Web/API/History_API/Working_with_the_History_API
- Navigation ứng dụng nên dùng `<nav>` và link thường, không dùng ARIA menu nếu không triển khai keyboard model của menu. Active link dùng `aria-current="page"`. Nguồn: https://www.w3.org/WAI/ARIA/apg/patterns/disclosure/examples/disclosure-navigation/
- Mobile modal drawer chỉ dùng `aria-modal` nếu thực sự khóa nền; Escape đóng và focus trở về trigger. Nguồn: https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/

## Kết luận áp dụng

- Dùng `BrowserRouter`, `NavLink`, route layout ổn định và path nghiệp vụ rõ ràng; không dùng hash routing.
- Shell tồn tại ngoài page outlet. Map chỉ mount ở route cần bản đồ.
- Mobile navigation dùng overlay drawer có close button, Escape và focus return; desktop dùng sidebar tĩnh.
- Form/list state thuộc page; đổi resource/page reset có chủ đích.
- Production phải kiểm tra direct request tới deep link trả `index.html`.
