# Hướng dẫn chạy demo tuần 3 trên máy khác

Tài liệu này dùng cho các thành viên muốn chuyển sang nhánh `tuan3`, chạy app và nạp **10 tag + 10 gói đăng ký mẫu** để demo thêm / sửa / xóa.

> **Dữ liệu không đi theo git.** Gói và tag bạn đã nhập nằm trong file `subtrack.db` trên máy ảo / điện thoại, không nằm trong repo. Đổi nhánh hay `git pull` không làm mất và cũng không mang theo dữ liệu đó. Dữ liệu mẫu chỉ có khi chạy file `demo/seed_data.sql`.

Các file trong thư mục `demo/`:

| File | Dùng để |
|---|---|
| `seed_data.sql` | Lệnh SQL thêm 10 tag, 10 gói, 18 liên kết gói–tag. Chạy nhiều lần không bị trùng |
| `seed_demo.bat` | Script Windows làm tự động mọi bước ở mục 3 (khuyên dùng) |
| `HUONG_DAN_TUAN3.md` | Tài liệu này |

---

## 1. Chuyển sang nhánh `tuan3`

### Cách A – trong Android Studio

1. Mở dự án (thư mục `android`).
2. Góc trên bên trái, bấm tên nhánh (đang là `main`) › **Fetch** (biểu tượng mũi tên tròn) để tải danh sách nhánh mới.
3. Mục **Remote › origin › tuan3** › **Checkout**.
4. Nếu Android Studio hỏi về thay đổi chưa commit: chọn **Smart Checkout** để giữ lại, hoặc commit trước rồi checkout.

### Cách B – dòng lệnh (Git Bash, cmd hoặc Terminal của Android Studio, đứng ở thư mục gốc repo)

```bash
git status
```
Nếu có file đã sửa mà chưa commit, cất tạm trước (lấy lại bằng `git stash pop` khi quay về nhánh cũ):
```bash
git stash -u
```
Tải nhánh và chuyển sang:
```bash
git fetch origin
git switch tuan3
```
Lần sau muốn cập nhật nhánh:
```bash
git pull
```

> Nhánh `tuan3` đã **bỏ** các màn hình Báo cáo, Cài đặt, nhắc hạn… để chỉ giữ phần thêm/sửa/xóa. Muốn xem lại bản đầy đủ thì `git switch main`.

## 2. Build và chạy app

1. Android Studio sẽ tự **Gradle Sync** sau khi đổi nhánh (nếu không: **File › Sync Project with Gradle Files**). Lần đầu tải thêm thư viện test, cần Internet.
2. Chọn máy ảo ở ô thiết bị, cấu hình chạy **app**, bấm **Run ▶**.
3. App mở ra: CSDL được tạo mới hoặc **tự nâng cấp lên version 3** nếu máy đã có dữ liệu từ bản cũ (nhánh `main`). Dữ liệu cũ được giữ nguyên.

## 3. Nạp dữ liệu mẫu

Trước tiên, xác định máy bạn thuộc trường hợp nào:

| Tình trạng máy | Nên chọn |
|---|---|
| Chưa từng chạy SubTrack, hoặc app đang trống | Chế độ **giữ** (kết quả như nhau) |
| Đã có dữ liệu tự nhập (từ `main` hoặc `tuan3`) và **muốn giữ** | Chế độ **giữ**: chỉ thêm tag/gói mẫu còn thiếu. Gói hoặc tag đã có **cùng tên** sẽ giữ nguyên, không bị ghi đè |
| Đã có dữ liệu cũ, muốn **demo sạch** đúng 10 tag + 10 gói | Chế độ **xóa**: xóa toàn bộ dữ liệu của app rồi nạp mẫu. **Không khôi phục được** |
| Đã nạp mẫu trước đó, chạy lại | Chế độ **giữ**: không thêm gì trùng |

### Cách A – script tự động (Windows)

Máy ảo phải đang chạy và đã cài app bằng nút Run (mục 2). Ở thư mục gốc repo, mở **cmd** hoặc nhấp đúp file `demo\seed_demo.bat`:

```bat
demo\seed_demo.bat
```
Script hiện menu chọn 1 (giữ) / 2 (xóa) / 3 (thoát). Hoặc chọn sẵn:
```bat
demo\seed_demo.bat giu
```
```bat
demo\seed_demo.bat xoa
```

Script sẽ: tìm `adb` → kiểm tra có thiết bị, đã cài app, máy ảo có `sqlite3` → (chế độ xóa: hỏi xác nhận, xóa dữ liệu app) → mở app một lần để tạo/nâng cấp CSDL → kiểm tra CSDL đúng version 3 → nạp `seed_data.sql` → mở lại app.

