# Kết quả kiểm thử – SubTrack (Tuần 5)

- **Ngày chạy:** 24/11/2026
- **Người chạy:** (điền tên thành viên)
- **Bản kiểm thử:**
  - nhánh `du-lieu`, commit `f59d732` cho vòng 0
  - cùng commit cộng các sửa lỗi chưa commit (danh sách ở mục 4) cho vòng 1
- **Bộ test case:** [../05_bo_test_case_chon.md](../05_bo_test_case_chon.md), chọn từ [../02_test_case.md](../02_test_case.md), cộng thêm vài ca liên quan.
- **Ảnh chứng minh:** [evidence/](evidence/).
  - Tên tệp có dạng `<mã test case>_<vòng>_<mô tả>_<dat|sai>.png`, với `v0` là trước khi sửa và `v1` là sau khi sửa.
  - Đuôi tên tệp (`dat` / `sai`) là kết quả của ca test case ở vòng đó.
- **Log test tự động:** [unit/](unit/).

## 1. Môi trường
| Mục | Giá trị |
|---|---|
| Thiết bị | Máy ảo `Medium_Phone_API_36.1` (Android 16, 1080×2400, 420 dpi). Đã **cold boot** (`-no-snapshot-load`) |
| Màn hình nhỏ (D2) | Cùng máy ảo, `wm size 720x1280` + `wm density 320`. Đã đặt lại sau khi test |
| Mất mạng (E2) | `svc wifi disable` + `svc data disable`. Đã bật lại sau khi test |
| Dữ liệu test | Bộ dữ liệu riêng "Test W5" nạp từ `temp/w5/w5_data.csv` (4 gói: VND, USD, ngày 31/01, tên có dấu). Bộ "Dữ liệu của tôi" (2 gói) không bị đụng tới: [trước](evidence/00_bo_goc_truoc_test.png), [sau](evidence/99_don_dep_xong.png) |
| Cách thao tác | `adb shell input` + `uiautomator dump`, qua script `temp/w5/ui5.py`. Ảnh chụp bằng `adb exec-out screencap` |
| Chưa làm | Máy thật (D4, D5) và camera thật: cần nhóm tự chạy với thiết bị của mình |

## 2. Bảng test case và kết quả

Tổng quan:
- **Vòng 0** (trước khi sửa): chạy 26 ca, **20 Đạt / 6 Không đạt**.
- **Vòng 1** (sau khi sửa): chạy lại 6 ca hỏng và 20 ca hồi quy, **26/26 Đạt**, không phát sinh lỗi mới.

