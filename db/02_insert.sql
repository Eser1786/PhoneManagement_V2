-- =====================================================================
-- 02_insert.sql  -  Dữ liệu mẫu cho phonemanagement_db
-- Chạy SAU 01_schema.sql, trên database mới (ID tự tăng bắt đầu từ 1).
-- Các dòng dưới đây tham chiếu khóa ngoại bằng ID cố định, nên nếu cần
-- chạy lại hãy xóa dữ liệu:  docker compose down -v && docker compose up -d
--
-- CHƯA có dữ liệu cho Orders và OrderDetails (sẽ tạo bằng ứng dụng / sau).
--
-- Tài khoản mẫu: mật khẩu của TẤT CẢ tài khoản là  123456
-- (lưu dạng BCrypt, chỉ dùng cho môi trường phát triển)
-- =====================================================================

SET NAMES utf8mb4;
USE phonemanagement_db;

-- ---------------------------------------------------------------------
-- Brands (ID 1..9)
-- logo_url là đường dẫn tương đối trong src/main/resources; file ảnh có thể
-- chưa tồn tại, giao diện nên xử lý trường hợp không tìm thấy ảnh.
-- ---------------------------------------------------------------------
INSERT INTO Brands(brand_name, country, logo_url, description) VALUES
('Apple',   'Mỹ',         'images/brands/apple.png',   'Hãng sản xuất iPhone, dùng hệ điều hành iOS'),
('Samsung', 'Hàn Quốc',   'images/brands/samsung.png', 'Hãng điện thoại Android lớn nhất thế giới, có dòng Galaxy S, A và Z'),
('Xiaomi',  'Trung Quốc', 'images/brands/xiaomi.png',  'Điện thoại cấu hình tốt, giá cạnh tranh, gồm các dòng Xiaomi, Redmi và Poco'),
('OnePlus', 'Trung Quốc', 'images/brands/oneplus.png', 'Điện thoại hiệu năng cao, sạc nhanh'),
('Google',  'Mỹ',         'images/brands/google.png',  'Dòng Pixel, camera tính toán và Android thuần'),
('Oppo',    'Trung Quốc', 'images/brands/oppo.png',    'Điện thoại thiên về camera chân dung và sạc nhanh'),
('Vivo',    'Trung Quốc', 'images/brands/vivo.png',    'Điện thoại chụp ảnh selfie, thiết kế mỏng nhẹ'),
('Sony',    'Nhật Bản',   'images/brands/sony.png',    'Dòng Xperia, màn hình OLED và camera theo phong cách máy ảnh'),
('LG',      'Hàn Quốc',   'images/brands/lg.png',      'Hãng đã ngừng kinh doanh điện thoại, còn lại ở dạng hàng tồn');

-- ---------------------------------------------------------------------
-- Categories (ID 1..5)
-- ---------------------------------------------------------------------
INSERT INTO Categories(category_name, description) VALUES
('Flagship',  'Điện thoại cao cấp, cấu hình và camera mạnh nhất của hãng'),
('Mid-range', 'Điện thoại tầm trung, cân bằng giữa giá và hiệu năng'),
('Budget',    'Điện thoại giá rẻ, đáp ứng nhu cầu cơ bản'),
('Gaming',    'Điện thoại tối ưu hiệu năng chơi game'),
('Foldable',  'Điện thoại màn hình gập');

-- ---------------------------------------------------------------------
-- Roles (ID 1..4) và Permissions (ID 1..10)
-- ---------------------------------------------------------------------
INSERT INTO Roles(role_name, description) VALUES
('Admin',    'Quản lý tài khoản và quyền truy cập'),
('Manager',  'Quản lý sản phẩm và đơn hàng'),
('Staff',    'Xử lý đơn hàng và hỗ trợ khách hàng'),
('Customer', 'Người dùng cuối');

