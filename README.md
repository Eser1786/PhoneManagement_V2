# Phone Management – Hệ thống quản lý bán điện thoại

Đồ án quản lý cửa hàng bán điện thoại: sản phẩm và cấu hình, khách hàng, nhân viên, bán hàng, kho, báo cáo thống kê và phân quyền người dùng.

- **Ngôn ngữ / giao diện:** Java 21, JavaFX 21
- **Build:** Gradle (dùng Gradle Wrapper, không cần cài Gradle)
- **Cơ sở dữ liệu:** MySQL 8.4 chạy bằng Docker
- **Kiến trúc:** 3 lớp **GUI – BUS – DAO** kèm **DTO**

## Thành viên

| Thành viên | Vai trò | Phụ trách chính |
|---|---|---|
| Thái Anh | Backend | Cấu hình dự án, DAO/BUS sản phẩm, xác thực, đơn hàng, báo cáo |
| Sang | Backend | Docker/SQL, DTO, DAO/BUS người dùng, khách hàng, nhân viên, kho, test |
| Thảo | Frontend | Wireframe, Dashboard, khách hàng, đơn hàng, báo cáo |
| Phước | Frontend | Layout/theme, sản phẩm, bán hàng, kho |
| Trân | Frontend | Điều hướng, đăng nhập, thương hiệu/danh mục, nhân viên, phân quyền |

Chi tiết từng công việc, hạn và trạng thái xem file `docs/PhanCong_QuanLyBanDienThoai.xlsx`.

## Cấu trúc thư mục

```
phone-management/
├── build.gradle                    # cấu hình Gradle (JavaFX, MySQL, HikariCP, BCrypt, JUnit)
├── settings.gradle
├── gradlew                         # Gradle Wrapper (Linux/macOS)
├── gradlew.bat                     # Gradle Wrapper (Windows)
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── docker-compose.yml              # MySQL container
├── .gitignore
├── README.md
│
├── db/                             # script khởi tạo, MySQL tự chạy theo thứ tự tên file
│   ├── 01_schema.sql               # tạo bảng
│   └── 02_insert.sql               # dữ liệu mẫu
│
├── docs/
│   ├── PhanCong_QuanLyBanDienThoai.xlsx
│   ├── erd.png                     # sơ đồ ERD
│   └── wireframes/                 # mockup các màn hình
│
└── src/
    ├── main/
    │   ├── java/com/phonestore/
    │   │   ├── App.java                    # điểm khởi chạy JavaFX
    │   │   │
    │   │   ├── config/
    │   │   │   └── DBConnection.java       # HikariCP, đọc db.properties
    │   │   │
    │   │   ├── dto/                        # chỉ chứa dữ liệu, không có logic
    │   │   │   ├── BrandDTO.java
    │   │   │   ├── CategoryDTO.java
    │   │   │   ├── RoleDTO.java
    │   │   │   ├── PermissionDTO.java
    │   │   │   ├── UserDTO.java
    │   │   │   ├── CustomerDTO.java
    │   │   │   ├── StaffDTO.java
    │   │   │   ├── ProductDTO.java
    │   │   │   ├── ProductSpecDTO.java
    │   │   │   ├── InventoryDTO.java
    │   │   │   ├── OrderDTO.java
    │   │   │   └── OrderDetailDTO.java
    │   │   │
    │   │   ├── dao/                        # chỉ viết SQL, trả về DTO
    │   │   │   ├── BrandDAO.java
    │   │   │   ├── CategoryDAO.java
    │   │   │   ├── RoleDAO.java
    │   │   │   ├── PermissionDAO.java
    │   │   │   ├── UserDAO.java
    │   │   │   ├── CustomerDAO.java
    │   │   │   ├── StaffDAO.java
    │   │   │   ├── ProductDAO.java
    │   │   │   ├── ProductSpecDAO.java
    │   │   │   ├── InventoryDAO.java
    │   │   │   ├── OrderDAO.java           # có createOrder() dùng transaction
    │   │   │   └── ReportDAO.java
    │   │   │
    │   │   ├── bus/                        # nghiệp vụ, kiểm tra dữ liệu, phân quyền
    │   │   │   ├── BrandBUS.java
    │   │   │   ├── CategoryBUS.java
    │   │   │   ├── AuthBUS.java            # đăng nhập, kiểm tra quyền
    │   │   │   ├── UserBUS.java
    │   │   │   ├── CustomerBUS.java
    │   │   │   ├── StaffBUS.java
    │   │   │   ├── ProductBUS.java
    │   │   │   ├── InventoryBUS.java
    │   │   │   ├── OrderBUS.java
    │   │   │   └── ReportBUS.java
    │   │   │
    │   │   ├── gui/                        # controller JavaFX, chỉ gọi BUS
    │   │   │   ├── SceneNavigator.java     # chuyển màn hình, nạp FXML
    │   │   │   ├── MainLayoutController.java
    │   │   │   ├── LoginController.java
    │   │   │   ├── DashboardController.java
    │   │   │   ├── ProductController.java
    │   │   │   ├── BrandCategoryController.java
    │   │   │   ├── CustomerController.java
    │   │   │   ├── StaffController.java
    │   │   │   ├── SalesController.java    # màn hình bán hàng (giỏ hàng)
    │   │   │   ├── OrderController.java
    │   │   │   ├── InventoryController.java
    │   │   │   ├── ReportController.java
    │   │   │   └── UserRoleController.java
    │   │   │
    │   │   ├── exception/
    │   │   │   └── BusinessException.java  # lỗi nghiệp vụ, thông điệp tiếng Việt
    │   │   │
    │   │   └── util/
    │   │       ├── PasswordUtil.java       # băm/kiểm tra mật khẩu BCrypt
    │   │       ├── SessionManager.java     # người dùng đang đăng nhập
    │   │       ├── AlertUtil.java          # hộp thoại thông báo
    │   │       └── FormatUtil.java         # định dạng tiền, ngày
    │   │
    │   └── resources/
    │       ├── db.properties               # cấu hình kết nối DB
    │       ├── css/
    │       │   └── style.css
    │       ├── fxml/
    │       │   ├── MainLayout.fxml
    │       │   ├── Login.fxml
    │       │   ├── Dashboard.fxml
    │       │   ├── Product.fxml
    │       │   ├── BrandCategory.fxml
    │       │   ├── Customer.fxml
    │       │   ├── Staff.fxml
    │       │   ├── Sales.fxml
    │       │   ├── Order.fxml
    │       │   ├── Inventory.fxml
    │       │   ├── Report.fxml
    │       │   └── UserRole.fxml
    │       └── images/                     # icon, logo
    │
    └── test/
        └── java/com/phonestore/
            ├── dao/                        # test DAO
            └── bus/                        # test nghiệp vụ
```

