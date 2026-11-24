# Bảng lỗi – SubTrack (Tuần 5)

Mỗi lỗi một dòng. Mức độ theo bảng ở mục 5 của [01_ke_hoach_kiem_thu.md](01_ke_hoach_kiem_thu.md). Ảnh chụp, video và logcat lưu vào `testplan/bang-chung/BUG-xx/`.

## Trạng thái
`Mở` → `Đang sửa` → `Chờ kiểm thử lại` → `Đã đóng`. Nếu kiểm thử lại vẫn hỏng thì chuyển về `Mở lại`.

Hai trạng thái khác:
- `Không sửa`: cả nhóm đồng ý chấp nhận lỗi, phải ghi lý do.
- `Trùng`: ghi mã lỗi gốc.

## Danh sách lỗi
| Mã | Test case | Mô tả | Cách tái hiện | Kết quả mong đợi / thực tế | Mức độ | Thiết bị / điều kiện | Người phát hiện | Người phụ trách | Trạng thái | Commit sửa | Ngày kiểm thử lại – KQ |
|---|---|---|---|---|---|---|---|---|---|---|---|
| BUG-01 | | | 1. …<br>2. …<br>3. … | Mong đợi: …<br>Thực tế: … | | | | | Mở | | |

### Ví dụ cách ghi (xoá khi nộp)
| Mã | Test case | Mô tả | Cách tái hiện | Kết quả mong đợi / thực tế | Mức độ | Thiết bị / điều kiện | Người phát hiện | Người phụ trách | Trạng thái | Commit sửa | Ngày kiểm thử lại – KQ |
|---|---|---|---|---|---|---|---|---|---|---|---|
| BUG-00 | TC-F04-07 | Sắp xếp theo số tiền không quy đổi tiền tệ | 1. Bộ "Test" có gói 59.000 ₫/tháng và 10 USD/tháng<br>2. Danh sách → Sắp xếp theo số tiền | Mong đợi: 10 USD (~260.000 ₫) đứng trên 59.000 ₫<br>Thực tế: gói USD nằm cuối | Trung bình | D1, E1 | TV2 | TV2 | Chờ kiểm thử lại | `abc1234` | 27/11 – Đạt |

*Dòng trên chỉ là ví dụ cách điền (commit và ngày là giả). Danh sách lỗi dự kiến tìm được nằm ở [05_bo_test_case_chon.md](05_bo_test_case_chon.md).*

## Tổng hợp (điền khi trình bày)
| Mức độ | Tổng | Đã đóng | Đang mở / sửa | Không sửa |
|---|---|---|---|---|
| Nghiêm trọng | | | | |
| Cao | | | | |
| Trung bình | | | | |
| Thấp | | | | |
| **Tổng** | | | | |

## Quy tắc ghi lỗi
- **Một lỗi, một dòng.** Hai hiện tượng khác nhau thì tách thành hai dòng.
- **Cách tái hiện:** viết đủ để người khác làm lại được. Ghi rõ bộ dữ liệu, giá trị đã nhập, thiết bị và điều kiện.
- **App crash:** đính kèm `adb logcat -d` hoặc đoạn stack trace.
- **Lỗi chỉ thấy trên một thiết bị hay điều kiện** (màn hình nhỏ, mất mạng…): ghi rõ, và ghi luôn là trên D1 vẫn bình thường.
- **Người phụ trách:** là người sửa, thường là người viết phần code đó. Người phụ trách không tự kiểm thử lại lỗi của mình; người phát hiện kiểm lại.
