package com.example.webdienthoai.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

@Service
public class ExcelTemplateService {

    public byte[] buildProductTemplate() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet data = wb.createSheet("Sản phẩm");
            Row h = data.createRow(0);
            h.createCell(0).setCellValue("Tên sản phẩm");
            h.createCell(1).setCellValue("Giá");
            h.createCell(2).setCellValue("Danh mục");
            h.createCell(3).setCellValue("Tồn kho");
            h.createCell(4).setCellValue("Mô tả");
            h.createCell(5).setCellValue("Ảnh (URL)");
            h.createCell(6).setCellValue("Nổi bật");
            h.createCell(7).setCellValue("Slug");
            h.createCell(8).setCellValue("Màu sắc");
            h.createCell(9).setCellValue("Dung lượng");
            Row ex = data.createRow(1);
            ex.createCell(0).setCellValue("Ví dụ điện thoại");
            ex.createCell(1).setCellValue(19990000);
            ex.createCell(2).setCellValue("Điện thoại > Apple > iPhone");
            ex.createCell(3).setCellValue(10);
            ex.createCell(4).setCellValue("Mô tả ngắn");
            ex.createCell(5).setCellValue("https://example.com/hinh.jpg");
            ex.createCell(6).setCellValue("có");
            ex.createCell(7).setCellValue("");
            ex.createCell(8).setCellValue("Đen, Titan tự nhiên, Trắng");
            ex.createCell(9).setCellValue("128GB, 256GB");

            Sheet guide = wb.createSheet("Hướng dẫn");
            int r = 0;
            guide.createRow(r++).createCell(0).setCellValue("1) Danh mục: dùng dấu \" > \" giữa các cấp (ví dụ: Điện thoại > Samsung > Galaxy).");
            guide.createRow(r++).createCell(0).setCellValue("2) Giá: nhập số (VNĐ), có thể dùng ô kiểu số trong Excel.");
            guide.createRow(r++).createCell(0).setCellValue("3) Nổi bật: có / không / true / false / 1 / 0.");
            guide.createRow(r++).createCell(0).setCellValue("4) Slug: để trống sẽ tạo từ tên; nếu trùng slug đã có → dòng báo lỗi.");
            guide.createRow(r++).createCell(0).setCellValue("5) Màu sắc / Dung lượng: phân cách bằng dấu phẩy; nếu tên màu có dấu phẩy thì dùng ký tự | thay vì phẩy (tối đa 12 mỗi loại). Để trống nếu không cần.");
            guide.createRow(r++).createCell(0).setCellValue("6) Thông số kỹ thuật không import từ Excel — sau import vào Sửa sản phẩm trên admin để nhập.");
            guide.createRow(r++).createCell(0).setCellValue("7) Xóa dòng ví dụ trước khi import thật.");

            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public byte[] buildUserTemplate() {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet data = wb.createSheet("Người dùng");
            Row h = data.createRow(0);
            h.createCell(0).setCellValue("Email");
            h.createCell(1).setCellValue("Mật khẩu");
            h.createCell(2).setCellValue("Họ tên");
            h.createCell(3).setCellValue("Vai trò");
            h.createCell(4).setCellValue("SĐT");
            h.createCell(5).setCellValue("Giới tính");
            h.createCell(6).setCellValue("Ngày sinh");
            Row ex = data.createRow(1);
            ex.createCell(0).setCellValue("user@example.com");
            ex.createCell(1).setCellValue("matkhau123");
            ex.createCell(2).setCellValue("Nguyễn Văn A");
            ex.createCell(3).setCellValue("customer");
            ex.createCell(4).setCellValue("0909123456");
            ex.createCell(5).setCellValue("nam");
            ex.createCell(6).setCellValue("1995-03-15");

            Sheet guide = wb.createSheet("Hướng dẫn");
            int r = 0;
            guide.createRow(r++).createCell(0).setCellValue("1) Vai trò: admin hoặc customer.");
            guide.createRow(r++).createCell(0).setCellValue("2) Mật khẩu: tối thiểu 6 ký tự; server lưu dạng băm (BCrypt).");
            guide.createRow(r++).createCell(0).setCellValue("3) Ngày sinh: yyyy-MM-dd (ví dụ 1995-03-15).");
            guide.createRow(r++).createCell(0).setCellValue("4) Email trùng trong hệ thống → dòng báo lỗi.");
            guide.createRow(r++).createCell(0).setCellValue("5) Xóa dòng ví dụ trước khi import thật.");

            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
