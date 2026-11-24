# Bộ test case chọn để trình bày (có cả Đạt và Không đạt)

Đây là bộ test case rút ra từ [02_test_case.md](02_test_case.md), chọn dựa trên mã nguồn ở commit `f59d732`. Mục đích: khi trình bày có đủ ba phần của quy trình.
- **Đạt:** những ca mà code đã xử lý đúng.
- **Không đạt:** lỗi thật trong code. Mỗi lỗi ghi vào [03_bang_loi.md](03_bang_loi.md), sửa xong thì chạy lại (retest) cho ra Đạt.
- **Hồi quy:** chạy lại các ca Đạt sau khi sửa để chứng minh không phát sinh lỗi mới.

Mọi dự đoán dưới đây đều dựa trên việc đọc code. Khi chạy thật trên máy, ghi kết quả thực tế. Nếu khác dự đoán thì ghi kết quả thực tế, không ghi theo dự đoán.

## A. Ca dự kiến Không đạt (lỗi thật, sửa được)

| # | Test case | Ưu tiên | Lỗi dự kiến (thực tế) | Nguyên nhân trong code | Mức độ | Cách sửa đề xuất | Độ khó |
|---|---|---|---|---|---|---|---|
| 1 | TC-F04-07 (sắp theo số tiền) | TB | Gói 10 USD/tháng luôn nằm cuối, dưới cả gói 59.000 ₫. Sắp xếp so số tiền gốc, không quy đổi tiền tệ | `ui/list/ListViewModel.kt` dòng 116: `compareByDescending { BillingMath.monthlyCost(it.amount, it.cycle) }` | Trung bình | Dùng `BillingMath.monthlyIn(sub, defaultCurrency, perUsd)` như phần tính tổng. Gói chưa có tỷ giá để cuối | Dễ |
| 2 | TC-F05-10 (Đã trả hai lần, gói ngày 31/01) | Cao | Lần 2 ra **28/03**, không phải 31/03: ngày thanh toán bị trôi vĩnh viễn về 28 | `domain/BillingMath.kt` dòng 39-44: `advance` cộng tháng từ kỳ vừa trả, nên làm mất ngày gốc. `data/SubscriptionRepository.kt` dòng 111 gọi hàm này | Cao | Lưu ngày gốc của gói (cột mới, CSDL v5, nâng cấp giữ dữ liệu), rồi tính kỳ sau = cộng tháng và kẹp vào ngày gốc. Có test sẵn ở `BillingMathTest.kt` dòng 74 cho bước 31/01→28/02, cần thêm test cho bước tiếp theo | Trung bình |
| 3 | TC-F11-13 (CSV có tên 61 ký tự, liên kết `abc`) | Cao | Nhập CSV vẫn nhận cả 2 dòng sai. Mở gói đó → Lưu thì form báo "Tên tối đa 60 ký tự" / "Liên kết phải bắt đầu bằng http://…" | `domain/DatasetCsv.kt` dòng 104: tên chỉ kiểm khác rỗng, không kiểm độ dài và liên kết. Quy tắc chỉ nằm trong `ui/edit/EditViewModel.kt` dòng 163-178 | Trung bình | Đưa quy tắc tên / liên kết về một chỗ ở `domain/`, cho cả form và `DatasetCsv` cùng dùng. Dòng sai thì tính vào "Bỏ qua N dòng" | Dễ |
| 4 | TC-F02-12 (số tiền `0`) | TB | Lưu được gói 0 ₫ và 0,00 $ | `EditViewModel.kt` dòng 171-175 chỉ kiểm trống và sai cú pháp. `parseAmount` trả về 0.0 là hợp lệ | Thấp | Thêm điều kiện `amount <= 0` thì báo lỗi số tiền | Dễ |
| 5 | TC-F04-02 (tìm không dấu `goi gia dinh`) | TB | Không tìm thấy gói "Gói gia đình" | `ListViewModel.kt` dòng 99-112: `contains(q, ignoreCase = true)` chỉ bỏ qua hoa thường, không bỏ dấu | Trung bình | Chuẩn hoá cả từ khoá và nội dung được tìm: `Normalizer.normalize(NFD)`, bỏ dấu, đổi `đ` thành `d` | Dễ |
| 6 | TC-F10-06 (thêm dữ liệu mẫu 2 lần) | Thấp | Ra 10 gói, mỗi gói mẫu bị trùng 2 lần | `ui/settings/SettingsViewModel.kt` dòng 142-161 lưu 5 gói mà không kiểm tên đã có | Thấp | Bỏ qua gói mẫu đã có cùng tên. Nếu không thêm được gói nào thì báo "đã có dữ liệu mẫu" | Dễ |

Nên ưu tiên trình bày 1, 3 và 5: dễ tái hiện trên máy ảo trong 1–2 phút, và sửa chỉ vài dòng nên vòng retest nhanh. Lỗi 2 là lỗi nặng nhất, nên đưa vào danh sách lỗi. Nhóm có thể sửa ngay, hoặc để trạng thái "Đang sửa" nếu chưa kịp làm phần nâng cấp CSDL.

### Unit test để chứng minh đã sửa (viết khi sửa lỗi)
Mỗi test sẽ hỏng trước khi sửa và đạt sau khi sửa, nên tự nó là bằng chứng retest:

