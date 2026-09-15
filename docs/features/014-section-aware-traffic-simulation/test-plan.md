# Test plan

| AC | Mức test | Kịch bản | Kết quả |
|---|---|---|---|
| 1 | Unit | Baseline 10s, traffic 50s | Rate bằng 0.2. |
| 1 | Unit | Traffic quá nhanh | Rate bị giới hạn 1.5. |
| 3 | Unit | Traffic cực chậm | Rate bị giới hạn 0.05, không thành free-flow. |
| 2 | Unit | Flow tại route point | Matcher trả khoảng cách 0 để chọn flow gần xe. |
| 5 | Maven | Policy và matcher | Không có failure. |
