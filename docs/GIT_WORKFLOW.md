# Hướng dẫn làm việc nhóm với Git

Tài liệu này quy định cách cả nhóm dùng Git cho đồ án **Phone Management**: tạo nhánh `develop`, tạo nhánh riêng cho từng công việc, và đưa code vào `develop` sau khi xong.

## 1. Sơ đồ nhánh

```
main                  ← bản ổn định, chỉ nhận code từ develop khi nộp / kết thúc mỗi tuần
 └── develop          ← nhánh tích hợp của cả nhóm
       ├── feature/thaianh-dbconnection
       ├── feature/phuoc-docker-mysql
       ├── feature/phuoc-product-screen
       └── ...        ← mỗi công việc một nhánh, làm xong thì merge vào develop rồi xóa
```

**Quy tắc cốt lõi**

1. Không ai commit trực tiếp vào `main` hoặc `develop`.
2. Mỗi công việc trong bảng phân công = **một nhánh** `feature/<tên>-<chức năng>`.
3. Nhánh luôn được tạo **từ `develop` mới nhất**.
4. Muốn đưa code vào `develop` phải tạo **Pull Request (PR)** và có ít nhất **1 người review**.
5. Merge xong thì xóa nhánh feature.

## 2. Cài đặt Git lần đầu (mỗi người làm một lần)

```bash
git config --global user.name "Tên của bạn"
git config --global user.email "email-dung-tren-github@example.com"
git config --global init.defaultBranch main
git config --global pull.rebase false          # pull = fetch + merge, dễ hiểu hơn cho người mới
git config --global core.autocrlf input        # Linux/macOS
# Windows dùng:  git config --global core.autocrlf true
```

Kiểm tra: `git config --list`

## 3. Thái Anh: tạo repo và nhánh `develop` (làm một lần)

Chỉ **một người** (Thái Anh) làm bước này.

**3.1. Tạo repo trên GitHub**

- Tạo repo mới, ví dụ `phone-management`, để **Private**, **không** tick "Add README".
- Vào **Settings → Collaborators** mời phuoc, Thảo, Sang, Trân.

**3.2. Đẩy project lên và tạo `develop`**

```bash
cd phone-management                      # thư mục project Gradle đã tạo
git init
git add .
git commit -m "Khởi tạo project Gradle + JavaFX"
git branch -M main
git remote add origin https://github.com/<tai-khoan>/phone-management.git
git push -u origin main

# Tạo nhánh develop từ main rồi đẩy lên
git switch -c develop
git push -u origin develop
```

Trước khi commit, kiểm tra `.gitignore` đã loại `build/`, `.gradle/`, `.idea/` (nội dung có trong `README.md`) và đã có `gradlew`, `gradlew.bat`, `gradle/wrapper/`.

**3.3. Đặt `develop` làm nhánh mặc định và bảo vệ nhánh**

Trên GitHub:

- **Settings → Branches → Default branch**: đổi phuoc `develop` (để PR mặc định trỏ vào `develop`).
- **Settings → Branches → Add branch ruleset** (hoặc *Branch protection rule*) cho `main` và `develop`:
  - Bật *Require a pull request before merging*
  - *Required approvals*: 1
  - Bật *Block force pushes*

Bước bảo vệ nhánh có thể cần repo ở chế độ Public hoặc tài khoản có gói phù hợp. Nếu không dùng được, cả nhóm tự giữ kỷ luật theo quy tắc ở mục 1.

## 4. Các thành viên còn lại: lấy code và chuyển phuoc `develop`

```bash
git clone https://github.com/<tai-khoan>/phone-management.git
cd phone-management

git branch -a                 # xem các nhánh (có origin/develop)
git switch develop            # chuyển phuoc nhánh develop
git pull                      # lấy bản mới nhất
```

Nếu `git switch develop` báo không tìm thấy nhánh: chạy `git fetch origin` rồi thử lại.

Sau đó bật database và chạy thử đúng theo `README.md`.

## 5. Quy ước đặt tên nhánh

```
feature/<tên>-<chức-năng>
```

- `<tên>` viết thường, không dấu: `thaianh`, `phuoc`, `thao`, `phuoc`, `tran`.
- `<chức-năng>` ngắn gọn, tiếng Anh hoặc tiếng Việt không dấu, nối bằng dấu gạch ngang.
- Sửa lỗi: `fix/<tên>-<mô-tả>`, ví dụ `fix/phuoc-stock-negative`.
- Không dùng dấu cách, dấu tiếng Việt hay chữ hoa.