### Quy tắc giữa các lớp

```
GUI  ──►  BUS  ──►  DAO  ──►  MySQL
 └────────┴─────────┴── dùng chung DTO
```

- **GUI** chỉ gọi BUS. Không viết SQL và không gọi DAO trong controller.
- **BUS** kiểm tra dữ liệu đầu vào, tính tiền/chiết khấu, kiểm tra tồn kho, phân quyền; ném `BusinessException` với thông điệp tiếng Việt để GUI hiển thị.
- **DAO** chỉ chứa SQL và ánh xạ ResultSet sang DTO. Không chứa nghiệp vụ.
- **DTO** chỉ có field, getter/setter. Màn hình cần dữ liệu ghép nhiều bảng thì tạo DTO riêng cho view đó.
- **Transaction** khi tạo đơn hàng (Orders + OrderDetails + trừ kho + ghi Inventory) nằm trong `OrderDAO.createOrder(...)`, dùng chung một `Connection`.

## Yêu cầu cài đặt

- JDK 21 trở lên
- Docker Desktop (hoặc Docker Engine + Docker Compose v2)
- Git
- IDE khuyến nghị: IntelliJ IDEA
- Gradle

## Cài đặt và chạy

### 1. Lấy mã nguồn

```bash
git clone <url-repo>
cd phone-management
```

### 2. Bật cơ sở dữ liệu

```bash
docker compose up -d
docker compose logs -f mysql      # chờ thấy "ready for connections" rồi nhấn Ctrl+C
```

Lần chạy đầu tiên, MySQL tự thực thi `db/01_schema.sql` rồi `db/02_insert.sql`.

### 3. Chạy ứng dụng

```bash
./gradlew run                     # Windows: gradlew.bat run
```

### 4. Cấu hình kết nối (nếu cần)

Sửa `src/main/resources/db.properties`:

