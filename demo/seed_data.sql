-- =====================================================================
-- SubTrack – Dữ liệu mẫu tuần 3: 10 tag + 10 gói đăng ký
-- Dùng cho CSDL version 3 (app nhánh tuan3 đã được mở ít nhất một lần).
--
-- Chạy được nhiều lần, KHÔNG tạo trùng:
--   - Tag đã có cùng tên (không phân biệt hoa/thường với chữ không dấu) thì bỏ qua.
--   - Gói đã có cùng tên thì bỏ qua (không ghi đè dữ liệu cũ).
--   - Liên kết gói–tag đã có thì bỏ qua.
--
-- Cách chạy: xem demo/HUONG_DAN_TUAN3.md hoặc dùng demo/seed_demo.bat
-- =====================================================================
PRAGMA foreign_keys = ON;
BEGIN;

-- ---------- 10 tag ----------
INSERT OR IGNORE INTO tags (name, color) VALUES
  ('Giải trí',   4293216333),
  ('Học tập',    4281377900),
  ('Thể dục',    4287335832),
  ('Gia đình',   4279346334),
  ('Công việc',  4285287665),
  ('Cá nhân',    4278227199),
  ('Dùng chung', 4294078208),
  ('Dùng thử',   4294142741),
  ('Cần hủy',    4292231327),
  ('Quan trọng', 4287335832);

-- ---------- 10 gói ----------
-- next_payment_date = số ngày tính từ 01/01/1970; created_at/updated_at = mili giây
-- platforms: khóa nền tảng cách nhau dấu phẩy, theo thứ tự android, iphone, ipad, tablet,
--            web, windows, mac, smart_tv, playstation, xbox, nintendo, other
WITH v(name, amount, currency, billing_cycle, pay_date, category, status, remind, url, notes, platforms) AS (VALUES
  ('Netflix Premium',      260000,  'VND', 'MONTHLY',   '2026-11-18', 'entertainment', 'ACTIVE',    3,    'https://www.netflix.com/account',          'Chia sẻ 4 màn hình cho cả nhà', 'android,smart_tv'),
  ('Spotify Premium',      59000,   'VND', 'MONTHLY',   '2026-11-25', 'music',         'ACTIVE',    1,    'https://www.spotify.com/account',          NULL,                            'android,windows'),
  ('YouTube Premium',      79000,   'VND', 'MONTHLY',   '2026-12-02', 'entertainment', 'ACTIVE',    3,    'https://www.youtube.com/paid_memberships', 'Gói gia đình',                  'android,web,smart_tv'),
  ('Google One 100 GB',    45000,   'VND', 'MONTHLY',   '2026-11-15', 'storage',       'ACTIVE',    0,    'https://one.google.com',                   'Sao lưu ảnh điện thoại',        'android,web'),
  ('iCloud+ 50 GB',        19000,   'VND', 'MONTHLY',   '2026-12-08', 'storage',       'ACTIVE',    NULL, NULL,                                       NULL,                            'iphone,mac'),
  ('Microsoft 365 Family', 1499000, 'VND', 'YEARLY',    '2027-03-10', 'work',          'ACTIVE',    7,    'https://account.microsoft.com/services',   'Dùng chung 6 tài khoản',        'web,windows,mac'),
  ('ChatGPT Plus',         20,      'USD', 'MONTHLY',   '2026-11-20', 'work',          'ACTIVE',    3,    'https://chatgpt.com',                      'Thanh toán bằng thẻ Visa',      'android,web'),
  ('Duolingo Super',       899000,  'VND', 'YEARLY',    '2027-01-05', 'education',     'PAUSED',    7,    NULL,                                       'Tạm dừng trong kỳ thi',         'android'),
  ('Coursera Plus',        59,      'EUR', 'MONTHLY',   '2026-11-30', 'education',     'CANCELLED', NULL, 'https://www.coursera.org/account-profile', 'Đã hủy, hết hạn cuối tháng',    'web'),
  ('Phòng gym',            1500000, 'VND', 'QUARTERLY', '2027-01-01', 'other',         'ACTIVE',    3,    NULL,                                       'Gói 3 tháng',                   '')
)
INSERT INTO subscriptions (name, amount, currency, billing_cycle, next_payment_date, category, status,
                           remind_days_before, management_url, notes, platforms, created_at, updated_at)
SELECT v.name, v.amount, v.currency, v.billing_cycle,
       CAST(julianday(v.pay_date) - 2440587.5 AS INTEGER),
       v.category, v.status, v.remind, v.url, v.notes, v.platforms,
       CAST(strftime('%s', 'now') AS INTEGER) * 1000, CAST(strftime('%s', 'now') AS INTEGER) * 1000
FROM v
WHERE NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.name = v.name);

-- ---------- Gắn tag cho gói (tìm theo tên) ----------
WITH m(sub, tag) AS (VALUES
  ('Netflix Premium',      'Giải trí'),
  ('Netflix Premium',      'Gia đình'),
  ('Spotify Premium',      'Giải trí'),
  ('Spotify Premium',      'Cá nhân'),
  ('YouTube Premium',      'Giải trí'),
  ('YouTube Premium',      'Gia đình'),
  ('YouTube Premium',      'Dùng chung'),
  ('Google One 100 GB',    'Cá nhân'),
  ('Google One 100 GB',    'Quan trọng'),
  ('iCloud+ 50 GB',        'Cá nhân'),
  ('Microsoft 365 Family', 'Công việc'),
  ('Microsoft 365 Family', 'Dùng chung'),
  ('ChatGPT Plus',         'Công việc'),
  ('ChatGPT Plus',         'Quan trọng'),
  ('Duolingo Super',       'Học tập'),
  ('Coursera Plus',        'Học tập'),
  ('Coursera Plus',        'Cần hủy'),
  ('Phòng gym',            'Thể dục')
)
INSERT OR IGNORE INTO subscription_tags (subscription_id, tag_id)
SELECT s.id, t.id
FROM m
JOIN subscriptions s ON s.name = m.sub
JOIN tags t ON t.name = m.tag;

COMMIT;

-- Kết quả sau khi nạp
SELECT 'tags: '              || COUNT(*) FROM tags;
SELECT 'subscriptions: '     || COUNT(*) FROM subscriptions;
SELECT 'subscription_tags: ' || COUNT(*) FROM subscription_tags;
