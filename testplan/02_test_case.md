# Bảng test case – SubTrack (Tuần 5)

Kế hoạch: [01_ke_hoach_kiem_thu.md](01_ke_hoach_kiem_thu.md). Mã thiết bị (D1–D5) và điều kiện (E1–E7) định nghĩa ở mục 4 của kế hoạch.

**Loại:**
- **Đúng:** nhập hợp lệ
- **Sai:** nhập không hợp lệ
- **Trống:** để trống
- **Biên:** giá trị ở ranh giới, hoặc tình huống đặc biệt

**Cách điền:**
- Cột **KQ** ghi `Đạt` hoặc `Không đạt`, kèm thiết bị, người test và ngày, ví dụ `Đạt – D1 – TV2 – 25/11`.
- Ca Không đạt thì ghi mã lỗi (`BUG-xx` trong [03_bang_loi.md](03_bang_loi.md)) vào cột **Lỗi**.
- Ca thuộc ưu tiên Cao phải chạy trên cả D1 và D4.

Mọi chuỗi thông báo trong ngoặc kép lấy đúng từ `strings.xml` của app.

---

## F01 – Giới thiệu lần đầu
| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F01-01 | Đúng | Cài mới (hoặc xoá dữ liệu app trên máy thật) → mở app → đi hết các trang giới thiệu → hoàn tất | Vào màn Danh sách. Mở lại app thì không hiện giới thiệu nữa | TB | | |
| TC-F01-02 | Biên | Đang ở trang giới thiệu thì xoay ngang, rồi xoay lại | Vẫn ở đúng trang, không crash | Thấp | | |
| TC-F01-03 | Biên | Đang giới thiệu thì bấm Back hệ thống | Hành vi hợp lý (về trang trước hoặc thoát), không crash, không bỏ qua bước lưu "đã xem" | Thấp | | |

## F02 – Thêm / sửa gói đăng ký
Quy tắc trong code:
- Tên: bắt buộc, tối đa 60 ký tự sau khi bỏ khoảng trắng hai đầu.
- Số tiền: dấu chấm tách nghìn, dấu phẩy trước phần lẻ, tối đa 2 chữ số lẻ; VND không có phần lẻ.
- Liên kết: không bắt buộc, nhưng nếu có thì phải bắt đầu bằng http:// hoặc https://.

| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F02-01 | Đúng | + → Tên `Netflix`, Số tiền `260.000`, VND, Hằng tháng, ngày tiếp theo = hôm nay + 5 → Lưu | Quay về Danh sách. Gói hiện `260.000 ₫`, đúng ngày `dd/MM/yyyy` | Cao | | |
| TC-F02-02 | Đúng | Tên `Spotify`, `5,99`, USD, Hằng tháng → Lưu | Lưu được, hiện `5,99 $`, có dòng quy đổi sang tiền tệ mặc định (nếu có tỷ giá) | Cao | | |
| TC-F02-03 | Đúng | Gói đủ trường: 2 tag, 2 nền tảng, liên kết `https://netflix.com/account`, ghi chú tiếng Việt có xuống dòng | Mở Chi tiết thấy đủ và đúng mọi trường | Cao | | |
| TC-F02-04 | Đúng | Mở gói ở TC-F02-01 → Sửa → đổi số tiền thành `300.000` → Lưu | Danh sách, Chi tiết và Báo cáo đều cập nhật 300.000 ₫ | Cao | | |
| TC-F02-05 | Sai | Số tiền `15,50` với VND | Lỗi "Số tiền không hợp lệ. Dấu chấm tách nghìn, dấu phẩy trước phần lẻ (ví dụ 1.234,50)", không lưu | Cao | | |
| TC-F02-06 | Sai | Số tiền lần lượt: `1234.567`, `1.23`, `abc`, `-5`, `1,2,3` | Mỗi giá trị đều báo lỗi số tiền không hợp lệ | Cao | | |
| TC-F02-07 | Sai | Liên kết lần lượt: `ftp://x.com`, `www.netflix.com`, `https://` | Lỗi "Liên kết phải bắt đầu bằng http:// hoặc https://" | TB | | |
| TC-F02-08 | Trống | Tên để trống, hoặc chỉ gõ dấu cách | Lỗi "Vui lòng nhập tên dịch vụ" | Cao | | |
| TC-F02-09 | Trống | Số tiền để trống | Lỗi "Vui lòng nhập số tiền" | Cao | | |
| TC-F02-10 | Trống | Mở form mới, không nhập gì → bấm Lưu | Từng trường báo lỗi, có thông báo "Vui lòng sửa các trường bị lỗi", không tạo gói | Cao | | |
| TC-F02-11 | Biên | Tên đúng 60 ký tự → Lưu. Sau đó tên 61 ký tự → Lưu | 60 ký tự lưu được. 61 ký tự báo "Tên tối đa 60 ký tự" | Cao | | |
| TC-F02-12 | Biên | Số tiền `0` (VND) và `0,00` (USD) | Báo lỗi số tiền không hợp lệ: gói phải có số tiền lớn hơn 0 | TB | | |
| TC-F02-13 | Biên | Số tiền `999.999.999.999` VND | Lưu được. Số hiện đầy đủ, không tràn ra ngoài ở Danh sách và Báo cáo (kiểm cả trên D2) | TB | | |
| TC-F02-14 | Biên | USD: `1,5` (đạt), `1,50` (đạt), `1,505` (lỗi), `0,01` (đạt) | Đúng như ghi trong ngoặc | TB | | |
| TC-F02-15 | Biên | Số tiền có dấu cách `1 234` | Hiểu là 1.234 | Thấp | | |
| TC-F02-16 | Biên | Liên kết `http://a` (lỗi) và `http://ab` (đạt) | Đúng như ghi trong ngoặc | Thấp | | |
| TC-F02-17 | Biên | Ngày thanh toán tiếp theo: hôm qua, hôm nay, 29/02 của năm nhuận | Lưu được. Gói hôm qua hiện quá hạn, gói hôm nay hiện đến hạn hôm nay | TB | | |
| TC-F02-18 | Biên | Nhắc trước: "Không nhắc", rồi 30 ngày | Lưu đúng lựa chọn, mở lại form thấy đúng | Thấp | | |
| TC-F02-19 | Sai | Trong form, tạo tag mới trùng tên tag có sẵn (khác hoa thường) | Báo "Tag này đã tồn tại", không tạo tag trùng | TB | | |
| TC-F02-20 | Biên | Đang nhập dở form thì bấm Back | Thoát và không tạo gói. Nếu app có hỏi bỏ thay đổi thì hộp hỏi hoạt động đúng | TB | | |

## F03 – Xoá gói
| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F03-01 | Đúng | Gói đã có ít nhất 1 kỳ "Đã trả" → Sửa → Xoá → Xác nhận | Gói biến mất khỏi Danh sách. Khoản đã trả vẫn còn trong "Chi thực tế" ở Báo cáo | Cao | | |
| TC-F03-02 | Sai | Bấm Xoá → Huỷ trong hộp xác nhận | Gói vẫn còn nguyên | Cao | | |
| TC-F03-03 | Biên | Xoá gói duy nhất còn lại | Danh sách hiện "Chưa có gói đăng ký nào" và "Nhấn nút + để thêm gói đầu tiên." | TB | | |
| TC-F03-04 | Biên | Gói đang có thông báo nhắc → xoá gói → bấm "Đã trả" trên thông báo cũ | Không crash, không tạo dữ liệu rác | TB | | |

## F04 – Danh sách: tìm kiếm, lọc, sắp xếp
Chuẩn bị: dữ liệu mẫu (Cài đặt → Thêm dữ liệu mẫu) trên bộ "Test".

| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F04-01 | Đúng | Tìm `net` | Thấy Netflix (không phân biệt hoa thường) | Cao | | |
| TC-F04-02 | Đúng | Tên gói `Gói gia đình` → tìm `gói`, rồi tìm không dấu `goi gia dinh` | Cả hai lần đều thấy gói (người dùng hay gõ không dấu) | TB | | |
| TC-F04-03 | Sai | Tìm `xyz123` | Hiện "Không tìm thấy gói phù hợp" | TB | | |
| TC-F04-04 | Trống | Xoá hết ô tìm kiếm | Hiện lại toàn bộ gói | TB | | |
| TC-F04-05 | Đúng | Lọc trạng thái "Đang hoạt động" + một danh mục | Chỉ còn gói thoả cả hai điều kiện | Cao | | |
| TC-F04-06 | Đúng | Bấm xoá bộ lọc | Hiện tất cả gói, giữ nguyên kiểu sắp xếp | TB | | |
| TC-F04-07 | Đúng | Lần lượt sắp theo ngày, số tiền, tên | Thứ tự đúng. Số tiền ngoại tệ so sánh sau khi quy đổi (nếu có tỷ giá) | TB | | |
| TC-F04-08 | Biên | Tìm ký tự đặc biệt `%`, `'`, `"` | Không crash, kết quả hợp lý | TB | | |
| TC-F04-09 | Biên | Bộ dữ liệu không có gói nào | Hiện trạng thái trống, không crash | TB | | |
| TC-F04-10 | Biên | Bộ 80 gói (nhập từ `temp/qr-test/dataset_qr.csv`) → cuộn nhanh từ đầu đến cuối | Cuộn mượt, không giật hay crash | Thấp | | |