## 6. Danh sách nhánh cho từng công việc

Tên nhánh ứng với 37 công việc trong bảng phân công (kế hoạch 3 tuần).

### Thái Anh (Backend)

| Tuần | Công việc | Nhánh |
|---|---|---|
| 1 | Tạo repo Git và quy ước nhánh | *(làm trực tiếp theo mục 3, không cần nhánh)* |
| 1 | Setup Gradle + JavaFX plugin | `feature/thaianh-gradle-setup` |
| 1 | DBConnection (HikariCP) | `feature/thaianh-dbconnection` |
| 1 | DAO: Product, ProductSpec | `feature/thaianh-product-dao` |
| 2 | AuthBUS (đăng nhập, phân quyền) | `feature/thaianh-auth-bus` |
| 2 | OrderBUS (tạo đơn hàng, transaction) | `feature/thaianh-order-bus` |
| 2 | ReportBUS (thống kê) | `feature/thaianh-report-bus` |
| 3 | Validation và xử lý lỗi | `feature/thaianh-validation` |
| 3 | Tích hợp FE–BE và sửa lỗi | `feature/thaianh-integration` |
| 3 | Đóng gói và README chạy dự án | `feature/thaianh-readme` |

### phuoc (Backend)

| Tuần | Công việc | Nhánh |
|---|---|---|
| 1 | Docker MySQL (docker-compose) | `feature/phuoc-docker-mysql` |
| 1 | Sửa và bổ sung dữ liệu mẫu SQL | `feature/phuoc-sql-data` |
| 1 | DTO classes (13 bảng) | `feature/phuoc-dto` |
| 1 | DAO: Brand, Category | `feature/phuoc-brand-category-dao` |
| 1 | DAO: User, Role, Permission | `feature/phuoc-user-role-dao` |
| 2 | DAO + BUS: Customer, Staff | `feature/phuoc-customer-staff` |
| 2 | InventoryBUS (nhập/xuất kho) | `feature/phuoc-inventory-bus` |
| 3 | Unit test DAO và BUS | `feature/phuoc-unit-test` |
| 3 | Kiểm thử luồng nghiệp vụ end-to-end | `feature/phuoc-e2e-test` |

### Thảo (Frontend)

| Tuần | Công việc | Nhánh |
|---|---|---|
| 1 | Wireframe các màn hình | `feature/thao-wireframe` |
| 2 | Quản lý khách hàng | `feature/thao-customer-screen` |
| 2 | Danh sách và chi tiết đơn hàng | `feature/thao-order-screen` |
| 3 | Dashboard | `feature/thao-dashboard` |
| 3 | Báo cáo thống kê | `feature/thao-report-screen` |
| 3 | Slide thuyết trình | `feature/thao-slides` |

### Sang (Frontend)

| Tuần | Công việc | Nhánh |
|---|---|---|
| 1 | Layout chung và theme CSS | `feature/phuoc-layout-theme` |
| 2 | Quản lý sản phẩm và cấu hình | `feature/phuoc-product-screen` |
| 2 | Màn hình bán hàng (giỏ hàng) | `feature/phuoc-sales-screen` |
| 3 | Quản lý kho | `feature/phuoc-inventory-screen` |
| 3 | Chuẩn bị kịch bản demo | `feature/phuoc-demo-script` |

### Trân (Frontend)

| Tuần | Công việc | Nhánh |
|---|---|---|
| 1 | Điều hướng giữa các màn hình | `feature/tran-navigation` |
| 1 | Màn hình đăng nhập | `feature/tran-login-screen` |
| 2 | Quản lý thương hiệu và danh mục | `feature/tran-brand-category-screen` |
| 2 | Quản lý nhân viên | `feature/tran-staff-screen` |
| 3 | Quản lý người dùng và phân quyền | `feature/tran-user-role-screen` |
| 3 | Ẩn/hiện chức năng theo quyền | `feature/tran-permission-ui` |
| 3 | Báo cáo đồ án (tài liệu) | `feature/tran-report-doc` |

## 7. Quy trình cho MỘT công việc (từ đầu đến khi merge)

Ví dụ: Sang làm màn hình bán hàng, nhánh `feature/phuoc-sales-screen`.

