# TechHome – Backend API (Spring Boot)

API REST cho cửa hàng điện tử **TechHome**, chạy mặc định tại **`http://localhost:8080`**. Mọi endpoint REST nằm dưới prefix **`/api`**.

Frontend (Vite/React, repo riêng ví dụ `techhome-e-commerce`) cấu hình `VITE_API_URL=http://localhost:8080/api`.

## Yêu cầu

- **JDK 21**
- **Maven 3.9+**
- **MySQL 8** (hoặc tương thích); tạo database trước, ví dụ: `webbandienthoai`

## Cấu trúc project

Module Spring Boot nằm tại:

```
backend_springboot/webdienthoai/webdienthoai/
├── pom.xml
├── src/main/java/com/example/webdienthoai/
│   ├── WebdienthoaiApplication.java
│   ├── config/          # Security, CORS, WebMvc, DataInitializer
│   ├── controller/      # REST controllers
│   ├── dto/, entity/, repository/, security/
│   └── ...
└── src/main/resources/application.properties
```

## Cấu hình

Chỉnh `src/main/resources/application.properties`:

| Mục | Ghi chú |
|-----|---------|
| `spring.datasource.url`, `username`, `password` | Kết nối MySQL |
| `app.jwt.secret` | **Đổi** khi deploy production |
| `app.cors.allowed-origins` | Origin của frontend (mặc định localhost:3000/3001) |
| `app.upload.dir` | Thư mục lưu file upload (ảnh sản phẩm) |
| `app.gemini.api-key` | API key Gemini (tính năng AI admin); không commit key thật lên git |

## Chạy local

```bash
cd backend_springboot/webdienthoai/webdienthoai
mvn spring-boot:run
```

Hoặc mở project trong IDE và chạy `WebdienthoaiApplication`.

## API tổng quan

**Public (không cần JWT)**

- `GET /api/health` – health check
- `GET /api/categories`, `GET /api/categories/{id}`
- `GET /api/products`, `GET /api/products/featured`, `GET /api/products/{id}`
- `POST /api/auth/login`, `POST /api/auth/register`
- `GET /uploads/**` – file tĩnh (ảnh đã upload)

**Cần JWT** (`Authorization: Bearer <token>`)

- **Profile:** `GET /api/profile`, `PATCH /api/profile`
- **Địa chỉ:** `GET/POST /api/addresses`, `PATCH /api/addresses/{id}`, `PUT /api/addresses/{id}/set-default`, `DELETE /api/addresses/{id}` (tối đa 10 địa chỉ/user)
- **Giỏ hàng:** `GET /api/cart`, `POST /api/cart/items`, `PATCH /api/cart/items/{itemId}`, `DELETE /api/cart/items/{itemId}`, `DELETE /api/cart/items` (xóa hết giỏ)
- **Đơn hàng:** `POST /api/orders`, `GET /api/orders`, `GET /api/orders/{id}`
- **Upload:** `POST /api/upload` (multipart `file`)

**Admin** (JWT + role `admin`)

- `GET /api/admin/users`, `GET /api/admin/users/{id}`, `PUT /api/admin/users/{id}/role`, `DELETE /api/admin/users/{id}`
- `GET/POST /api/admin/products`, `PATCH/DELETE /api/admin/products/{id}`
- `GET/POST /api/admin/categories`, `PATCH/DELETE /api/admin/categories/{id}`
- `GET /api/admin/stats`
- **AI:** `GET /api/admin/ai/list-models`, `POST /api/admin/ai/generate`

## Frontend

Trong repo frontend, chạy `npm run dev` (thường port **3000**). Đảm bảo CORS trong `application.properties` trùng origin dev của bạn.

## Ghi chú

- Mật khẩu user được hash (BCrypt); JWT dùng cho stateless auth.
- `spring.jpa.hibernate.ddl-auto=update` giúp cập nhật schema khi đổi entity; với thay đổi cột phức tạp có thể cần migration SQL thủ công.
