@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
rem =====================================================================
rem  SubTrack - nạp dữ liệu mẫu tuần 3 vào máy ảo / điện thoại đang kết nối
rem  Cách dùng:  demo\seed_demo.bat          (hiện menu chọn)
rem              demo\seed_demo.bat giu      (giữ dữ liệu cũ, thêm dữ liệu mẫu)
rem              demo\seed_demo.bat xoa      (xóa sạch dữ liệu app rồi nạp mẫu)
rem  Yêu cầu: app nhánh tuan3 đã được cài bằng nút Run trong Android Studio.
rem =====================================================================
set "PKG=com.nhom4.subtrack"
set "DB=databases/subtrack.db"
set "SEED=%~dp0seed_data.sql"

rem ---------- Tìm adb ----------
set "ADB="
if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB (
  where adb >nul 2>&1 && set "ADB=adb"
)
if not defined ADB (
  echo [LỖI] Không tìm thấy adb. Mở Android Studio ^> SDK Manager ^> SDK Tools, cài "Android SDK Platform-Tools".
  goto :fail
)
echo adb: %ADB%

rem ---------- Kiểm tra thiết bị, app, sqlite3 ----------
"%ADB%" get-state >nul 2>&1
if errorlevel 1 (
  echo [LỖI] Chưa có máy ảo/điện thoại nào kết nối. Mở máy ảo trong Android Studio rồi chạy lại.
  goto :fail
)
"%ADB%" shell pm path %PKG% | findstr /b "package:" >nul
if errorlevel 1 (
  echo [LỖI] Máy chưa cài SubTrack. Trong Android Studio chọn nhánh tuan3, bấm Run rồi chạy lại script.
  goto :fail
)
"%ADB%" shell which sqlite3 | findstr "sqlite3" >nul
if errorlevel 1 (
  echo [LỖI] Máy ảo này không có lệnh sqlite3 ^(thường gặp ở ảnh hệ thống "Google Play"^).
  echo        Tạo máy ảo dùng ảnh "Google APIs", hoặc nạp bằng Database Inspector ^(xem HUONG_DAN_TUAN3.md^).
  goto :fail
)

rem ---------- Chọn chế độ ----------
set "MODE=%~1"
if /i "%MODE%"=="giu" goto :mode_ok
if /i "%MODE%"=="xoa" goto :mode_ok
set "MODE="
echo.
echo Chọn cách nạp dữ liệu mẫu:
echo   1. Giữ dữ liệu đang có, chỉ thêm tag/gói mẫu còn thiếu ^(không tạo trùng^)
echo   2. XÓA SẠCH dữ liệu của app rồi nạp dữ liệu mẫu ^(không khôi phục được^)
echo   3. Thoát
set /p "CHOICE=Nhập 1, 2 hoặc 3: "
if "%CHOICE%"=="1" set "MODE=giu"
if "%CHOICE%"=="2" set "MODE=xoa"
if not defined MODE goto :end
:mode_ok

if /i "%MODE%"=="xoa" (
  set /p "OK=Xóa toàn bộ gói và tag hiện có trên máy? Gõ CO để đồng ý: "
  if /i not "!OK!"=="CO" (
    echo Đã hủy, không thay đổi gì.
    goto :end
  )
  echo Đang xóa dữ liệu app...
  "%ADB%" shell pm clear %PKG% >nul
)

rem ---------- Mở app một lần để tạo / nâng cấp CSDL lên version 3 ----------
echo Đang mở app để tạo / nâng cấp CSDL...
"%ADB%" shell am start -n %PKG%/.MainActivity >nul
timeout /t 5 /nobreak >nul
"%ADB%" shell am force-stop %PKG%

"%ADB%" shell "run-as %PKG% sqlite3 %DB% 'PRAGMA user_version;'" | findstr /b "3" >nul
if errorlevel 1 (
  echo [LỖI] CSDL trên máy không phải version 3. App đang cài là bản cũ:
  echo        trong Android Studio chuyển sang nhánh tuan3, bấm Run, rồi chạy lại script.
  goto :fail
)

rem ---------- Nạp dữ liệu ----------
echo Đang nạp dữ liệu mẫu ^(%MODE%^)...
"%ADB%" shell "run-as %PKG% sqlite3 %DB%" < "%SEED%"
if errorlevel 1 (
  echo [LỖI] Nạp dữ liệu thất bại.
  goto :fail
)

"%ADB%" shell am start -n %PKG%/.MainActivity >nul
echo.
echo XONG. App đã được mở lại với dữ liệu mới.
echo Nếu đang mở Database Inspector: chọn lại tiến trình %PKG% ^(bỏ trạng thái DETACHED^).
goto :end

:fail
echo.
pause
exit /b 1

:end
endlocal
exit /b 0