INSERT INTO Permissions(permission_name, description) VALUES
('view_products',    'Xem danh sách sản phẩm'),
('add_product',      'Thêm sản phẩm mới'),
('edit_product',     'Chỉnh sửa thông tin sản phẩm'),
('delete_product',   'Xóa sản phẩm'),
('view_orders',      'Xem danh sách đơn hàng'),
('process_orders',   'Xử lý đơn hàng'),
('manage_users',     'Quản lý người dùng và quyền truy cập'),
('view_reports',     'Xem báo cáo doanh thu và hiệu suất'),
('manage_inventory', 'Quản lý nhập xuất kho'),
('manage_customers', 'Quản lý thông tin khách hàng');

-- ---------------------------------------------------------------------
-- RolePermissions (gán theo tên để không phụ thuộc ID)
--   Admin    : toàn bộ quyền
--   Manager  : sản phẩm, đơn hàng, kho, khách hàng, báo cáo (không quản lý user)
--   Staff    : xem sản phẩm, xử lý đơn hàng, quản lý khách hàng
--   Customer : chỉ xem sản phẩm
-- ---------------------------------------------------------------------
INSERT INTO RolePermissions(role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM Roles r CROSS JOIN Permissions p
WHERE r.role_name = 'Admin';

INSERT INTO RolePermissions(role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM Roles r JOIN Permissions p
WHERE r.role_name = 'Manager'
  AND p.permission_name IN ('view_products','add_product','edit_product','delete_product',
                            'view_orders','process_orders','view_reports',
                            'manage_inventory','manage_customers');

INSERT INTO RolePermissions(role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM Roles r JOIN Permissions p
WHERE r.role_name = 'Staff'
  AND p.permission_name IN ('view_products','view_orders','process_orders','manage_customers');

INSERT INTO RolePermissions(role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM Roles r JOIN Permissions p
WHERE r.role_name = 'Customer'
  AND p.permission_name IN ('view_products');

-- ---------------------------------------------------------------------
-- Users (ID 1..9): 4 nhân sự nội bộ + 5 khách hàng. Mật khẩu: 123456
-- ---------------------------------------------------------------------
INSERT INTO Users(username, email, password, full_name, phone, role_id, is_active) VALUES
('admin',     'admin@phonestore.vn',     '$2a$10$mEYqSDNk2HwbnAox4YWOnOMR6N6aXKv1cvW92D6uXpbvhGuvQDzd.', 'Nguyễn Quản Trị',  '0901000001', 1, TRUE),
('manager',   'manager@phonestore.vn',   '$2a$10$mEYqSDNk2HwbnAox4YWOnOMR6N6aXKv1cvW92D6uXpbvhGuvQDzd.', 'Trần Quản Lý',    '0901000002', 2, TRUE),
('staff1',    'staff1@phonestore.vn',    '$2a$10$mEYqSDNk2HwbnAox4YWOnOMR6N6aXKv1cvW92D6uXpbvhGuvQDzd.', 'Lê Minh Tuấn',     '0901000003', 3, TRUE),
('staff2',    'staff2@phonestore.vn',    '$2a$10$mEYqSDNk2HwbnAox4YWOnOMR6N6aXKv1cvW92D6uXpbvhGuvQDzd.', 'Phạm Thu Hà',      '0901000004', 3, TRUE),
('customer1', 'customer1@example.com',   '$2a$10$mEYqSDNk2HwbnAox4YWOnOMR6N6aXKv1cvW92D6uXpbvhGuvQDzd.', 'Nguyễn Văn An',    '0912000001', 4, TRUE),
('customer2', 'customer2@example.com',   '$2a$10$mEYqSDNk2HwbnAox4YWOnOMR6N6aXKv1cvW92D6uXpbvhGuvQDzd.', 'Trần Thị Bích',    '0912000002', 4, TRUE),
('customer3', 'customer3@example.com',   '$2a$10$mEYqSDNk2HwbnAox4YWOnOMR6N6aXKv1cvW92D6uXpbvhGuvQDzd.', 'Lê Hoàng Cường',   '0912000003', 4, TRUE),
('customer4', 'customer4@example.com',   '$2a$10$mEYqSDNk2HwbnAox4YWOnOMR6N6aXKv1cvW92D6uXpbvhGuvQDzd.', 'Phạm Ngọc Dung',   '0912000004', 4, TRUE),
('customer5', 'customer5@example.com',   '$2a$10$mEYqSDNk2HwbnAox4YWOnOMR6N6aXKv1cvW92D6uXpbvhGuvQDzd.', 'Võ Quốc Em',       '0912000005', 4, FALSE);

-- ---------------------------------------------------------------------
-- Staff (user_id 1..4)
-- ---------------------------------------------------------------------
INSERT INTO Staff(user_id, position, salary, hire_date) VALUES
(1, 'Quản trị hệ thống',     20000000.00, '2023-01-02'),
(2, 'Quản lý cửa hàng',      18000000.00, '2023-03-15'),
(3, 'Nhân viên bán hàng',    10000000.00, '2024-02-01'),
(4, 'Nhân viên bán hàng',    10000000.00, '2024-06-10');

-- ---------------------------------------------------------------------
-- Customers (user_id 5..9). total_spent = 0 vì chưa có đơn hàng;
-- hạng thành viên sẽ được cập nhật theo tổng chi tiêu khi có đơn.
-- ---------------------------------------------------------------------
INSERT INTO Customers(user_id, address, city, membership_level, total_spent) VALUES
(5, '12 Nguyễn Trãi, Phường Bến Thành, Quận 1',     'TP. Hồ Chí Minh', 'Bronze', 0.00),
(6, '45 Lê Văn Sỹ, Phường 14, Quận 3',              'TP. Hồ Chí Minh', 'Bronze', 0.00),
(7, '78 Trần Hưng Đạo, Phường Cầu Ông Lãnh, Quận 1','TP. Hồ Chí Minh', 'Bronze', 0.00),
(8, '23 Hai Bà Trưng, Quận Hoàn Kiếm',              'Hà Nội',          'Bronze', 0.00),
(9, '9 Bạch Đằng, Quận Hải Châu',                   'Đà Nẵng',         'Bronze', 0.00);

-- ---------------------------------------------------------------------
-- Products (ID 1..16)
-- brand_id   : 1 Apple, 2 Samsung, 3 Xiaomi, 4 OnePlus, 5 Google, 6 Oppo, 7 Vivo, 8 Sony, 9 LG
-- category_id: 1 Flagship, 2 Mid-range, 3 Budget, 4 Gaming, 5 Foldable
-- Thông số và giá chỉ mang tính minh họa.
-- ---------------------------------------------------------------------
INSERT INTO Products(name, brand_id, category_id, description) VALUES
('iPhone 15 Pro',        1, 1, 'Khung titan, chip A17 Pro, camera chính 48MP, cổng USB-C'),
('iPhone 16',            1, 2, 'Chip A18, nút Camera Control, camera chính 48MP'),
('Galaxy S24 Ultra',     2, 1, 'Màn hình 6.8 inch, bút S Pen, camera chính 200MP, Galaxy AI'),
('Galaxy A55 5G',        2, 2, 'Khung kim loại, màn hình Super AMOLED 120Hz, kháng nước IP67'),
('Galaxy Z Fold5',       2, 5, 'Điện thoại gập dạng sách, màn hình trong 7.6 inch'),
('Xiaomi 14',            3, 1, 'Chip Snapdragon 8 Gen 3, camera hợp tác Leica, sạc nhanh 90W'),
('Redmi Note 13 Pro 5G', 3, 2, 'Camera chính 200MP, màn hình AMOLED 120Hz, pin 5100mAh'),
('Redmi 13C',            3, 3, 'Máy giá rẻ pin 5000mAh, màn hình 6.74 inch 90Hz'),
('Poco F6',              3, 4, 'Chip Snapdragon 8s Gen 3, tản nhiệt lớn, tối ưu chơi game'),
('OnePlus 12',           4, 1, 'Snapdragon 8 Gen 3, camera Hasselblad, sạc 100W'),
('Pixel 8',              5, 1, 'Chip Tensor G3, Android thuần, hỗ trợ cập nhật 7 năm'),
('Oppo Reno11 5G',       6, 2, 'Camera chân dung 50MP, thiết kế mặt lưng bo cong'),
('Oppo A78',             6, 3, 'Pin 5000mAh, sạc nhanh 33W, màn hình AMOLED 90Hz'),
('Vivo V30',             7, 2, 'Camera selfie 50MP, camera Zeiss, pin 5000mAh sạc 80W'),
('Vivo Y28',             7, 3, 'Máy giá rẻ pin 5000mAh, màn hình 6.56 inch'),
('Sony Xperia 1 V',      8, 1, 'Màn hình OLED 4K 21:9, camera cảm biến Exmor T, jack 3.5mm');

-- ---------------------------------------------------------------------
-- ProductSpecs (ID 1..19): mỗi dòng là một phiên bản (dung lượng / màu)
-- Cột: product_id, price, cost_price, stock_quantity, screen_size, processor,
--      resolution, ram, storage, battery, camera_main, camera_front, weight,
--      os, color, warranty_period, status
-- status: 'Đang bán' | 'Hết hàng' | 'Ngừng kinh doanh'
-- ---------------------------------------------------------------------
INSERT INTO ProductSpecs(product_id, price, cost_price, stock_quantity, screen_size, processor, resolution,
                         ram, storage, battery, camera_main, camera_front, weight, os, color,
                         warranty_period, status) VALUES
-- 1 iPhone 15 Pro
(1, 27990000, 25500000, 15, '6.1 inch',  'Apple A17 Pro',          '2556x1179', '8GB',  '128GB', '3274 mAh', '48MP',  '12MP',   '187 g', 'iOS 17',       'Titan tự nhiên', '12 tháng', 'Đang bán'),
(1, 32990000, 30000000, 10, '6.1 inch',  'Apple A17 Pro',          '2556x1179', '8GB',  '256GB', '3274 mAh', '48MP',  '12MP',   '187 g', 'iOS 17',       'Titan xanh',     '12 tháng', 'Đang bán'),
-- 2 iPhone 16
(2, 22990000, 20800000, 20, '6.1 inch',  'Apple A18',              '2556x1179', '8GB',  '128GB', '3561 mAh', '48MP',  '12MP',   '170 g', 'iOS 18',       'Đen',            '12 tháng', 'Đang bán'),
(2, 25990000, 23500000,  8, '6.1 inch',  'Apple A18',              '2556x1179', '8GB',  '256GB', '3561 mAh', '48MP',  '12MP',   '170 g', 'iOS 18',       'Xanh mòng két',  '12 tháng', 'Đang bán'),
-- 3 Galaxy S24 Ultra
(3, 31990000, 28800000, 12, '6.8 inch',  'Snapdragon 8 Gen 3',     '3120x1440', '12GB', '256GB', '5000 mAh', '200MP', '12MP',   '232 g', 'Android 14',   'Xám titan',      '12 tháng', 'Đang bán'),
(3, 35990000, 32500000,  6, '6.8 inch',  'Snapdragon 8 Gen 3',     '3120x1440', '12GB', '512GB', '5000 mAh', '200MP', '12MP',   '232 g', 'Android 14',   'Đen titan',      '12 tháng', 'Đang bán'),
-- 4 Galaxy A55 5G
(4,  9990000,  8600000, 30, '6.6 inch',  'Exynos 1480',            '2340x1080', '8GB',  '128GB', '5000 mAh', '50MP',  '32MP',   '213 g', 'Android 14',   'Xanh nhạt',      '12 tháng', 'Đang bán'),
-- 5 Galaxy Z Fold5
(5, 38990000, 35000000,  0, '7.6 inch',  'Snapdragon 8 Gen 2',     '2176x1812', '12GB', '256GB', '4400 mAh', '50MP',  '10MP',   '253 g', 'Android 13',   'Kem',            '12 tháng', 'Ngừng kinh doanh'),
-- 6 Xiaomi 14
(6, 20990000, 18500000, 14, '6.36 inch', 'Snapdragon 8 Gen 3',     '2670x1200', '12GB', '256GB', '4610 mAh', '50MP',  '32MP',   '193 g', 'HyperOS',      'Đen',            '18 tháng', 'Đang bán'),
-- 7 Redmi Note 13 Pro 5G
(7,  8490000,  7200000, 40, '6.67 inch', 'Snapdragon 7s Gen 2',    '2400x1080', '8GB',  '256GB', '5100 mAh', '200MP', '16MP',   '187 g', 'Android 13',   'Tím',            '18 tháng', 'Đang bán'),
-- 8 Redmi 13C
(8,  3290000,  2700000, 60, '6.74 inch', 'Helio G85',              '1600x720',  '4GB',  '128GB', '5000 mAh', '50MP',  '5MP',    '192 g', 'Android 13',   'Xanh dương',     '18 tháng', 'Đang bán'),
-- 9 Poco F6
(9, 10990000,  9400000, 18, '6.67 inch', 'Snapdragon 8s Gen 3',    '2712x1220', '12GB', '512GB', '5000 mAh', '50MP',  '20MP',   '179 g', 'HyperOS',      'Đen',            '18 tháng', 'Đang bán'),
-- 10 OnePlus 12
(10, 19990000, 17500000,  9, '6.82 inch','Snapdragon 8 Gen 3',     '3168x1440', '12GB', '256GB', '5400 mAh', '50MP',  '32MP',   '220 g', 'Android 14',   'Xanh ngọc',      '12 tháng', 'Đang bán'),
-- 11 Pixel 8
(11, 16990000, 14800000,  5, '6.2 inch', 'Google Tensor G3',      '2400x1080', '8GB',  '128GB', '4575 mAh', '50MP',  '10.5MP', '187 g', 'Android 14',   'Đen',            '12 tháng', 'Đang bán'),
-- 12 Oppo Reno11 5G
(12,  9490000,  8100000, 22, '6.7 inch', 'Dimensity 8200',         '2412x1080', '8GB',  '256GB', '5000 mAh', '50MP',  '32MP',   '184 g', 'ColorOS 14',   'Xanh đá',        '12 tháng', 'Đang bán'),
-- 13 Oppo A78
(13,  5490000,  4600000, 35, '6.43 inch','Snapdragon 680',         '2400x1080', '8GB',  '128GB', '5000 mAh', '50MP',  '8MP',    '187 g', 'ColorOS 13',   'Đen',            '12 tháng', 'Đang bán'),
-- 14 Vivo V30
(14, 10990000,  9300000, 16, '6.78 inch','Snapdragon 7 Gen 3',     '2800x1260', '12GB', '256GB', '5000 mAh', '50MP',  '50MP',   '188 g', 'Funtouch OS 14','Xanh lá',       '12 tháng', 'Đang bán'),
-- 15 Vivo Y28
(15,  4290000,  3500000,  0, '6.56 inch','Helio G85',              '1612x720',  '4GB',  '128GB', '5000 mAh', '50MP',  '8MP',    '199 g', 'Funtouch OS 14','Đỏ',            '12 tháng', 'Hết hàng'),
-- 16 Sony Xperia 1 V
(16, 29990000, 26500000,  3, '6.5 inch', 'Snapdragon 8 Gen 2',    '3840x1644', '12GB', '256GB', '5000 mAh', '52MP',  '12MP',   '187 g', 'Android 13',   'Đen',            '12 tháng', 'Đang bán');

-- ---------------------------------------------------------------------
-- Inventory: ghi nhận tồn kho ban đầu cho mọi phiên bản đang có hàng,
-- để tổng nhập - xuất khớp với ProductSpecs.stock_quantity.
-- ---------------------------------------------------------------------
INSERT INTO Inventory(spec_id, quantity_in, quantity_out, transaction_type, notes)
SELECT spec_id, stock_quantity, 0, 'Nhập kho', 'Tồn kho ban đầu'
FROM ProductSpecs
WHERE stock_quantity > 0;