| Mã | Loại | Nội dung | Kết quả mong đợi | Vòng 0 | Vòng 1 | Lỗi | Ảnh |
|---|---|---|---|---|---|---|---|
| TC-F02-05 | Sai | Số tiền `15,50` với VND | Báo số tiền không hợp lệ | Đạt | Đạt | | [v0](evidence/TC-F02-05_v0_vnd_phan_le_dat.png) · [v1](evidence/TC-F02-05_v1_vnd_phan_le_dat.png) |
| TC-F02-10 | Trống | Form trống → Lưu | Lỗi từng trường + "Vui lòng sửa các trường bị lỗi" | Đạt | Đạt | | [v0](evidence/TC-F02-10_v0_de_trong_dat.png) · [v1](evidence/TC-F02-10_v1_de_trong_dat.png) |
| TC-F02-11 | Biên | Tên 61 ký tự, rồi 60 ký tự | 61 báo "Tên tối đa 60 ký tự"; 60 hết lỗi | Đạt | Đạt | | [v0-61](evidence/TC-F02-11_v0_ten_61_dat.png) · [v0-60](evidence/TC-F02-11_v0_ten_60_dat.png) · [v1-61](evidence/TC-F02-11_v1_ten_61_dat.png) · [v1-60](evidence/TC-F02-11_v1_ten_60_dat.png) |
| TC-F02-12 | Biên | Số tiền `0` | Báo lỗi, không lưu | **Không đạt** (lưu được gói `0 ₫`) | Đạt ("Số tiền phải lớn hơn 0") | BUG-04 | [v0](evidence/TC-F02-12_v0_so_tien_0_sai.png) · [v1](evidence/TC-F02-12_v1_so_tien_0_dat.png) |
| TC-F04-01 | Đúng | Tìm `NET` | Thấy Netflix | Đạt | Đạt | | [v0](evidence/TC-F04-01_v0_tim_net_dat.png) · [v1](evidence/TC-F04-01_v1_tim_net_dat.png) |
| TC-F04-02 | Đúng | Tìm không dấu `goi gia dinh` | Thấy "Gói gia đình" | **Không đạt** ("Không tìm thấy gói phù hợp") | Đạt | BUG-02 | [v0](evidence/TC-F04-02_v0_tim_khong_dau_sai.png) · [v1](evidence/TC-F04-02_v1_tim_khong_dau_dat.png) |
| TC-F04-07 | Đúng | Sắp theo "Chi phí tháng cao nhất" | 10 USD (~258.000 ₫) đứng trên 100.000 ₫ và 59.000 ₫ | **Không đạt** (gói USD nằm cuối) | Đạt (Netflix → Spotify USD → Cuoi thang → Gói gia đình) | BUG-01 | [v0](evidence/TC-F04-07_v0_sapxep_sai.png) · [v1](evidence/TC-F04-07_v1_sapxep_dat.png) |
| TC-F05-01 | Đúng | Đã trả kỳ 31/01 | Kỳ sau lùi 1 tháng, lịch sử thêm 1 dòng | Đạt (28/02, 1 dòng) | Đạt | | Bước 1 của TC-F05-10 |
| TC-F05-04 | Trống | Hộp Đã trả: xoá trống số tiền | Nút Đồng ý bị khoá, hiện lỗi | Đạt | Đạt | | [v0](evidence/TC-F05-04_v0_trong_so_tien_dat.png) · [v1](evidence/TC-F05-04_v1_trong_so_tien_dat.png) |
| TC-F05-10 | Biên | Gói ngày 31/01: Đã trả 2 lần | 28/02 rồi **31/03** | **Không đạt** (ra 28/03) | Đạt (28/02 → 31/03) | BUG-03 | [v0](evidence/TC-F05-10_v0_troi_ngay_sai.png) · [v1](evidence/TC-F05-10_v1_giu_ngay_31_dat.png) |
| TC-F05-12 | Biên | Bấm Đồng ý 2 lần cùng lúc (`input tap & input tap`) | Chỉ ghi 1 kỳ | Đạt (2 → 3 dòng) | Đạt (2 → 3 dòng, kỳ sau 30/04) | | [v0](evidence/TC-F05-12_v0_bam_2_lan_dat.png) · [v1](evidence/TC-F05-12_v1_bam_2_lan_dat.png) |
| TC-F06-03 | Sai | Tạo tag `giai tri` khi đã có `Giai tri` | "Tag này đã tồn tại" | Đạt | Đạt | | [v0](evidence/TC-F06-03_v0_tag_trung_dat.png) · [v1](evidence/TC-F06-03_v1_tag_trung_dat.png) |
| TC-F08-06 | Biên | Mất mạng → bấm Cập nhật tỷ giá | Giữ tỷ giá đã có, không crash, không kẹt "Đang cập nhật" | Đạt | Đạt | | [v0](evidence/TC-F08-06_TC-F14-06_v0_mat_mang_dat.png) · [v1](evidence/TC-F08-06_TC-F14-06_v1_mat_mang_dat.png) |
| TC-F10-03 | Đúng | Xoá dữ liệu bộ đang dùng ("Test W5") | Chỉ bộ đó trống; bộ gốc vẫn 2 gói | Đạt | Đạt | | [bộ gốc vẫn 2 gói](evidence/TC-F13-03_06_v1_bo_goc_khong_doi_dat.png) |
| TC-F10-06 | Biên | Bấm "Thêm 5 gói mẫu" 2 lần | Vẫn 5 gói, không trùng | **Không đạt** (10 gói, mỗi gói trùng 2 lần) | Đạt (5 gói + "Bộ dữ liệu này đã có đủ gói mẫu") | BUG-06 | [v0](evidence/TC-F10-06_v0_mau_trung_sai.png) · [v1 thông báo](evidence/TC-F10-06_v1_thong_bao_da_co_dat.png) · [v1 danh sách](evidence/TC-F10-06_v1_mau_khong_trung_dat.png) |
| TC-F11-07 | Trống | Nhập CSV 0 byte | "Không đọc được dữ liệu: không có gói hợp lệ" | Đạt | Đạt | | [v0](evidence/TC-F11-07_v0_csv_rong_dat.png) · [v1](evidence/TC-F11-07_v1_csv_rong_dat.png) |
| TC-F11-13 | Sai | CSV: 1 tên 61 ký tự, 1 liên kết `abc`, 3 dòng đúng | Nhập 3 gói, "Bỏ qua 2 dòng" | **Không đạt** (nhận cả 5 gói) | Đạt (3 gói, "Bỏ qua 2 dòng không đọc được") | BUG-05 | [v0](evidence/TC-F11-13_v0_csv_bo_qua_quy_tac_sai.png) · [v1](evidence/TC-F11-13_v1_csv_bo_qua_2_dong_dat.png) |
| TC-F12-01 | Đúng | Xuất mã QR (bộ ít gói) | Hiện mã QR và đúng tên bộ, số gói | Đạt | Đạt | | [v0](evidence/TC-F12-01_v0_xuat_qr_dat.png) · [v1](evidence/TC-F12-01_v1_xuat_qr_dat.png) |
| TC-F12-03 | Đúng | Lưu thành ảnh GIF | Tệp GIF được tạo trong Downloads | Đạt (10,4 kB) | Đạt (9,6 kB) | | Thấy tệp trong trình chọn ảnh ở TC-F12-04 |
| TC-F12-04 | Đúng | Chọn tệp GIF vừa lưu ở màn Quét | Hộp nhập đúng số gói / tag / lịch sử | Đạt (5 gói, 1 tag, 3 dòng) | Đạt (4 gói, 1 tag, 3 dòng) | | [v0](evidence/TC-F12-04_v0_nap_gif_dat.png) · [v1](evidence/TC-F12-04_v1_nap_gif_dat.png) |
| TC-F12-08 | Sai | Từ chối quyền camera | "Cần quyền camera…" + nút cấp quyền; vẫn chọn ảnh được | Đạt | Đạt | | [v0](evidence/TC-F12-08_v0_tu_choi_camera_dat.png) · [v1](evidence/TC-F12-08_v1_tu_choi_camera_dat.png) |
| TC-F13-03 | Biên | Sửa bộ "Test W5", bộ gốc không đổi | Bộ gốc vẫn 2 gói | Đạt | Đạt | | [v0](evidence/TC-F13-03_06_v0_bo_goc_khong_doi_dat.png) · [v1](evidence/TC-F13-03_06_v1_bo_goc_khong_doi_dat.png) |
| TC-F13-04 | Đúng | Đổi tên bộ thành `Test W5` | Tên mới hiện ở danh sách bộ | Đạt | Không chạy lại (không liên quan đến phần sửa) | | [v0](evidence/TC-F13-04_v0_doi_ten_dat.png) |
| TC-F13-05 | Trống | Đổi tên bộ thành chuỗi trống | Nút Lưu bị khoá | Đạt | Đạt | | [v0](evidence/TC-F13-05_v0_ten_trong_dat.png) · [v1](evidence/TC-F13-05_v1_ten_trong_dat.png) |
| TC-F13-06 | Đúng | Bộ gốc và bộ đang dùng không có nút Xoá | Không có nút Xoá | Đạt | Đạt | | Cùng ảnh với TC-F13-03 |
| TC-F14-01 | Biên | Màn hình nhỏ 720×1280 | Không cắt chữ quan trọng, nút bấm được | Đạt (xem ghi chú mục 5) | Đạt | | [v0 danh sách](evidence/TC-F14-01_v0_man_nho_danhsach.png) · [v0 báo cáo](evidence/TC-F14-01_v0_man_nho_baocao.png) · [v1](evidence/TC-F14-01_v1_man_nho_danhsach.png) |
| TC-F14-08 | Biên | Cài đè bản đã sửa (CSDL nâng từ v4 lên v5) | Dữ liệu cả 2 bộ còn nguyên | — | Đạt (bộ gốc 2 gói, Test W5 10 gói) | | [v1](evidence/TC-F14-08_v1_cai_de_nang_cap_v5_dat.png) |

