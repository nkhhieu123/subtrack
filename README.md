# SubTrack – Subscription Tracker (Android)

Đồ án môn Lập trình thiết bị di động – Nhóm 4. Ứng dụng Android native (Kotlin + Jetpack Compose + Material 3), giao diện 100% tiếng Việt. Ý tưởng và luồng chức năng tham khảo từ Sub Radar (chỉ có trên iOS); tên, giao diện và mã nguồn do nhóm tự xây dựng.

| Thư mục | Nội dung |
|---|---|
| `android/` | Dự án Android Studio |
| `docs/01-kiem-tra-moi-truong.md` | Kết quả kiểm tra môi trường |
| `docs/02-khao-sat-subradar.md` | Khảo sát Sub Radar (đã xác minh / suy luận) |
| `docs/*.pptx`, `docs/*.docx` | Slide giới thiệu đề tài, Use Case, kế hoạch nhóm |

## Thiết kế (Figma)

File Figma: **[SubTrack – Luồng màn hình, Wireframe, Mockup](https://www.figma.com/design/DrSCH8Nb4Zg7jVJkkWBEIu)**

Toàn bộ nằm trên trang **SubTrack** (gói Figma Starter giới hạn số trang), chia thành các vùng:

| Vùng | Nội dung |
|---|---|
| 1. Luồng màn hình | 11 màn hình thu nhỏ nối bằng mũi tên ghi thao tác (Bắt đầu, Nút + Thêm gói, Chạm vào gói, Biểu tượng thùng rác, các tab…) và các thành phần Android bên ngoài (Lịch, trình duyệt, chia sẻ, chọn nơi lưu CSV, xin quyền thông báo, WorkManager) |
| 2. Wireframe | Bản thang xám của 11 màn hình |
| 3. Mockup | 11 màn hình có màu theo giao diện thật của app (Material 3, tiếng Việt, dữ liệu là 5 gói mẫu của app) |
| Components | Biến màu lấy từ `ui/theme/Theme.kt`, kiểu chữ Roboto, 27 icon Material và các component: thanh trạng thái, thanh điều hướng, dòng gói đăng ký, chip, ô nhập liệu, nút, dòng cài đặt, nút nổi |

Danh sách màn hình: M1 Giới thiệu lần đầu · M2 Gói đăng ký · M3 Gói đăng ký – trống · M4 Thêm gói – lỗi kiểm tra dữ liệu · M5 Sửa gói (form đầy đủ) · M6 Hộp thoại xác nhận xóa · M7 Tổ chức – Tag · M8 Hộp thoại tạo tag · M9 Báo cáo chi phí · M10 Cài đặt · M11 Thông báo nhắc hạn.

## Chạy bằng Android Studio

1. Mở Android Studio → **File › Open** → chọn thư mục `android` bên trong repo (không chọn thư mục gốc của repo).
2. Đợi Gradle Sync xong (lần đầu cần Internet để tải thư viện). Android Studio tự tạo `local.properties` trỏ tới Android SDK.
3. Chọn thiết bị chạy trên thanh công cụ:
   - **Máy ảo:** chọn `Pixel_8` (đã có sẵn) hoặc tạo mới ở **Tools › Device Manager › Create Virtual Device**. Máy ảo cần khá nhiều RAM; nếu máy yếu nên dùng điện thoại thật.
   - **Điện thoại thật:** bật **Tùy chọn nhà phát triển** (vào Cài đặt › Thông tin điện thoại, chạm 7 lần vào *Số hiệu bản tạo*), bật **Gỡ lỗi USB**, cắm cáp và chấp nhận hộp thoại "Cho phép gỡ lỗi USB". Điện thoại sẽ hiện trong danh sách thiết bị.
4. Bấm nút **Run ▶** (Shift + F10). Ứng dụng tên **SubTrack** được cài và mở tự động.

Yêu cầu: Android 8.0 (API 26) trở lên. Dự án dùng AGP 9.3.3, Gradle 9.5.0 (wrapper), compileSdk/targetSdk 37, JDK 17+ (JBR đi kèm Android Studio).

## Build APK debug bằng dòng lệnh

```bat
cd android
gradlew.bat assembleDebug
```

macOS/Linux: `./gradlew assembleDebug`. Nếu báo không tìm thấy Java, đặt `JAVA_HOME` tới JBR của Android Studio, ví dụ `C:\Program Files\Android\Android Studio\jbr`.

File APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Cài lên điện thoại đang kết nối bằng `adb install -r app-debug.apk`, hoặc chép file vào điện thoại rồi mở để cài (cần cho phép cài từ nguồn không xác định).

Chạy kiểm thử: `gradlew.bat testDebugUnitTest lintDebug`.

## Chức năng đã hoàn thành

- **Giới thiệu lần đầu** (onboarding) khi mở app lần đầu.
- **Gói đăng ký**: thẻ tổng quan (chi phí tháng ước tính, số gói đang hoạt động, số gói đến hạn trong 7 ngày); danh sách có vạch màu báo gia hạn (đỏ ≤ 7 ngày, cam ≤ 30 ngày), chip tag và nền tảng; tìm theo tên, tag, nền tảng, danh mục; lọc theo trạng thái và danh mục; sắp xếp theo ngày, chi phí, tên; trạng thái đang tải / lỗi / trống / không có kết quả.
- **Thêm / sửa / xóa gói**: tên, số tiền, tiền tệ (VND/USD/EUR), chu kỳ (tuần/tháng/quý/năm), ngày thanh toán kế tiếp (DatePicker tiếng Việt), danh mục, trạng thái (đang hoạt động / tạm dừng / đã hủy), nhắc trước (không nhắc, đúng ngày, 1/3/7/30 ngày), tag (tạo tag ngay trong form), nền tảng, liên kết quản lý (mở trình duyệt), ghi chú. Có kiểm tra dữ liệu (tên bắt buộc, tối đa 60 ký tự, số tiền hợp lệ và không âm, URL phải là http/https), hộp thoại xác nhận xóa, thông báo đã lưu / đã xóa.
- **Thêm vào Lịch**: mở ứng dụng Lịch của Android với sự kiện lặp theo chu kỳ.
- **Màn hình xem gói**: chạm vào gói để xem thẻ tổng quan, lịch sử chi trả và mục "Kỳ này" với các thao tác Đã trả, Bỏ qua kỳ này, Nhắc lại sau, Đã hủy; có Hoàn tác, và Kích hoạt lại (chọn ngày thanh toán mới).
- **Xác nhận từng kỳ**: app không tự chuyển ngày thanh toán; gói quá hạn hiển thị "Chờ xác nhận" cho đến khi người dùng xác nhận.
- **Lịch sử chi trả**: mỗi lần đánh dấu đã trả được lưu lại; xóa gói vẫn giữ lịch sử chi tiêu trong Báo cáo.
- **Tổ chức**: tạo / sửa / xóa tag có màu (chặn trùng tên); thống kê số gói theo nền tảng; chi phí tháng theo danh mục.
- **Báo cáo**: tổng chi phí mỗi tuần / tháng / quý / năm; mục "Chi thực tế" (tháng này, năm nay, 6 tháng gần nhất, theo danh mục trong 12 tháng) bên cạnh mục "Ước tính"; biểu đồ tròn cơ cấu chi phí (gộp "Các gói khác" khi nhiều hơn 6 gói); chi phí theo danh mục; 3 gói tốn kém nhất; chọn tiền tệ báo cáo; gói và khoản chi khác tiền tệ được quy đổi theo tỷ giá.
- **Tỷ giá**: lần đầu mở app mỗi ngày tự lấy tỷ giá VND/USD/EUR từ ExchangeRate-API (bản miễn phí, không cần key) và lưu lại trên máy; nút "Cập nhật" (có trạng thái đang tải) ở đầu Báo cáo và thẻ Danh mục của Tổ chức. Lấy lỗi (ví dụ không có mạng) thì hiện dòng "Hôm nay chưa cập nhật tỷ giá" ngay tại chỗ và tiếp tục dùng tỷ giá gần nhất. Dùng để quy đổi thẻ tổng chi phí, chi phí theo danh mục và Báo cáo.
- **Định dạng số tiền thống nhất** theo kiểu Việt Nam cho mọi tiền tệ: dấu chấm tách nghìn, dấu phẩy trước phần lẻ (`1.000.000 ₫`, `1.000.000,50 US$`). Ô nhập số tiền dùng cùng quy tắc; nhập `9.99` cho USD sẽ báo lỗi thay vì đoán.
- **Nhắc hạn**: WorkManager kiểm tra mỗi ngày và ngay sau khi lưu gói; nhắc một lần khi vào khoảng nhắc, một lần vào đúng ngày đến hạn, và mỗi 3 ngày khi quá hạn; có nhắc lại sau; thông báo có nút "Đã trả" và "Nhắc sau 1 ngày"; xin quyền thông báo trên Android 13+; nút gửi thông báo thử.
- **Cài đặt**: tiền tệ mặc định, bật/tắt nhắc hạn, xuất CSV (UTF-8, mở được bằng Excel), chia sẻ báo cáo chi phí dạng văn bản, thêm 5 gói mẫu (ghi chú rõ "Gói mẫu để demo"), xóa toàn bộ dữ liệu (có xác nhận), giới thiệu.
- **Lưu trữ**: SQLite trên máy (bảng `subscriptions`, `tags`, `subscription_tags`, `payments`), truy cập qua Repository; đóng mở app dữ liệu vẫn còn. Nâng cấp dữ liệu từ bản v1 (nhánh main) và v3 (nhánh tuan3) không mất gói/tag; bản v2 không được hỗ trợ.
- **Việt hóa**: mọi chuỗi nằm trong `res/values/strings.xml` (tiếng Việt là mặc định); ứng dụng ép locale `vi-VN` nên DatePicker, định dạng tiền (260.000 ₫) và ngày (dd/MM/yyyy) đều theo tiếng Việt dù máy đặt ngôn ngữ khác. Chỉ giữ nguyên tên riêng (Android, iPhone, PlayStation…) và mã tiền tệ.
- **Giao diện sáng / tối** theo cài đặt hệ thống.

## Đã kiểm thử

- `assembleDebug`: build thành công.
- `testDebugUnitTest`: 59/59 test qua, gồm: quy đổi chi phí theo chu kỳ, tổng chi theo tiền tệ, cuộn ngày thanh toán, cuối tháng, mức báo gia hạn, đọc số tiền VND/USD, kiểm tra URL, điều kiện nhắc hạn, nội dung thông báo theo số ngày còn lại, số liệu báo cáo, CSV, định dạng và đọc số tiền theo một quy tắc, quy đổi tiền tệ (BillingMathTest: 27); hành động thanh toán như Đã trả, Bỏ qua, Nhắc lại sau, Hoàn tác, Kích hoạt lại và trạng thái nhắc hạn (PaymentActionsTest: 11); cơ sở dữ liệu SQLite, lịch sử chi trả và nâng cấp schema từ v1/v3 lên v4 (SubTrackDatabaseTest: 7); đọc phản hồi API tỷ giá, lưu và dùng lại khi mất mạng, chặn bấm cập nhật liên tục (RatesRepositoryTest: 4); CRUD trên CSDL tuần 3 (SubTrackDatabaseCrudTest: 9, CrudDemoTest: 1). Unit test đã phát hiện và giúp sửa một lỗi đọc số tiền (`1.2.3.4` từng được chấp nhận).
- `lintDebug`: không còn lỗi.
- **Chạy thử trên máy ảo** (AVD API 36.1): nâng cấp CSDL giữ nguyên gói cũ; màn hình xem gói; Đã trả (ghi lịch sử, chuyển kỳ, snackbar Hoàn tác); hoàn tác bằng nút ↶ trong lịch sử; mục "Chi thực tế" trong Báo cáo; thông báo nhắc hạn có nút "Đã trả" (bấm xong thông báo biến mất, gói chuyển kỳ); chạm vào thông báo mở đúng màn hình của gói; kiểm tra lại trong cùng ngày không nhắc trùng. Không có lỗi crash trong logcat.

## Chưa hoàn thành / giới hạn

- **Chưa chạy thử trên máy thật**, và trên máy ảo chưa thử: thông báo khi gói đã quá hạn, nút "Nhắc sau 1 ngày", "Bỏ qua kỳ này", xóa gói đã có lịch sử chi trả (đã có unit test cho trường hợp này).
- **Lịch sử chi trả** chỉ có từ khi người dùng bấm "Đã trả"; các kỳ trước khi cài bản này và các kỳ không được xác nhận không có trong "Chi thực tế". Lần đầu chạy sau khi nâng cấp, gói đang trong khoảng nhắc có thể được nhắc thêm một lần.
- **Không đồng bộ đám mây / không có tài khoản**: Sub Radar dùng iCloud. Kế hoạch nhóm xếp đăng nhập vào phần mở rộng; muốn làm thì cần một dự án Firebase (file `google-services.json`, bật Authentication và Firestore).
- **Tỷ giá**: chưa chạy thử trên máy ảo/máy thật (mới kiểm thử ở tầng code). Chi thực tế của các kỳ cũ được quy đổi theo tỷ giá hiện tại vì API miễn phí không có tỷ giá lịch sử. Khi chưa từng lấy được tỷ giá, app chỉ cộng các gói cùng tiền tệ và ghi chú số gói bị loại.
- **Nền tảng cố định**: có 12 nền tảng có sẵn, chưa cho tự tạo nền tảng như Sub Radar.
- **Xuất dữ liệu** chỉ hỗ trợ CSV (Sub Radar có thêm JSON và văn bản thuần), chưa kèm lịch sử chi trả; chưa có nhập lại dữ liệu từ file.
- **Thông báo hằng ngày** chạy theo lịch của WorkManager nên giờ nhắc không cố định chính xác.

## API, biến môi trường, quyền

- Không cần API key, backend hay biến môi trường nào. Không có khóa bí mật trong mã nguồn.
- Tỷ giá: `GET https://open.er-api.com/v6/latest/USD` (ExchangeRate-API, bản open access). Điều kiện sử dụng yêu cầu ghi nguồn, nên app hiện dòng "Rates By Exchange Rate API" dưới nút Cập nhật.
- Quyền Android: `POST_NOTIFICATIONS` (Android 13+, xin khi bật nhắc hạn), `INTERNET` (lấy tỷ giá). Thêm vào Lịch và mở liên kết dùng Intent của hệ thống, không cần quyền riêng.