```properties
db.url=jdbc:mysql://localhost:3307/phonemanagement_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Ho_Chi_Minh
db.user=root
db.password=root123
```

Thông tin này chỉ dùng cho môi trường phát triển. Không dùng mật khẩu này ở môi trường thật.

## Các lệnh thường dùng

| Mục đích | Lệnh |
|---|---|
| Chạy ứng dụng | `./gradlew run` |
| Biên dịch và chạy test | `./gradlew build` |
| Chỉ chạy test | `./gradlew test` |
| Xóa thư mục build | `./gradlew clean` |
| Bật database | `docker compose up -d` |
| Tắt database (giữ dữ liệu) | `docker compose down` |
| Xóa dữ liệu và khởi tạo lại | `docker compose down -v && docker compose up -d` |
| Vào MySQL trong container | `docker exec -it phone-mysql mysql -uroot -proot123 phonemanagement_db` |

> Mỗi khi `db/*.sql` thay đổi, cả nhóm cần chạy `docker compose down -v && docker compose up -d` để đồng bộ dữ liệu.

## `docker-compose.yml`

```yaml
services:
  mysql:
    image: mysql:8.4
    container_name: phone-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: phonemanagement_db
      TZ: Asia/Ho_Chi_Minh
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    ports:
      - "3307:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./db:/docker-entrypoint-initdb.d

volumes:
  mysql_data:
```

## `build.gradle`

```groovy
plugins {
    id 'application'
    id 'org.openjfx.javafxplugin' version '0.1.0'
}

repositories { mavenCentral() }

dependencies {
    implementation 'com.mysql:mysql-connector-j:9.1.0'
    implementation 'com.zaxxer:HikariCP:6.2.1'
    implementation 'at.favre.lib:bcrypt:0.10.2'

    testImplementation 'org.junit.jupiter:junit-jupiter:5.11.3'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

javafx {
    version = '21.0.5'
    modules = ['javafx.controls', 'javafx.fxml']
}

application {
    mainClass = 'com.phonestore.App'
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

test { useJUnitPlatform() }
```

## `.gitignore`

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# IDE
.idea/
*.iml
.vscode/
out/

# Hệ điều hành
.DS_Store
Thumbs.db

# Môi trường cục bộ
.env
*.log
```

## Cơ sở dữ liệu

Database `phonemanagement_db` gồm 13 bảng:

| Nhóm | Bảng |
|---|---|
| Sản phẩm | `Brands`, `Categories`, `Products`, `ProductSpecs` |
| Người dùng và phân quyền | `Roles`, `Permissions`, `RolePermissions`, `Users`, `Customers`, `Staff` |
| Kho | `Inventory` |
| Bán hàng | `Orders`, `OrderDetails` |

Vai trò mặc định: `Admin`, `Manager`, `Staff`, `Customer`.

## Quy trình làm việc với Git

- Nhánh `main`: bản ổn định, chỉ merge khi đã kiểm thử.
- Nhánh `develop`: nhánh tích hợp của cả nhóm.
- Mỗi việc làm trên nhánh riêng, đặt tên `feature/<tên>-<việc>`, ví dụ `feature/phuoc-sales-screen`.
- Tạo Pull Request vào `develop`, ít nhất một người khác review rồi mới merge.
- Commit message ngắn gọn, nói rõ việc đã làm, ví dụ `Thêm ProductDAO: CRUD và lọc theo hãng`.
- Không commit `build/`, `.gradle/`, `.idea/`.

## Xử lý sự cố thường gặp

| Hiện tượng | Cách xử lý |
|---|---|
| `./gradlew run` báo thiếu module JavaFX | Kiểm tra đang dùng JDK 21 và khối `javafx { modules = [...] }` trong `build.gradle` đã đủ |
| `Communications link failure` khi kết nối DB | Container chưa sẵn sàng: chạy `docker compose logs mysql`, chờ "ready for connections" |
| `port is already allocated` | Cổng 3307 đã bị chiếm: đổi cổng trong `docker-compose.yml` và `db.url` |
| Sửa SQL nhưng DB không đổi | Script chỉ chạy lần đầu: chạy `docker compose down -v && docker compose up -d` |
| Tiếng Việt bị lỗi font trong DB | Kiểm tra `db.url` có `useUnicode=true&characterEncoding=UTF-8` và container dùng `utf8mb4` |
| `Permission denied: ./gradlew` | Chạy `chmod +x gradlew` |