## 3. Danh sách lỗi và tình trạng xử lý
| Mã | Test case | Mô tả | Cách tái hiện | Mức độ | Người phụ trách | Trạng thái | Kiểm thử lại |
|---|---|---|---|---|---|---|---|
| BUG-01 | TC-F04-07 | Sắp theo chi phí tháng so số tiền gốc, không quy đổi tiền tệ, nên gói 10 USD luôn đứng cuối | Bộ có gói 10 USD và gói 59.000 ₫ → Sắp xếp → "Chi phí tháng cao nhất" | Trung bình | (điền tên TV) | **Đã đóng** | 24/11 – Đạt |
| BUG-02 | TC-F04-02 | Tìm kiếm chỉ bỏ qua hoa thường, không bỏ dấu, nên `goi gia dinh` không ra "Gói gia đình" | Gõ không dấu vào ô tìm kiếm | Trung bình | (điền tên TV) | **Đã đóng** | 24/11 – Đạt |
| BUG-03 | TC-F05-10 | Gói ngày 31 sau khi qua tháng 2 bị trôi vĩnh viễn về ngày 28 (31/01 → 28/02 → 28/03 → 28/04) | Gói hằng tháng ngày 31/01 → Đã trả 2 lần | **Cao** | (điền tên TV) | **Đã đóng** | 24/11 – Đạt |
| BUG-04 | TC-F02-12 | Form cho lưu gói có số tiền 0 | Thêm gói, số tiền `0` → Lưu | Thấp | (điền tên TV) | **Đã đóng** | 24/11 – Đạt |
| BUG-05 | TC-F11-13 | Nhập CSV không áp quy tắc của form (tên ≤ 60, liên kết http(s), số tiền > 0). Gói nhập vào mở ra Lưu lại thì báo lỗi | Nhập `w5_bad.csv` | Trung bình | (điền tên TV) | **Đã đóng** | 24/11 – Đạt |
| BUG-06 | TC-F10-06 | "Thêm 5 gói mẫu" bấm lần 2 tạo thêm 5 gói trùng | Bấm "Thêm 5 gói mẫu" 2 lần | Thấp | (điền tên TV) | **Đã đóng** | 24/11 – Đạt |