| Lỗi | Test đề xuất | Vị trí |
|---|---|---|
| 2 | `advance_keepsAnchorDay`: 31/01 → 28/02 → 31/03 | `test/.../BillingMathTest.kt` |
| 3 | `read_skipsRowsBreakingFormRules`: tên 61 ký tự và liên kết `abc` bị bỏ, `skipped = 2` | `test/.../domain/DatasetCsvTest.kt` |
| 5 | Test hàm bỏ dấu: `"Gói gia đình"` khớp với `"goi gia dinh"` | Test mới cho hàm chuẩn hoá (đặt ở `domain/`) |
| 1, 4, 6 | Kiểm thủ công theo test case. Logic nằm trong ViewModel, hiện chưa có test ViewModel | — |

## B. Ca dự kiến Đạt (đối chứng và hồi quy)

| # | Test case | Ưu tiên | Vì sao dự kiến Đạt (bằng chứng trong code / lần chạy trước) |
|---|---|---|---|
| 1 | TC-F02-05 (VND có phần lẻ `15,50`) | Cao | `BillingMath.parseAmount` dòng 81: VND có phần lẻ thì trả null |
| 2 | TC-F02-08 (tên trống / chỉ dấu cách) | Cao | `EditViewModel.kt` dòng 164-166: trim rồi kiểm rỗng |
| 3 | TC-F02-11 (tên 60 / 61 ký tự) | Cao | `EditViewModel.kt` dòng 167 |
| 4 | TC-F02-06 (`1234.567`, `abc`, `-5`…) | Cao | Regex và kiểm nhóm nghìn ở `parseAmount` dòng 77-80. Đã có test trong `BillingMathTest` |
| 5 | TC-F05-01 (Đã trả kỳ bình thường) | Cao | `SubscriptionRepository.record`. Có test `PaymentActionsTest` |
| 6 | TC-F05-04 (hộp Đã trả để trống số tiền) | Cao | `DetailScreen.kt` dòng 291-297: nút OK bị khoá khi số tiền không hợp lệ |
| 7 | TC-F05-12 (bấm Đã trả 2 lần nhanh) | TB | `record` kiểm kỳ trong cùng một giao dịch và bắt `SQLiteConstraintException` (dòng 100-115) |
| 8 | TC-F06-03 (tag trùng, khác hoa thường) | TB | Tên tag là unique không phân biệt hoa thường, và form báo "Tag này đã tồn tại" |
| 9 | TC-F11-07 (CSV rỗng) | Cao | Không có gói hợp lệ thì báo "Không đọc được dữ liệu: không có gói hợp lệ" |
| 10 | TC-F11-10 (vòng CSV 80 gói → app → CSV) | Cao | Đã chạy trên máy ảo, `compare` báo KHỚP |
| 11 | TC-F12-04 (nhập lại từ một tệp GIF) | Cao | Đã chạy trên máy ảo: 80 gói, 8 tag, 185 dòng lịch sử |
| 12 | TC-F13-03 (sửa bộ B không ảnh hưởng bộ A) | Cao | Mỗi bộ một tệp SQLite riêng. Đã chạy trên máy ảo (gốc giữ nguyên, 80 / 79 gói) |
| 13 | TC-F13-05 (đổi tên bộ thành chuỗi trống) | TB | `DatasetsScreen.kt` dòng 101 khoá nút Lưu, và `DatasetRegistry.rename` dòng 86 bỏ qua tên trống |
| 14 | TC-F13-06 (không xoá được bộ gốc / bộ đang dùng) | Cao | `DatasetRegistry.delete` dòng 92-96 |
| 15 | TC-F08-04 (mất mạng, chưa từng có tỷ giá) | Cao | `RatesRepository.sync` bọc `runCatching` và giữ trạng thái cũ. Chưa chạy thật: dự kiến Đạt, cần xác nhận |

## C. Kịch bản trình bày đề xuất
1. **Vòng 0:**
   - Chạy 21 ca (6 ở mục A, 15 ở mục B) trên D1, và các ca ưu tiên Cao trên D4.
   - Kết quả dự kiến: 15 Đạt, 6 Không đạt.
   - Chạy test tự động: 74/74.
2. **Ghi lỗi:** ghi 6 lỗi BUG-01…BUG-06 vào [03_bang_loi.md](03_bang_loi.md), kèm mức độ và người phụ trách.
3. **Sửa:** sửa ít nhất lỗi 1, 3, 4, 5, 6. Mỗi lỗi một commit, kèm unit test nếu có trong bảng trên.
4. **Vòng 1 (retest):**
   - Chạy lại đúng các ca Không đạt, kết quả phải chuyển sang Đạt.
   - Chạy lại 15 ca ở mục B và các ca hồi quy theo bảng trong [04_kiem_thu_lai.md](04_kiem_thu_lai.md) để chứng minh không phát sinh lỗi mới.
   - Test tự động: 74 + số test mới.
5. **Trình bày:**
   - Bảng trước / sau, ví dụ vòng 0 có 15/21 Đạt, vòng 1 có 20/21 hoặc 21/21.
   - Danh sách lỗi và tình trạng: Đã đóng, hoặc Đang sửa (lỗi 2 nếu chưa làm xong).
