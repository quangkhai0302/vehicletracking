# Test plan

| AC | Kiểm tra |
|---|---|
| AC1 | Unit 10→37, service frame/telemetry/progress với rate>1,5, flow chậm/blocked/fallback. |
| AC2 | Node native test buffer qua biên 1s, góc cua, mẫu trùng, mất stream; tsc/lint/build. Manual kéo map/theo xe/pause/replay. |
| AC3/5 | Service preview không ghi, remap điểm dẫn đường không thành trạm, PUT conflict, validation và copy; integration migration khi Docker có sẵn. |
| AC4 | Motion prefix + detour, khôi phục revision, replay attempt, check-in geometry detour; API route đang chạy. |

Full Maven test Java26; ghi rõ hạn chế môi trường nếu có. Không gọi HERE thật trong unit test.