| Mức độ | Phát hiện | Đã đóng | Còn mở |
|---|---|---|---|
| Nghiêm trọng | 0 | 0 | 0 |
| Cao | 1 | 1 | 0 |
| Trung bình | 3 | 3 | 0 |
| Thấp | 2 | 2 | 0 |
| **Tổng** | **6** | **6** | **0** |

## 4. Cách sửa (thay đổi chưa commit)
| Lỗi | Thay đổi | Test tự động chứng minh |
|---|---|---|
| BUG-01 | `ui/list/ListViewModel.kt`: bộ so sánh chuyển vào `companion` và dùng `BillingMath.monthlyIn` (quy đổi theo tỷ giá). Gói chưa có tỷ giá xếp cuối | `BillingMathTest.priceSort_convertsCurrency` |
| BUG-02 | Thêm `domain/TextSearch.kt` (bỏ dấu bằng `Normalizer`, đổi `đ` thành `d`). `ListViewModel.matches` dùng hàm này | `TextSearchTest.matches_ignoresCaseAndVietnameseMarks` |
| BUG-03 | CSDL **v5**: cột `billing_day` (ngày gốc của gói), nâng cấp từ v3/v4 bằng `ALTER ADD COLUMN`, dữ liệu cũ giữ nguyên. `BillingMath.advance(date, cycle, billingDay)` quay về ngày gốc nếu tháng đủ ngày. Sửa gói mà không đổi ngày thì giữ ngày gốc; Kích hoạt lại với ngày mới thì đổi ngày gốc | `BillingMathTest.advance_keepsBillingDay`, `PaymentActionsTest.markPaid_monthEnd_keepsBillingDay`, `SubTrackDatabaseTest.upgradeFromV4_addsBillingDay`, `freshInstall_hasV5Schema` |
| BUG-04 | `EditViewModel.validate`: số tiền ≤ 0 báo `err_amount_zero` ("Số tiền phải lớn hơn 0") | Kiểm thủ công (TC-F02-12) |
| BUG-05 | `domain/DatasetCsv.kt`: dòng gói phải có tên ≤ `BillingMath.MAX_NAME_LENGTH`, liên kết hợp lệ và số tiền > 0, nếu không thì tính vào "Bỏ qua N dòng". Form và CSV dùng chung hằng số độ dài tên | `DatasetCsvTest.rowsBreakingFormRules_skipped` |
| BUG-06 | `SettingsViewModel.addDemoData`: bỏ qua gói mẫu đã có cùng tên. Nếu không còn gì để thêm thì báo `demo_exists`. `demo_added` giờ ghi đúng số gói đã thêm | Kiểm thủ công (TC-F10-06) |

