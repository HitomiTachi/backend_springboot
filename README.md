# Backend – TechHome E‑commerce

Thư mục này chứa **backend API** (chạy tại `http://localhost:8080`). Frontend (Vite/React ở thư mục gốc) gọi API qua base URL `http://localhost:8080/api`.

## Cấu trúc gợi ý (Spring Boot)

Nếu backend của bạn là **Spring Boot**, đặt toàn bộ project vào đây, ví dụ:

```
backend/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/.../TechHomeApplication.java
│   │   ├── resources/
│   │   │   └── application.properties
│   │   └── ...
│   └── test/
└── README.md  (file này)
```

## Chạy backend

- **Maven:** `mvn spring-boot:run` (trong thư mục `backend/`)
- **Gradle:** `./gradlew bootRun`
- Đảm bảo CORS cho phép origin frontend (ví dụ `http://localhost:3000`, `http://localhost:3001`).

## API (đã dùng ở frontend)

- `GET /api/health` – kiểm tra sống
- `GET /api/categories` – danh mục
- `GET /api/products`, `GET /api/products/featured`, `GET /api/products/{id}`
- `POST /api/auth/login`, `POST /api/auth/register`
- **Cart (cần JWT):** `GET /api/cart`, `POST /api/cart/items`, `PATCH /api/cart/items/:id`, `DELETE /api/cart/items/:id`, `PUT /api/cart`
- `POST /api/orders`, `GET /api/orders` (cần JWT)

**Chạy backend Node (server.js):** trong thư mục `backend/` chạy `npm install` rồi `npm start` (port 8080).

Sau khi đặt code backend vào đây, chạy backend rồi chạy frontend (`npm run dev` ở thư mục gốc) để dùng đầy đủ tính năng.