## F05 – Chi tiết và xác nhận kỳ thanh toán
| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F05-01 | Đúng | Gói hằng tháng → Đã trả, giữ số tiền mặc định | Ngày tiếp theo lùi 1 tháng. Lịch sử thêm 1 dòng "Đã trả" | Cao | | |
| TC-F05-02 | Đúng | Đã trả với số tiền khác `250.000` | Lịch sử ghi 250.000 ₫. Giá gói không đổi | Cao | | |
| TC-F05-03 | Sai | Ở hộp Đã trả nhập `abc` | Hiện lỗi số tiền không hợp lệ, không ghi nhận | Cao | | |
| TC-F05-04 | Trống | Ở hộp Đã trả xoá trống số tiền | Không cho xác nhận, hoặc báo lỗi rõ ràng | Cao | | |
| TC-F05-05 | Đúng | Bỏ qua kỳ | Ngày tiếp theo lùi 1 kỳ. Lịch sử ghi "Bỏ qua", không tính vào Chi thực tế | Cao | | |
| TC-F05-06 | Đúng | Hoãn 1 ngày | Hôm nay không nhắc lại. Ngày thanh toán không đổi | TB | | |
| TC-F05-07 | Đúng | Huỷ gói | Trạng thái Đã huỷ, không còn tính trong Báo cáo | Cao | | |
| TC-F05-08 | Đúng | Kích hoạt lại gói đã huỷ, chọn ngày mới | Trạng thái Đang hoạt động, ngày tiếp theo là ngày đã chọn | TB | | |
| TC-F05-09 | Đúng | Ngay sau mỗi thao tác 01, 05, 07: bấm Hoàn tác trên snackbar | Gói trở về đúng như trước thao tác, lịch sử bỏ dòng vừa thêm | Cao | | |
| TC-F05-10 | Biên | Gói hằng tháng ngày 31/01 → Đã trả → Đã trả lần nữa | Lần 1: kỳ sau 28/02 (29/02 năm nhuận). Lần 2: kỳ sau **31/03** (giữ ngày gốc của gói, không bị trôi về 28) | Cao | | |
| TC-F05-11 | Biên | Gói có ngày tiếp theo là 3 tháng trước | Hiện quá hạn. Đã trả chỉ lùi 1 kỳ, app không tự nhảy kỳ | TB | | |
| TC-F05-12 | Biên | Bấm Đã trả hai lần thật nhanh | Chỉ ghi nhận 1 lần | TB | | |
| TC-F05-13 | Đúng | Trong lịch sử, hoàn tác kỳ gần nhất | Dòng lịch sử mất, ngày tiếp theo trở về kỳ đó | TB | | |
| TC-F05-14 | Biên | Gói chưa có kỳ nào được xử lý | Mục lịch sử hiện "Chưa có kỳ nào được xử lý" | Thấp | | |

## F06 – Quản lý tag (tab Tổ chức)
| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F06-01 | Đúng | + → tên `Giải trí`, chọn màu → Lưu | Tag xuất hiện với đúng màu, chọn được trong form gói | TB | | |
| TC-F06-02 | Trống | Tên trống hoặc chỉ có dấu cách → Lưu | "Vui lòng nhập tên tag" | TB | | |
| TC-F06-03 | Sai | Tạo `giải trí` khi đã có `Giải trí` | "Tag này đã tồn tại" | TB | | |
| TC-F06-04 | Biên | Gõ tên 41 ký tự | Ô nhập dừng ở 40 ký tự. Lưu được và hiện không vỡ bố cục | Thấp | | |
| TC-F06-05 | Đúng | Sửa tên và màu tag đang gắn với gói | Gói hiển thị tag theo tên và màu mới | TB | | |
| TC-F06-06 | Biên | Xoá tag đang gắn với 2 gói | 2 gói vẫn còn, chỉ mất tag đó | Cao | | |
| TC-F06-07 | Biên | Tên tag có dấu phẩy `Chia sẻ, nhóm` → xuất CSV rồi nhập lại | Tag giữ nguyên tên | TB | | |
| TC-F06-08 | Trống | Bộ dữ liệu chưa có tag | Hiện "Chưa có tag nào. Nhấn + để tạo tag." | Thấp | | |