### Bước 1. Cập nhật `develop` rồi tạo nhánh mới

```bash
git switch develop
git pull
git switch -c feature/phuoc-sales-screen
```

`git switch -c <tên>` vừa tạo vừa chuyển sang nhánh mới. Kiểm tra đang ở nhánh nào: `git branch` (nhánh có dấu `*`).

### Bước 2. Làm việc và commit thường xuyên

```bash
git status                          # xem file nào thay đổi
git add src/main/java/com/phonestore/gui/SalesController.java
git add src/main/resources/fxml/Sales.fxml
git commit -m "feat(sales): thêm giao diện giỏ hàng"
```

- Commit nhỏ, mỗi commit làm một việc rõ ràng; commit ít nhất mỗi khi xong một phần nhỏ.
- Hạn chế `git add .` khi chưa kiểm tra `git status`, vì dễ kéo theo file không mong muốn (`build/`, `.idea/`, file cá nhân).
- Xem lại thay đổi trước khi commit: `git diff` (chưa add) hoặc `git diff --staged` (đã add).

**Quy ước commit message:** `<loại>(<phạm vi>): <mô tả ngắn>`

| Loại | Dùng khi | Ví dụ |
|---|---|---|
| `feat` | thêm chức năng | `feat(order): thêm tạo đơn hàng có transaction` |
| `fix` | sửa lỗi | `fix(inventory): không cho tồn kho âm` |
| `refactor` | sửa cấu trúc, không đổi hành vi | `refactor(dao): tách hàm mapRow` |
| `docs` | tài liệu | `docs: cập nhật README` |
| `test` | thêm/sửa test | `test(product): test ProductBUS` |
| `style` | giao diện/CSS/định dạng | `style(sales): căn lại bảng giỏ hàng` |
| `chore` | cấu hình, build | `chore: thêm dependency BCrypt` |

### Bước 3. Đẩy nhánh lên GitHub

Lần đầu đẩy nhánh:

```bash
git push -u origin feature/phuoc-sales-screen
```

Các lần sau chỉ cần `git push`. Nên đẩy ít nhất cuối mỗi buổi làm việc để có bản sao lưu.

### Bước 4. Trước khi tạo PR: lấy `develop` mới nhất về nhánh của mình

Trong lúc bạn làm, người khác có thể đã merge code vào `develop`. Hãy cập nhật để phát hiện xung đột sớm:

```bash
git fetch origin
git merge origin/develop
```

- Nếu báo `Already up to date` hoặc merge êm: tiếp tục.
- Nếu có xung đột: xem **mục 9**.
- Sau khi merge, **chạy lại ứng dụng và test** cho chắc chắn còn chạy được:

```bash
./gradlew build
./gradlew run
```

Rồi `git push`.

### Bước 5. Tạo Pull Request

**Cách 1: trên GitHub:** vào repo, bấm **Compare & pull request** ở thông báo vừa hiện ra (hoặc *Pull requests → New pull request*).

- **base:** `develop`, **compare:** `feature/phuoc-sales-screen`
- Tiêu đề: `feat(sales): màn hình bán hàng`
- Mô tả theo mẫu ở mục 8, chọn **Reviewers** là một bạn trong nhóm.

**Cách 2: dùng GitHub CLI** (nếu đã cài `gh` và đăng nhập):

```bash
gh pr create --base develop --title "feat(sales): màn hình bán hàng" --body "Mô tả ngắn các thay đổi"
```

### Bước 6. Review

Người được chọn review:

- Vào tab **Files changed**, đọc code, bấm vào dòng cần góp ý để comment.
- Có thể kéo nhánh về chạy thử:
  ```bash
  git fetch origin
  git switch feature/phuoc-sales-screen
  ./gradlew run
  ```
- Bấm **Review changes → Approve** nếu ổn, hoặc **Request changes** nếu cần sửa.

Người viết sửa theo góp ý: commit tiếp trên cùng nhánh và `git push`, PR tự cập nhật.

### Bước 7. Merge vào `develop`

Sau khi được Approve, người tạo PR bấm **Merge pull request** trên GitHub. Nhóm nên chọn **Squash and merge** (gộp mọi commit của nhánh thành một commit gọn trên `develop`) để lịch sử dễ đọc.

Nếu muốn merge bằng dòng lệnh (không khuyến khích khi đã có PR):

```bash
git switch develop
git pull
git merge --no-ff feature/phuoc-sales-screen
git push
```

