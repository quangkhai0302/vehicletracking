# Research — Phân quyền người dùng và cổng tài xế

Không cần research bên ngoài. Thiết kế dùng session HTTP của Spring Security đã thêm vào backend và cookie phiên của trình duyệt; không giới thiệu provider OAuth/SSO mới. Các quyết định dưới đây dựa trên source repository và được đối chiếu trong `survey.md`.

Session cookie được chọn vì frontend hiện là cùng một ứng dụng nội bộ và không cần expose bearer token trong JavaScript. Đây là quyết định triển khai của feature, không phải yêu cầu từ nhà cung cấp bên ngoài.
