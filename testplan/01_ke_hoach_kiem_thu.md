# Kế hoạch kiểm thử – SubTrack (Tuần 5)

Phiên bản kiểm thử: nhánh `du-lieu`, commit `f59d732`. Nếu PR #3 và #4 được gộp trước khi chạy test thì dùng `main` sau khi gộp và ghi lại commit.

## 1. Mục tiêu
- Kiểm tra mọi chức năng của app, với dữ liệu nhập đúng, nhập sai, để trống và dữ liệu biên.
- Chạy trên máy ảo và máy thật, ở nhiều kích thước màn hình, và khi mất mạng.
- Ghi mọi lỗi tìm được vào bảng lỗi. Sửa xong thì chạy lại test case để chắc chắn không sinh lỗi mới.
- Sản phẩm để trình bày:
  - bảng test case có kết quả: [02_test_case.md](02_test_case.md)
  - danh sách lỗi và tình trạng xử lý: [03_bang_loi.md](03_bang_loi.md)
  - kết quả kiểm thử lại: [04_kiem_thu_lai.md](04_kiem_thu_lai.md)

## 2. Phạm vi

### Chức năng cần kiểm tra
| Mã | Chức năng | Màn hình / thành phần | Ưu tiên |
|---|---|---|---|
| F01 | Giới thiệu lần đầu (onboarding) | OnboardingScreen | Trung bình |
| F02 | Thêm / sửa gói đăng ký: tên, số tiền, tiền tệ, chu kỳ, ngày, nhắc, tag, nền tảng, liên kết, ghi chú | EditScreen | **Cao** |
| F03 | Xoá gói (lịch sử chi vẫn giữ) | EditScreen | **Cao** |
| F04 | Danh sách: tìm kiếm, lọc trạng thái / danh mục, sắp xếp | ListScreen | Cao |
| F05 | Chi tiết và xác nhận kỳ thanh toán: Đã trả, Bỏ qua, Hoãn, Huỷ, Kích hoạt lại, Hoàn tác | DetailScreen | **Cao** |
| F06 | Quản lý tag: tạo, sửa, xoá, trùng tên | OrganizeScreen | Trung bình |
| F07 | Báo cáo: chi theo tháng, theo danh mục, "Chi thực tế", đổi tiền tệ hiển thị | ReportsScreen | Cao |
| F08 | Tỷ giá: tự cập nhật mỗi ngày, nút Cập nhật, dùng bản lưu khi mất mạng | RatesBar, RatesRepository | Cao |
| F09 | Nhắc hạn và thông báo: quyền thông báo, thông báo thử, nút Đã trả / Hoãn trên thông báo | ReminderWorker, PaymentActionReceiver | Cao |
| F10 | Cài đặt: tiền tệ mặc định, bật / tắt nhắc, dữ liệu mẫu, xoá dữ liệu, chia sẻ báo cáo | SettingsScreen | Trung bình |
| F11 | Xuất / nhập CSV | SettingsScreen, ImportPreviewDialog | **Cao** |
| F12 | Mã QR: xuất QR động, lưu GIF, quét từ ảnh / GIF, quét bằng camera | QrExportScreen, QrScanScreen | Cao |
| F13 | Bộ dữ liệu: dùng, đổi tên, xoá, không ghi đè lẫn nhau | DatasetsScreen | **Cao** |
| F14 | Phi chức năng: kích thước màn hình, xoay màn hình, cỡ chữ lớn, mất mạng, khởi động lại app, giữ dữ liệu khi cập nhật bản cài | Toàn app | Cao |

### Ngoài phạm vi
- Hiệu năng với dữ liệu rất lớn (hơn 1.000 gói). Chỉ thử với 80 gói, bộ CSV đã có trong `temp/qr-test`.
- Bảo mật, và các phiên bản Android dưới 8.0 (minSdk 26).
- Giao diện tiếng Anh: app luôn ép tiếng Việt.

## 3. Loại kiểm thử
| Tầng | Cách làm | Công cụ | Ghi chú |
|---|---|---|---|
| Unit / tích hợp (tự động) | Chạy toàn bộ test JVM + lint | `gradlew.bat testDebugUnitTest lintDebug` (thư mục `android/`) | Hiện có 74 test: BillingMath, CSDL, thanh toán, tỷ giá, CSV, QR, GIF, bộ dữ liệu |
| Kiểm thử thủ công theo test case | Làm theo [02_test_case.md](02_test_case.md) | Máy ảo + máy thật | Mỗi chức năng có 4 nhóm: Đúng / Sai / Trống / Biên |
| Kiểm thử môi trường | Nhóm F14 | `adb shell wm size`, xoay máy, chế độ máy bay | Xem mục 4 |
| Kiểm thử lại + hồi quy | Sau mỗi lần sửa lỗi | Xem [04_kiem_thu_lai.md](04_kiem_thu_lai.md) | Bắt buộc trước khi đóng lỗi |