### Bước 8. Dọn dẹp

```bash
git switch develop
git pull                                           # lấy kết quả merge về máy
git branch -d feature/phuoc-sales-screen           # xóa nhánh ở máy
git push origin --delete feature/phuoc-sales-screen   # xóa nhánh trên GitHub (hoặc bấm Delete branch trên PR)
```

Nếu Git từ chối `-d` vì nói "chưa merge" trong khi bạn dùng Squash and merge, đó là bình thường: kiểm tra PR đã merge trên GitHub rồi dùng `git branch -D <tên>`.

Sau đó quay lại **Bước 1** cho công việc tiếp theo.

## 8. Mẫu mô tả Pull Request

```markdown
## Việc đã làm
- Thêm màn hình bán hàng (Sales.fxml, SalesController)
- Gọi OrderBUS.createOrder khi bấm "Thanh toán"

## Công việc trong bảng phân công
Tuần 2 – Màn hình bán hàng (giỏ hàng)

## Cách kiểm tra
1. docker compose up -d
2. ./gradlew run, đăng nhập, vào menu Bán hàng
3. Thêm 2 sản phẩm vào giỏ, bấm Thanh toán, kiểm tra tồn kho giảm

## Ảnh chụp màn hình (nếu có)

## Lưu ý cho người review
- Phần in hóa đơn chưa làm
```

**Checklist trước khi tạo PR**

- [ ] Đã `git merge origin/develop` vào nhánh của mình
- [ ] `./gradlew build` chạy không lỗi
- [ ] Đã chạy thử ứng dụng, chức năng hoạt động
- [ ] Không có file thừa (`build/`, `.idea/`, mật khẩu, file cá nhân)
- [ ] Tên nhánh và commit message đúng quy ước

## 9. Xử lý xung đột (conflict)

Xung đột xảy ra khi hai người sửa cùng một đoạn của cùng một file. Khi `git merge origin/develop` báo `CONFLICT`:

```bash
git status                  # xem file nào đang xung đột (mục "Unmerged paths")
```

Mở file đó, bạn sẽ thấy:

```
<<<<<<< HEAD
code của nhánh bạn
=======
code từ develop
>>>>>>> origin/develop
```

Cách xử lý:

1. Đọc cả hai phía, quyết định giữ phần nào (hoặc gộp cả hai). **Nếu không chắc, hỏi người kia.**
2. Xóa hết các dòng `<<<<<<<`, `=======`, `>>>>>>>`.
3. Lưu file, rồi:

```bash
git add <file-đã-sửa>
git commit                  # Git tự điền sẵn message merge, lưu và thoát là xong
./gradlew build             # kiểm tra lại còn chạy
git push
```

Muốn hủy việc merge và quay lại trạng thái trước đó: `git merge --abort`.

IntelliJ có công cụ gỡ xung đột trực quan: **Git → Resolve Conflicts**.

**Cách giảm xung đột trong nhóm này**

- File dùng chung (`style.css`, `SceneNavigator`, `build.gradle`, `db/*.sql`) chỉ nên do **một người** sửa. Cần thêm gì thì nhắn người phụ trách hoặc sửa nhỏ rồi merge sớm.
- Không format lại cả file khi chỉ sửa vài dòng.
- Merge PR sớm, đừng để nhánh sống quá vài ngày.
- Mỗi người làm file riêng của mình (mỗi màn hình một Controller và một FXML là đã tách tốt).

## 10. Các tình huống thường gặp

**Đã sửa code nhưng chưa commit, cần chuyển nhánh gấp**
```bash
git stash                   # cất thay đổi tạm
git switch develop
# ...làm việc khác...
git switch feature/ten-nhanh
git stash pop               # lấy lại thay đổi
```

**Lỡ sửa code trên `develop` (chưa commit)**
```bash
git switch -c feature/ten-chuc-nang     # mang luôn thay đổi sang nhánh mới
```

**Lỡ commit trên `develop` (chưa push)**
```bash
git branch feature/ten-chuc-nang        # tạo nhánh tại commit hiện tại
git reset --hard origin/develop         # đưa develop về như trên GitHub
git switch feature/ten-chuc-nang
```
`reset --hard` xóa thay đổi chưa commit, nên chỉ dùng khi chắc chắn mọi thứ đã nằm trong commit.

**Sửa message của commit cuối (chưa push)**
```bash
git commit --amend -m "message mới"
```