## F07 – Báo cáo
Chuẩn bị: bộ "Test" trống, thêm đúng 3 gói đang hoạt động: 260.000 ₫/tháng, 1.200.000 ₫/năm, 50.000 ₫/tuần. Tính tay kết quả mong đợi theo `BillingMath.monthlyCost` trước khi chạy.

| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F07-01 | Đúng | Mở Báo cáo | Tổng chi mỗi tháng đúng bằng số tính tay. Phần trăm các danh mục cộng lại 100% | Cao | | |
| TC-F07-02 | Đúng | Thêm gói USD 10,00/tháng → đổi tiền tệ hiển thị USD và VND | Quy đổi đúng theo tỷ giá đang hiện ở thanh tỷ giá | Cao | | |
| TC-F07-03 | Biên | Tạm dừng 1 gói và huỷ 1 gói | Hai gói này không được tính | Cao | | |
| TC-F07-04 | Trống | Bộ không có gói đang hoạt động | "Chưa có gói đang hoạt động để thống kê" | TB | | |
| TC-F07-05 | Trống | Chưa từng bấm Đã trả | Chi thực tế hiện "Chưa có khoản chi nào được ghi nhận." | TB | | |
| TC-F07-06 | Đúng | Đã trả 2 kỳ (TC-F05-01, 02) | Chi thực tế = tổng 2 khoản đã trả | Cao | | |
| TC-F07-07 | Biên | Có gói ngoại tệ nhưng chưa có tỷ giá (cài mới + mất mạng) | Hiện "Chưa có tỷ giá để quy đổi tiền tệ", không crash, không ra số sai | Cao | | |

## F08 – Tỷ giá
| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F08-01 | Đúng | E1: mở app lần đầu trong ngày | Thanh tỷ giá báo "Tỷ giá đã cập nhật hôm nay". Có dòng ghi nguồn "Rates By Exchange Rate API" | Cao | | |
| TC-F08-02 | Đúng | E1: bấm Cập nhật | Hiện "Đang cập nhật tỷ giá…" rồi báo đã cập nhật | TB | | |
| TC-F08-03 | Biên | E2, đã có tỷ giá cũ của hôm trước (đổi ngày máy hoặc chờ sang ngày) | "Hôm nay chưa cập nhật tỷ giá (đang dùng tỷ giá ngày …)". Quy đổi vẫn chạy bằng tỷ giá cũ | Cao | | |
| TC-F08-04 | Sai | E2, chưa từng có tỷ giá (máy mới cài) → bấm Cập nhật | "Hôm nay chưa cập nhật tỷ giá. Kiểm tra kết nối mạng rồi nhấn Cập nhật", không crash | Cao | | |
| TC-F08-05 | Biên | Bấm Cập nhật 5 lần liên tục | Không crash, không treo; chỉ một lần lấy chạy cùng lúc | TB | | |
| TC-F08-06 | Biên | E3: bấm Cập nhật rồi tắt mạng ngay | Giữ tỷ giá cũ, báo lỗi hợp lý, thanh không bị kẹt ở "Đang cập nhật" | TB | | |
| TC-F08-07 | Đúng | Sau TC-F08-04, bật mạng lại → Cập nhật | Cập nhật thành công | TB | | |

## F09 – Nhắc hạn và thông báo
Nếu không muốn chờ tới lượt chạy hằng ngày: lưu một gói sắp đến hạn sẽ kích hoạt kiểm tra ngay (`Reminders.checkNow`).

| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F09-01 | Đúng | Cài đặt → bật Nhắc hạn → cho phép quyền → Gửi thông báo thử | Có thông báo thử | Cao | | |
| TC-F09-02 | Sai | E7: từ chối quyền thông báo → bật Nhắc hạn | App báo cần quyền hoặc hướng dẫn cấp quyền, không crash | Cao | | |
| TC-F09-03 | Đúng | Gói đến hạn hôm nay, Nhắc trước = 0 ngày → lưu | Có thông báo "đến hạn hôm nay" với nút Đã trả và Hoãn | Cao | | |
| TC-F09-04 | Đúng | Bấm "Đã trả" trên thông báo | Thông báo biến mất. Mở app thấy kỳ đã lùi, lịch sử có dòng mới | Cao | | |
| TC-F09-05 | Đúng | Bấm "Hoãn" trên thông báo | Thông báo biến mất, hôm nay không nhắc lại | TB | | |
| TC-F09-06 | Biên | Gói chọn "Không nhắc" và đến hạn hôm nay | Không có thông báo | TB | | |
| TC-F09-07 | Biên | Kích hoạt kiểm tra 2 lần trong cùng ngày (lưu gói 2 lần) | Chỉ 1 thông báo cho mỗi gói, không lặp | TB | | |
| TC-F09-08 | Biên | Có thông báo của bộ A → chuyển sang bộ B | Thông báo của bộ A bị gỡ. Nếu còn thông báo nào, bấm vào không được sửa dữ liệu bộ B | Cao | | |
| TC-F09-09 | Đúng | Tắt Nhắc hạn | Không còn thông báo mới | TB | | |
| TC-F09-10 | Biên | Gói quá hạn 2 ngày | Thông báo dạng "quá hạn", đúng số ngày | TB | | |

## F10 – Cài đặt
| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F10-01 | Đúng | Đổi tiền tệ mặc định sang USD | Form gói mới mặc định USD. Danh sách và Báo cáo quy đổi về USD | TB | | |
| TC-F10-02 | Đúng | Thêm dữ liệu mẫu (trên bộ "Test" đang trống) | Có 5 gói mẫu (Netflix, Spotify Premium, YouTube Premium, Google One 100 GB, Duolingo Super). Bộ "Dữ liệu của tôi" không đổi | TB | | |
| TC-F10-03 | Đúng | Xoá dữ liệu → Xác nhận (trên bộ "Test") | Bộ "Test" trống. Các bộ khác không đổi | Cao | | |
| TC-F10-04 | Sai | Xoá dữ liệu → Huỷ | Không mất gì | Cao | | |
| TC-F10-05 | Đúng | Chia sẻ báo cáo | Mở bảng chia sẻ của hệ thống với nội dung báo cáo đúng | Thấp | | |
| TC-F10-06 | Biên | Thêm dữ liệu mẫu 2 lần | Vẫn chỉ 5 gói mẫu: lần 2 không tạo gói trùng tên (hoặc báo đã có) | Thấp | | |

## F11 – Xuất / nhập CSV
Công cụ: `temp/qr-test/qr_test.py` có các lệnh `push-csv`, `pull-export` và `compare`. Lệnh `compare` báo KHỚP hoặc liệt kê từng chỗ khác.

| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F11-01 | Đúng | Xuất CSV bộ có dữ liệu → mở bằng Excel / Google Sheets | Tiếng Việt không lỗi font. Có đủ 3 mục: Gói đăng ký, Tag, Lịch sử thanh toán | Cao | | |
| TC-F11-02 | Đúng | Nhập CSV vừa xuất → xem hộp xem trước → Lưu và dùng | Hộp ghi đúng số gói, tag và dòng lịch sử. Bộ mới được dùng ngay, các bộ cũ không đổi | Cao | | |
| TC-F11-03 | Đúng | Nhập → Chỉ lưu | Có bộ mới trong danh sách bộ. Bộ đang dùng giữ nguyên | Cao | | |
| TC-F11-04 | Đúng | Nhập → Huỷ | Không tạo bộ mới | Cao | | |
| TC-F11-05 | Sai | Chọn tệp không phải CSV (ảnh, PDF) | "Không đọc được dữ liệu: không có gói hợp lệ", không crash | Cao | | |
| TC-F11-06 | Sai | CSV có 2 dòng hỏng (ngày sai, số tiền chữ) và 5 dòng đúng | Hộp xem trước báo "Bỏ qua 2 dòng". Nhập được 5 gói | Cao | | |
| TC-F11-07 | Trống | CSV rỗng (0 byte), hoặc chỉ có dòng tiêu đề | "Không đọc được dữ liệu: không có gói hợp lệ" | Cao | | |
| TC-F11-08 | Trống | Ở hộp xem trước để trống tên bộ → Lưu | Dùng tên mặc định "Nhập dd/MM HH:mm" | TB | | |
| TC-F11-09 | Biên | CSV định dạng cũ (không có dòng `[Gói đăng ký]`, không có cột `Mã`) | Nhập được phần gói | TB | | |
| TC-F11-10 | Biên | `dataset_qr.csv` (80 gói; có ngoặc kép, dấu phẩy, xuống dòng) → nhập → xuất lại → `python qr_test.py compare` | "KHỚP: dữ liệu thu được từ QR giống hệt bản gốc" | Cao | | |
| TC-F11-11 | Trống | Xuất khi bộ không có gói | "Chưa có gói nào để xuất" | TB | | |
| TC-F11-12 | Biên | Ở trình chọn tệp, bấm Back không chọn gì | Không có gì xảy ra, không crash | Thấp | | |
| TC-F11-13 | Sai | CSV có 1 gói tên dài 61 ký tự và 1 gói liên kết `abc` (sai định dạng), cùng 3 gói đúng → nhập | Áp cùng quy tắc như form: 2 dòng sai bị bỏ qua ("Bỏ qua 2 dòng"), nhập 3 gói. Không được có gói mà mở Sửa rồi Lưu lại thì báo lỗi | Cao | | |