Test tự động thêm (đề xuất, làm khi sửa lỗi): mỗi lỗi sửa xong nên có một unit test tái hiện lỗi nếu nằm ở tầng `domain/` hoặc `data/`, để lần sau test tự bắt được.

## 4. Môi trường kiểm thử

### Thiết bị
| Mã | Thiết bị | Màn hình | Android | Dùng cho |
|---|---|---|---|---|
| D1 | Máy ảo `Medium_Phone_API_36.1` (mặc định) | 1080×2400, 420 dpi | 16 (API 36) | Toàn bộ test case |
| D2 | Cùng máy ảo, màn hình nhỏ: `adb shell wm size 720x1280` + `wm density 320` | ~4,6" | 16 | F02, F04, F05, F07, F14 |
| D3 | Cùng máy ảo, cỡ máy tính bảng: `adb shell wm size 1600x2560` + `wm density 320` | ~10" | 16 | F04, F07, F12, F14 |
| D4 | Máy thật của thành viên (ghi rõ hãng, model, Android) | — | ≥ 8.0 | Toàn bộ nhóm Cao + camera + thông báo |
| D5 | Máy thật thứ hai | — | ≥ 8.0 | Chiếu mã QR cho D4 quét (F12 camera) |

- Sau khi dùng D2 / D3 phải trả về như cũ: `adb shell wm size reset` và `adb shell wm density reset`.
- Máy ảo chỉ quick boot từ snapshot `default_boot`, và không dùng `-wipe-data`.

### Điều kiện chạy
| Mã | Điều kiện | Cách tạo |
|---|---|---|
| E1 | Có mạng | Mặc định |
| E2 | Mất mạng | Bật chế độ máy bay, hoặc `adb shell svc wifi disable` + `adb shell svc data disable` (bật lại bằng `enable`) |
| E3 | Mạng chập chờn: mất mạng giữa lúc đang cập nhật tỷ giá | Bấm Cập nhật rồi bật chế độ máy bay ngay |
| E4 | Xoay ngang | Bật tự xoay, xoay máy |
| E5 | Cỡ chữ lớn | `adb shell settings put system font_scale 1.3`, đặt lại về `1.0` |
| E6 | Giao diện tối | Cài đặt hệ thống → Hiển thị → Giao diện tối |
| E7 | Từ chối quyền | Từ chối quyền thông báo và quyền camera khi app hỏi |

### Dữ liệu kiểm thử
- Dữ liệu mẫu có sẵn trong app (Cài đặt → Thêm dữ liệu mẫu), chạy trên một bộ dữ liệu riêng tên "Test".
- `temp/qr-test/dataset_qr.csv`: 80 gói, 8 tag, 185 dòng lịch sử. Có tiếng Việt, ngoặc kép, dấu phẩy, xuống dòng.
- Tệp CSV hỏng tự tạo: thiếu cột, sai định dạng ngày, tệp trống, tệp không phải CSV.
- **Không thử trên bộ "Dữ liệu của tôi".** Tạo bộ "Test" để chạy test, xong thì xoá bộ đó.

## 5. Tiêu chí đạt yêu cầu

### Một test case Đạt khi
Kết quả thực tế khớp cột "Kết quả mong đợi", app không bị đóng đột ngột (crash / ANR), và dữ liệu không bị mất hay sai lệch.

### Một chức năng Đạt khi
Mọi test case của nó Đạt trên D1 và D4, và không còn lỗi mức Nghiêm trọng hay Cao đang mở.

### Cả đợt kiểm thử Đạt (tiêu chí kết thúc) khi
1. 100% test case ưu tiên Cao Đạt. ≥ 95% tổng số test case Đạt, và các ca còn lại có lỗi đã ghi nhận, mức Thấp hoặc Trung bình.
2. Không còn lỗi Nghiêm trọng / Cao ở trạng thái Mở hoặc Đang sửa.
3. `gradlew.bat testDebugUnitTest lintDebug` qua hết (74/74 trở lên, lint không lỗi).
4. Mọi lỗi đã sửa có kết quả kiểm thử lại Đạt, và vòng hồi quy cuối không phát sinh lỗi mới.

### Tiêu chí dừng tạm
Gặp lỗi làm mất dữ liệu, hoặc app crash ngay khi mở. Khi đó dừng, báo người phụ trách, và sửa trước rồi mới chạy tiếp.