**Bỏ thay đổi của một file (chưa commit)**
```bash
git restore <file>
```

**Hoàn tác một commit đã push mà không viết lại lịch sử**
```bash
git revert <mã-commit>
git push
```

**Lỡ `git add` nhầm file**
```bash
git restore --staged <file>
```

**Lỡ commit file `build/` hoặc `.idea/`**
```bash
git rm -r --cached build/ .idea/
git commit -m "chore: bỏ build và .idea khỏi git"
```
Kiểm tra `.gitignore` đã có hai mục này.

**Lỡ commit mật khẩu thật hoặc khóa bí mật:** báo cả nhóm, **đổi mật khẩu ngay** (xóa commit không đủ vì lịch sử vẫn còn trên GitHub).

**Xem lịch sử gọn**
```bash
git log --oneline --graph --all -20
```

**Xem ai sửa dòng nào**
```bash
git blame <file>
```

**Điều không được làm**

- `git push --force` lên `develop` hoặc `main`.
- `git reset --hard` khi chưa chắc code đã được lưu ở đâu đó.
- Commit trực tiếp vào `develop` hoặc `main`.

## 11. Cập nhật `develop` mỗi ngày

Đầu mỗi buổi làm việc, **trước khi viết code**:

```bash
git switch develop
git pull
git switch feature/ten-nhanh-cua-ban
git merge develop
```

Nếu `develop` có thay đổi database (`db/*.sql`), chạy lại:

```bash
docker compose down -v && docker compose up -d
```

## 12. Đưa `develop` vào `main` (cuối mỗi tuần hoặc khi nộp bài)

Do Thái Anh thực hiện sau khi cả nhóm đã test `develop`:

1. Trên GitHub tạo PR: **base `main`, compare `develop`**, tiêu đề ví dụ `Release tuần 1`.
2. Cả nhóm kiểm tra lần cuối, một người Approve.
3. Chọn **Create a merge commit** (không squash để giữ lịch sử) rồi merge.
4. Đánh dấu mốc:

```bash
git switch main
git pull
git tag -a v0.1 -m "Mốc cuối tuần 1"
git push origin v0.1
```

Gợi ý mốc: `v0.1` cuối tuần 1, `v0.2` cuối tuần 2, `v1.0` bản nộp.

## 13. Lịch phối hợp gợi ý

- **Đầu tuần 1:** Thái Anh làm mục 3 trước (tạo repo, `develop`, mời thành viên); sau đó mọi người mới tạo nhánh. phuoc và Thái Anh merge sớm các phần nền (Gradle, Docker, DTO, DBConnection) để frontend kéo về dùng.
- **Hằng ngày:** đầu buổi cập nhật `develop` (mục 11), cuối buổi `git push` nhánh của mình.
- **Review:** PR nào mở thì người được chọn review trong ngày, tránh để PR nằm quá 24 giờ.
- **Gợi ý cặp review:** Thái Anh ↔ phuoc (backend), Thảo ↔ Sang ↔ Trân xoay vòng (frontend); PR liên quan cả hai phía thì nhờ một người bên kia xem.

## 14. Bảng lệnh nhanh

| Mục đích | Lệnh |
|---|---|
| Xem nhánh hiện tại và các nhánh | `git branch` / `git branch -a` |
| Chuyển nhánh | `git switch <nhánh>` |
| Tạo và chuyển sang nhánh mới | `git switch -c <nhánh>` |
| Lấy thông tin mới từ GitHub (không merge) | `git fetch origin` |
| Lấy và merge nhánh hiện tại | `git pull` |
| Merge `develop` vào nhánh hiện tại | `git merge develop` |
| Xem trạng thái | `git status` |
| Xem thay đổi | `git diff` / `git diff --staged` |
| Thêm file vào commit | `git add <file>` |
| Commit | `git commit -m "feat(...): ..."` |
| Đẩy nhánh lần đầu | `git push -u origin <nhánh>` |
| Đẩy các lần sau | `git push` |
| Xóa nhánh ở máy | `git branch -d <nhánh>` |
| Xóa nhánh trên GitHub | `git push origin --delete <nhánh>` |
| Cất / lấy lại thay đổi tạm | `git stash` / `git stash pop` |
| Hủy merge đang xung đột | `git merge --abort` |
| Xem lịch sử gọn | `git log --oneline --graph --all` |