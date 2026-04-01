# Setup môi trường một lần

Tài liệu này chuẩn hóa version và cấu hình môi trường cho toàn bộ dự án.

## Version chuẩn

- Java: `21`
- Maven: `3.9+`
- Node.js: `22 LTS`
- npm: `10+`
- MySQL/MariaDB: `8.0+` (XAMPP/MariaDB vẫn chạy được cho local)

## 1) Frontend (`FE_Spring_boot`)

1. Copy file mẫu:
   - `.env.example` -> `.env`
2. Cài dependencies:
   - `npm install`
3. Chạy dev:
   - `npm run dev`

## 2) Backend (`backend_springboot/webdienthoai/webdienthoai`)

1. Tạo DB local:
   - `webbandienthoai`
2. Copy file mẫu:
   - `.env.example` -> `.env.local` (tham khảo biến, không commit file thật)
3. Set environment variables trong terminal trước khi chạy:

### PowerShell (Windows)

```powershell
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DB_URL="jdbc:mysql://localhost:3306/webbandienthoai"
$env:DB_USERNAME="root"
$env:DB_PASSWORD=""
$env:JWT_SECRET="replace_with_a_long_random_secret_min_32_chars"
$env:AUTH_MAX_FAILED_ATTEMPTS="5"
$env:AUTH_LOCK_MINUTES="15"
$env:GEMINI_API_KEY=""
```

4. Chạy backend:
   - `.\mvnw.cmd spring-boot:run`

## 3) Profile cấu hình

- `dev`: log SQL bật, `ddl-auto=update`
- `test`: dùng H2 in-memory, không phụ thuộc MySQL local
- `prod`: `ddl-auto=validate`, tắt SQL log

Đổi profile bằng biến `SPRING_PROFILES_ACTIVE`.

## 4) Nguyên tắc bảo mật

- Không commit secret vào git (`JWT_SECRET`, `GEMINI_API_KEY`, `DB_PASSWORD`)
- Chỉ dùng biến môi trường cho secret
- Rotate key nếu nghi ngờ đã lộ
