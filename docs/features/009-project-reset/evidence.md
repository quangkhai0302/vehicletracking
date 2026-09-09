# Evidence — Project reset

| ID | Trạng thái | Nguồn/cách kiểm chứng | Actual |
|---|---|---|---|
| `EVD-001` / `TC-001` | PASS | `npm run lint` | OxcLint kết thúc với exit code 0. |
| `EVD-002` / `TC-002` | PASS | `npm run build` bằng Node.js 24.16.0 | Vite build thành công, 16 module được transform. |
| `EVD-003` / `TC-003` | PASS | `bash ./mvnw test` bằng Java 26.0.1 | 1 test chạy, 0 failure, 0 error. |
| `EVD-004` / `TC-004` | PASS | `find` file `.env` và `rg` dependency/config tích hợp trong runtime | Không còn kết quả. |
| `EVD-005` / `TC-005` | PASS | `find` toàn bộ `src` | Frontend còn 4 file source; backend còn application, YAML và 1 context test. |

Lưu ý: Vite 8 không chạy với Node.js 18 của hệ thống. Repository đã khai báo
`node >=22.12.0` và `.nvmrc` chọn Node.js 24.