### Cách B – gõ lệnh thủ công

Dùng **cmd** (không dùng PowerShell vì PowerShell không hỗ trợ dấu `<` và dễ làm hỏng chữ tiếng Việt). Đứng ở thư mục gốc repo.

**B1. Đặt đường dẫn adb** (nếu `adb` chưa có trong PATH):
```bat
set ADB="%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
```

**B2. Kiểm tra thiết bị và app:**
```bat
%ADB% devices
```
```bat
%ADB% shell pm path com.nhom4.subtrack
```
Phải thấy một dòng `emulator-5554  device` và một dòng `package:...`.

**B3. (Chỉ khi muốn xóa sạch dữ liệu cũ)** Xóa dữ liệu app, sau đó mở app một lần để tạo CSDL mới:
```bat
%ADB% shell pm clear com.nhom4.subtrack
```
```bat
%ADB% shell am start -n com.nhom4.subtrack/.MainActivity
```
Đợi app hiện "Chưa có gói đăng ký nào".

**B4. Kiểm tra CSDL đã là version 3** (phải in ra `3`; nếu ra `1` hoặc `2` thì app chưa phải bản `tuan3`, quay lại mục 2):
```bat
%ADB% shell "run-as com.nhom4.subtrack sqlite3 databases/subtrack.db 'PRAGMA user_version;'"
```

**B5. Tắt app rồi nạp dữ liệu mẫu:**
```bat
%ADB% shell am force-stop com.nhom4.subtrack
```
```bat
%ADB% shell "run-as com.nhom4.subtrack sqlite3 databases/subtrack.db" < demo\seed_data.sql
```
Kết quả in ra số dòng mỗi bảng, ví dụ với máy trống: `tags: 10`, `subscriptions: 10`, `subscription_tags: 18`.

**B6. Mở lại app:**
```bat
%ADB% shell am start -n com.nhom4.subtrack/.MainActivity
```

**macOS / Linux:** giống hệt, thay `%ADB%` bằng `~/Library/Android/sdk/platform-tools/adb` (macOS) hoặc `~/Android/Sdk/platform-tools/adb` (Linux).

### Cách C – khi máy ảo không có `sqlite3`

Ảnh hệ thống loại **"Google Play"** thường không có lệnh `sqlite3` (script sẽ báo lỗi). Hai cách:

1. Tạo máy ảo mới bằng ảnh **"Google APIs"** (Device Manager › Create Virtual Device › chọn ảnh có chữ *Google APIs*, không có biểu tượng Play Store).
2. Hoặc dùng **Database Inspector**: chạy app bằng Run, mở **App Inspection › Database Inspector**, bấm **Open New Query Tab**, rồi dán và chạy **lần lượt từng câu lệnh** trong `seed_data.sql`: câu `INSERT … INTO tags`, câu `WITH v … INSERT INTO subscriptions`, câu `WITH m … INSERT … INTO subscription_tags`. Bỏ qua các dòng `PRAGMA`, `BEGIN`, `COMMIT`.

## 4. Kiểm tra trong Database Inspector

1. **View › Tool Windows › App Inspection › Database Inspector**.
2. Ở dòng chọn tiến trình phải là `com.nhom4.subtrack` (không có chữ **[DETACHED]**). Nếu có, bấm vào và chọn lại tiến trình `com.nhom4.subtrack`: việc script tắt/mở lại app làm Inspector mất kết nối.
3. CSDL `subtrack.db` có **3 bảng**: `subscriptions`, `tags`, `subscription_tags`. Nhấp đúp để mở, tích **Live updates**.

## 5. Lỗi thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| `adb: device offline` / không thấy thiết bị | Mở máy ảo trong Device Manager, đợi khởi động xong; hoặc `%ADB% kill-server` rồi thử lại |
| `run-as: package not debuggable` | App đang cài là bản release. Gỡ app, chạy lại bằng nút Run ▶ (bản debug) |
| `no such table: subscriptions` | CSDL chưa được tạo: mở app một lần (B3) rồi nạp lại |
| `no such column: platforms` hoặc version khác 3 | App là bản cũ: chuyển sang nhánh `tuan3`, Run lại |
| Chữ tiếng Việt bị lỗi font sau khi nạp | Đã chạy lệnh trong PowerShell. Xóa sạch (chế độ xóa) và nạp lại bằng cmd hoặc `seed_demo.bat` |
| Danh sách trong app chưa đổi | Tắt hẳn app và mở lại (B6) |
| Có gói bị trùng tên với gói mẫu | Script không ghi đè gói cùng tên. Muốn dữ liệu đúng như mẫu thì dùng chế độ **xóa** |