## F12 – Mã QR
Quy trình chi tiết trên máy ảo: `temp/qr-test/HUONG_DAN.md`.

| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F12-01 | Đúng | Bộ 1–2 gói → Xuất mã QR | Một mã QR tĩnh (1 khung) | TB | | |
| TC-F12-02 | Đúng | Bộ 80 gói → Xuất mã QR | QR động "Khung x/10". Nút Tạm dừng và Khung sau hoạt động. Màn hình không tự tắt | Cao | | |
| TC-F12-03 | Đúng | Lưu thành ảnh GIF | "Đã lưu ảnh GIF mã QR". Tệp có 10 khung và lặp vô hạn | Cao | | |
| TC-F12-04 | Đúng | Quét mã QR → Chọn ảnh → chọn GIF ở TC-F12-03 | Hộp xem trước đúng "80 gói, 8 tag, 185 dòng lịch sử thanh toán" | Cao | | |
| TC-F12-05 | Sai | Chọn ảnh không có mã QR | "Không tìm thấy mã QR SubTrack trong ảnh đã chọn" | TB | | |
| TC-F12-06 | Sai | Chỉ chọn 3 trong 10 ảnh khung | Báo đã nhận 3/10 khung, chưa nhập | TB | | |
| TC-F12-07 | Trống | Mở chọn ảnh rồi bấm Back | Không có gì xảy ra | Thấp | | |
| TC-F12-08 | Sai | E7: từ chối quyền camera | Màn quét báo thiếu quyền và có nút cấp quyền. Vẫn chọn ảnh được | Cao | | |
| TC-F12-09 | Đúng | **Máy thật:** D5 chiếu QR động 80 gói, D4 quét bằng camera | Đếm khung tăng dần tới 10/10, sau đó hộp xem trước hiện ra. Ghi thời gian quét | Cao | | |
| TC-F12-10 | Biên | Quét lẫn khung của 2 lần xuất khác nhau, hoặc mã QR của app khác | Mã lạ bị bỏ qua. Khung của lần xuất khác làm bộ đếm bắt đầu lại, không ra dữ liệu trộn | TB | | |
| TC-F12-11 | Biên | E2: quét bằng GIF và bằng camera khi mất mạng | Vẫn quét được (mô hình ML Kit có sẵn trong app) | Cao | | |
| TC-F12-12 | Biên | Rời màn quét → `adb shell dumpsys media.camera` | Camera đã ngắt (DISCONNECT) | TB | | |

