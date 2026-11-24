# Sửa lỗi và kiểm thử lại – SubTrack (Tuần 5)

Mục tiêu: lỗi đã sửa phải hết, và việc sửa không làm hỏng chỗ khác.

## Quy trình cho mỗi lỗi
1. **Tạo nhánh sửa:** người phụ trách tạo nhánh `sua/BUG-xx` từ nhánh đang kiểm thử.
2. **Viết test tái hiện lỗi trước khi sửa**, nếu lỗi nằm ở `domain/` hoặc `data/` (test JVM, Robolectric cho phần CSDL).
   - Chạy thử để thấy test hỏng: `gradlew.bat testDebugUnitTest --tests "*TênTest*"`.
   - Lỗi chỉ thấy trên giao diện thì bỏ qua bước này và ghi "kiểm thủ công".
3. **Sửa ở gốc nguyên nhân:**
   - Tìm mọi nơi gọi tới hàm bị sửa.
   - Commit có mã lỗi, ví dụ `Sửa BUG-03: không cho đổi tên bộ dữ liệu thành chuỗi trống`.
4. **Chạy test tự động:** `gradlew.bat testDebugUnitTest lintDebug` phải qua hết.
5. **Chuyển trạng thái:** đổi lỗi sang `Chờ kiểm thử lại`, ghi commit sửa vào bảng lỗi.
6. **Kiểm thử lại:**
   - Người phát hiện cài bản đã sửa và chạy lại đúng test case đã hỏng, trên đúng thiết bị và điều kiện cũ.
   - Đạt thì chuyển sang bước hồi quy. Không đạt thì đổi lỗi thành `Mở lại`.
7. **Hồi quy:** chạy các test case liên quan theo bảng bên dưới.
   - Đạt hết thì đổi lỗi thành `Đã đóng`.
   - Nếu phát sinh lỗi mới thì ghi lỗi mới, kèm "phát sinh do sửa BUG-xx".

## Chọn test case hồi quy theo phần code bị sửa
| Phần code sửa | Chạy lại |
|---|---|
| `ui/edit`, `BillingMath.parseAmount` / `isValidUrl` | Toàn bộ F02, TC-F05-02..04, TC-F11-06 |
| `BillingMath` (tính kỳ, tính tháng) | F05, F07, TC-F09-03..10 |
| `SubscriptionRepository`, `SubTrackDatabase` | F02, F03, F05, F06, TC-F13-03, TC-F14-07, TC-F14-08 |
| `DatasetRegistry`, chuyển bộ | Toàn bộ F13, TC-F11-02..04, TC-F09-08 |
| `DatasetCsv`, `QrChunks`, `QrGif` | Toàn bộ F11, F12 |
| `RatesRepository`, `RatesBar` | F08, TC-F07-02, TC-F07-07, TC-F14-06 |
| `reminder/` | Toàn bộ F09 |
| Giao diện (bố cục, chuỗi) | Test case của màn đó, và TC-F14-01..05 cho màn đó |

## Vòng hồi quy cuối (trước khi trình bày)
- Chạy toàn bộ test case ưu tiên Cao trên D1 và D4.
- Chạy toàn bộ F14.
- Chạy lại test tự động.

## Nhật ký các vòng kiểm thử
| Vòng | Ngày | Commit | Test tự động (đạt / tổng) | Lint | Test case chạy | Đạt | Không đạt | Lỗi mới | Ghi chú |
|---|---|---|---|---|---|---|---|---|---|
| 0 – ban đầu | | f59d732 | / 74 | | 135 | | | | |
| 1 – sau sửa lỗi | | | | | | | | | |
| Cuối – hồi quy | | | | | | | | | |

## Kiểm thử lại từng lỗi
| Mã lỗi | Commit sửa | Test case chạy lại | KQ | Test case hồi quy | KQ | Lỗi mới phát sinh | Người kiểm | Ngày |
|---|---|---|---|---|---|---|---|---|
| | | | | | | | | |