### Mức độ nghiêm trọng của lỗi
| Mức | Định nghĩa | Ví dụ |
|---|---|---|
| Nghiêm trọng | Crash, mất hoặc hỏng dữ liệu, ghi nhầm sang bộ dữ liệu khác | Nhập CSV ghi đè bộ đang dùng |
| Cao | Chức năng chính sai kết quả và không có cách đi vòng | Tổng chi tháng tính sai |
| Trung bình | Chức năng phụ sai, hoặc có cách đi vòng | Sắp xếp sai thứ tự khi trùng ngày |
| Thấp | Giao diện, chính tả, bố cục | Chữ bị cắt trên màn hình nhỏ |

## 6. Rủi ro đã biết, cần kiểm kỹ
| Rủi ro | Test case liên quan |
|---|---|
| Nút trên thông báo có thể tác động nhầm bộ dữ liệu nếu thông báo đến sau khi chuyển bộ. App gỡ hết thông báo khi chuyển bộ, nhưng receiver không kiểm mã bộ | TC-F09-08, TC-F13-07 |
| Thao tác đang chạy dở khi chuyển bộ có thể báo lỗi "database closed" | TC-F13-08 |
| Số tiền "0" hiện được chấp nhận (nhóm thống nhất: phải lớn hơn 0) | TC-F02-12 |
| Đổi tên bộ dữ liệu thành chuỗi trống | TC-F13-05 |
| Camera quét QR động chưa từng thử trên máy thật | TC-F12-09 |
| Không tự lùi ngày: gói quá hạn lâu phải hiện "quá hạn" chứ không tự nhảy kỳ | TC-F05-11 |

## 7. Phân công và lịch
Thay "TV1…TV4" bằng tên thành viên.

| Việc | Người phụ trách | Thời gian |
|---|---|---|
| Chuẩn bị môi trường, dữ liệu test, chạy test tự động | TV1 | Ngày 1 |
| F01–F05 (gói đăng ký, thanh toán) | TV2 | Ngày 1–2 |
| F06–F10 (tag, báo cáo, tỷ giá, nhắc hạn, cài đặt) | TV3 | Ngày 1–2 |
| F11–F13 (CSV, QR, bộ dữ liệu) | TV4 | Ngày 1–2 |
| F14 trên D2, D3, D4, E2–E7 | Cả nhóm, chia theo thiết bị | Ngày 2 |
| Ghi lỗi, phân loại mức độ, giao người sửa | TV1 | Ngay khi phát hiện |
| Sửa lỗi + kiểm thử lại + hồi quy | Người được giao từng lỗi | Ngày 3–4 |
| Tổng hợp số liệu, chuẩn bị trình bày | TV1 | Ngày 5 |

## 8. Quy trình thực hiện
1. **Chuẩn bị:**
   - Cài bản debug: `gradlew.bat installDebug`.
   - Ghi commit, thiết bị và điều kiện vào đầu cột Kết quả.
   - Chạy test tự động và ghi kết quả vào [04_kiem_thu_lai.md](04_kiem_thu_lai.md), vòng 0.
2. **Chạy test case:** làm theo thứ tự trong [02_test_case.md](02_test_case.md) và điền các cột Kết quả, Thiết bị, Người test, Ngày.
3. **Khi một ca Không đạt:**
   - Chụp màn hình hoặc quay video.
   - Lấy log bằng `adb logcat -d > log.txt` nếu có crash.
   - Thêm một dòng vào [03_bang_loi.md](03_bang_loi.md) và ghi mã lỗi vào cột "Lỗi" của test case.
4. **Sửa lỗi:**
   - Mỗi lỗi sửa trên nhánh riêng `sua/<mã lỗi>`.
   - Nếu được thì kèm một unit test tái hiện lỗi.
   - Commit ghi mã lỗi.
5. **Kiểm thử lại:**
   - Chạy lại đúng test case đã hỏng.
   - Chạy tiếp vòng hồi quy theo [04_kiem_thu_lai.md](04_kiem_thu_lai.md).
   - Cập nhật trạng thái lỗi.
6. **Báo cáo:** tổng hợp số ca Đạt / Không đạt theo chức năng, số lỗi theo mức độ và trạng thái, rồi trình bày.
7. **Dọn dẹp:**
   - Xoá bộ dữ liệu "Test".
   - `wm size reset`, `wm density reset`, đặt `font_scale` về `1.0`, bật lại mạng.
   - Xoá các tệp test trong Downloads của máy ảo.