## F13 – Bộ dữ liệu
| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F13-01 | Đúng | Cài đặt → Bộ dữ liệu | Thấy bộ gốc và các bộ đã nhập (tên, ngày tạo, số gói), có đánh dấu bộ đang dùng | Cao | | |
| TC-F13-02 | Đúng | Dùng bộ khác | Danh sách đổi theo. Đầu Danh sách hiện "Đang xem bộ dữ liệu: …" | Cao | | |
| TC-F13-03 | Biên | Ở bộ B: xoá 1 gói, thêm 1 gói → quay về bộ A | Bộ A giữ nguyên số gói và nội dung (không ghi đè lẫn nhau) | Cao | | |
| TC-F13-04 | Đúng | Đổi tên bộ thành `Bản thử 2` | Tên mới hiện ở danh sách bộ và ở đầu Danh sách | TB | | |
| TC-F13-05 | Trống | Đổi tên bộ thành chuỗi trống | Không cho lưu, hoặc giữ tên cũ. Không được ra bộ không tên | TB | | |
| TC-F13-06 | Đúng | Xoá bộ không dùng → Xác nhận | Bộ biến mất. Bộ gốc và bộ đang dùng không có nút Xoá | Cao | | |
| TC-F13-07 | Biên | Như TC-F09-08 | Như TC-F09-08 | Cao | | |
| TC-F13-08 | Biên | Lưu gói rồi lập tức chuyển bộ; hoặc chuyển bộ ngay khi bấm Lưu và dùng | Không crash, dữ liệu không ghi nhầm sang bộ khác | Cao | | |
| TC-F13-09 | Đúng | Chọn bộ B → tắt hẳn app → mở lại | Vẫn đang dùng bộ B | Cao | | |
| TC-F13-10 | Biên | Tên bộ dài 100 ký tự | Hiển thị gọn (cắt hoặc xuống dòng), không vỡ bố cục | Thấp | | |

## F14 – Phi chức năng: màn hình, mạng, vòng đời
| Mã | Loại | Bước / dữ liệu | Kết quả mong đợi | Ưu tiên | KQ | Lỗi |
|---|---|---|---|---|---|---|
| TC-F14-01 | Biên | D2 (màn hình nhỏ): mở lần lượt cả 4 tab và các màn Sửa, Chi tiết, Bộ dữ liệu, QR | Không cắt chữ quan trọng, mọi nút đều bấm được, cuộn được tới cuối | Cao | | |
| TC-F14-02 | Biên | D3 (cỡ máy tính bảng): như TC-F14-01 | Bố cục không vỡ, mã QR đủ to và nét | TB | | |
| TC-F14-03 | Biên | E4: đang nhập dở form Sửa thì xoay ngang, rồi xoay lại | Không mất dữ liệu đã gõ, không crash | Cao | | |
| TC-F14-04 | Biên | E5 (cỡ chữ 1.3) trên D1 và D2 | Chữ không chồng lên nhau, nút không bị đẩy ra ngoài màn hình | TB | | |
| TC-F14-05 | Biên | E6 (giao diện tối) | Chữ đủ tương phản. Mã QR vẫn đen trên nền trắng để quét được | TB | | |
| TC-F14-06 | Biên | E2 (mất mạng): thêm, sửa, xoá gói, Báo cáo, xuất / nhập CSV, QR | Mọi chức năng chạy bình thường. Chỉ tỷ giá báo chưa cập nhật | Cao | | |
| TC-F14-07 | Biên | Vuốt tắt app khỏi đa nhiệm → mở lại | Dữ liệu còn nguyên | Cao | | |
| TC-F14-08 | Biên | Cài đè bản mới (`gradlew.bat installDebug`, không gỡ app) | Dữ liệu, bộ dữ liệu và cài đặt còn nguyên | Cao | | |
| TC-F14-09 | Biên | D4 là máy Android 8–12 (nếu nhóm có) | Mở được. Thông báo hoạt động (máy dưới Android 13 không hỏi quyền thông báo) | TB | | |
| TC-F14-10 | Đúng | Bấm Back hệ thống ở từng màn | Quay về đúng màn trước. Ở tab gốc thì thoát app | TB | | |
| TC-F14-11 | Biên | Bấm vào thông báo nhắc khi app đang tắt | Mở thẳng đúng gói. Xoay màn hình sau đó không mở lại lần nữa | TB | | |

---

## Tổng hợp (điền sau khi chạy)
| Chức năng | Số ca | Đạt | Không đạt | Chưa chạy |
|---|---|---|---|---|
| F01 | 3 | | | |
| F02 | 20 | | | |
| F03 | 4 | | | |
| F04 | 10 | | | |
| F05 | 14 | | | |
| F06 | 8 | | | |
| F07 | 7 | | | |
| F08 | 7 | | | |
| F09 | 10 | | | |
| F10 | 6 | | | |
| F11 | 13 | | | |
| F12 | 12 | | | |
| F13 | 10 | | | |
| F14 | 11 | | | |
| **Tổng** | **135** | | | |