### Test tự động
| Lần chạy | Kết quả |
|---|---|
| Trước khi sửa: 2 test mới chạy được trên code cũ | **Cả 2 hỏng**, đúng lỗi: `expected:<2027-03-31> but was:<2027-03-28>`; CSV nhận cả 3 dòng sai. Log: [unit/vong0_test_moi_hong.log](unit/vong0_test_moi_hong.log). 4 test mới còn lại cần hàm và cột mới nên không biên dịch được trên code cũ |
| Sau khi sửa: `gradlew.bat testDebugUnitTest lintDebug` | **80/80 Đạt** (74 cũ + 6 mới), lint không lỗi. Log: [unit/vong1_toan_bo_test.txt](unit/vong1_toan_bo_test.txt) |

## 5. Ghi chú và giới hạn
- **Màn hình nhỏ (TC-F14-01):**
  - Nút "Thêm gói đăng ký" che một phần hàng lọc và gói đầu danh sách. Vẫn cuộn được và bấm được nên ghi Đạt.
  - Nếu nhóm thấy cần sửa thì thêm một lỗi mức Thấp.
- **Mất mạng:**
  - Chỉ kiểm phần tỷ giá và báo cáo.
  - TC-F14-06 (mọi chức năng khi mất mạng) chưa chạy hết.
  - Các chức năng còn lại không dùng mạng (đã xem trong code).
- **Ngày gốc cho dữ liệu cũ:**
  - Gói tạo trước v5 lấy ngày gốc theo ngày thanh toán hiện tại.
  - Gói nào đã bị trôi về 28 trước khi sửa thì không tự quay về 31. Người dùng sửa lại ngày một lần là xong.
  - CSV không mang cột ngày gốc: gói nhập vào lấy ngày theo cột "Ngày thanh toán tiếp theo".
- **Chưa chạy:**
  - máy thật và camera (TC-F12-09)
  - nhắc hạn và thông báo (F09)
  - các ca còn lại trong 135 ca của [../02_test_case.md](../02_test_case.md)
- **Dọn dẹp:** đã xoá bộ "Test W5", các tệp CSV / GIF test trên máy ảo, và đặt lại kích thước màn hình và mạng. Bộ gốc vẫn 2 gói.
- **Script và dữ liệu tạm:** nằm trong `temp/w5/`:
  - `ui5.py` điều khiển UI và chụp ảnh
  - `w5_data.csv`, `w5_bad.csv`, `w5_empty.csv` là dữ liệu test
  - log Gradle đầy đủ
