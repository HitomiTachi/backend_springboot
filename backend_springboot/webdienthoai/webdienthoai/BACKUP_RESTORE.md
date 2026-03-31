# Backup và Restore (MySQL/XAMPP)

## Backup thủ công

```powershell
mysqldump -u root -p webbandienthoai > backup-webbandienthoai.sql
```

## Restore thủ công

```powershell
mysql -u root -p webbandienthoai < backup-webbandienthoai.sql
```

## Chiến lược đề xuất

- Dev: backup nhanh trước khi chạy migration lớn.
- Staging/Prod: backup tự động hằng ngày, giữ ít nhất 7 bản gần nhất.
- Luôn test restore trên môi trường staging trước khi áp dụng production.

## Seed dữ liệu

- Seed admin/user cơ bản nằm trong `DataInitializer`.
- Nếu cần dữ liệu test riêng cho staging, tạo migration `V*_seed_staging.sql`.
