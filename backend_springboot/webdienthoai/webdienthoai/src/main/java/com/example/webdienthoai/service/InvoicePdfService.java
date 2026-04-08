package com.example.webdienthoai.service;

import com.example.webdienthoai.entity.Address;
import com.example.webdienthoai.entity.Order;
import com.example.webdienthoai.entity.OrderItem;
import com.example.webdienthoai.entity.User;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InvoicePdfService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(VN_ZONE);

    private BaseFont baseFontBody() throws IOException, DocumentException {
        ClassPathResource res = new ClassPathResource("fonts/NotoSans-Regular.ttf");
        if (!res.exists()) {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        }
        try (InputStream is = res.getInputStream()) {
            byte[] bytes = is.readAllBytes();
            return BaseFont.createFont("NotoSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
        }
    }

    private static String vnd(BigDecimal n) {
        if (n == null) {
            return "0 ₫";
        }
        long v = n.setScale(0, RoundingMode.HALF_UP).longValue();
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return nf.format(v) + " ₫";
    }

    private static String lineDescription(OrderItem it) {
        String base = it.getProductName() != null ? it.getProductName() : "—";
        List<String> parts = new ArrayList<>();
        if (it.getSelectedColor() != null && !it.getSelectedColor().isBlank()) {
            parts.add(it.getSelectedColor().trim());
        }
        if (it.getSelectedStorage() != null && !it.getSelectedStorage().isBlank()) {
            parts.add(it.getSelectedStorage().trim());
        }
        if (parts.isEmpty()) {
            return base;
        }
        return base + " (" + String.join(", ", parts) + ")";
    }

    private static String buyerText(Address addr, User user) {
        if (addr == null) {
            return user != null && user.getName() != null ? user.getName() : "—";
        }
        String line2 = addr.getLine2() != null && !addr.getLine2().isBlank() ? ", " + addr.getLine2().trim() : "";
        String statePart = addr.getState() != null && !addr.getState().isBlank() ? ", " + addr.getState().trim() : "";
        String zipPart = addr.getZipCode() != null && !addr.getZipCode().isBlank() ? " " + addr.getZipCode().trim() : "";
        String countryPart = addr.getCountry() != null && !addr.getCountry().isBlank() ? ", " + addr.getCountry().trim() : "";

        List<String> lines = new ArrayList<>();
        String name = addr.getName() != null ? addr.getName().trim() : "—";
        lines.add(name);
        String street = addr.getStreet() != null ? addr.getStreet().trim() : "—";
        lines.add(street + line2);
        String cityLine = (addr.getCity() != null ? addr.getCity().trim() : "—") + statePart + zipPart + countryPart;
        lines.add(cityLine);
        if (addr.getPhone() != null && !addr.getPhone().isBlank()) {
            lines.add("ĐT: " + addr.getPhone().trim());
        }
        return lines.stream().filter(Objects::nonNull).collect(Collectors.joining("\n"));
    }

    private static PdfPCell cell(Phrase phrase, int align) {
        PdfPCell c = new PdfPCell(phrase);
        c.setBorderWidth(0.5f);
        c.setPadding(6f);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return c;
    }

    @Transactional(readOnly = true)
    public byte[] buildInvoicePdf(Order order, Address shippingAddress) throws IOException, DocumentException {
        BaseFont bf = baseFontBody();
        Font titleFont = new Font(bf, 16, Font.BOLD);
        Font headFont = new Font(bf, 10, Font.BOLD);
        Font normal = new Font(bf, 9, Font.NORMAL);
        Font small = new Font(bf, 8, Font.NORMAL);

        User user = order.getUser();
        String buyer = buyerText(shippingAddress, user);

        BigDecimal subtotal = order.getSubtotal() != null ? order.getSubtotal() : BigDecimal.ZERO;
        BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal ship = order.getShippingCost() != null ? order.getShippingCost() : BigDecimal.ZERO;
        BigDecimal total = order.getTotalPrice() != null ? order.getTotalPrice() : BigDecimal.ZERO;

        Instant created = order.getCreatedAt();
        String invoiceDate = created != null ? DATE_FMT.format(created) : "—";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        doc.add(new Paragraph("HÓA ĐƠN BÁN HÀNG", titleFont));
        doc.add(new Paragraph(" ", small));
        doc.add(new Phrase("Mã đơn: ", headFont));
        doc.add(new Phrase(String.valueOf(order.getId()), normal));
        doc.add(new Paragraph("Ngày: " + invoiceDate, normal));
        doc.add(new Paragraph(" ", small));

        PdfPTable meta = new PdfPTable(2);
        meta.setWidthPercentage(100);
        meta.setWidths(new float[] { 1f, 1f });

        PdfPCell seller = new PdfPCell();
        seller.setBorder(0);
        seller.setPaddingBottom(8f);
        seller.addElement(new Phrase("Bên bán", headFont));
        seller.addElement(new Phrase("TechHome", normal));
        seller.addElement(new Phrase("Cửa hàng điện thoại / điện tử", small));

        PdfPCell buyerCell = new PdfPCell();
        buyerCell.setBorder(0);
        buyerCell.setPaddingBottom(8f);
        buyerCell.addElement(new Phrase("Bên mua", headFont));
        buyerCell.addElement(new Phrase(buyer, normal));

        meta.addCell(seller);
        meta.addCell(buyerCell);
        doc.add(meta);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 6f, 38f, 10f, 20f, 26f });
        table.setSpacingBefore(10f);

        String[] headers = { "STT", "Mô tả", "SL", "Đơn giá", "Thành tiền" };
        for (String h : headers) {
            table.addCell(cell(new Phrase(h, headFont), Element.ALIGN_CENTER));
        }

        List<OrderItem> items = order.getItems() != null ? order.getItems() : List.of();
        int idx = 1;
        for (OrderItem it : items) {
            int qty = it.getQuantity() != null ? it.getQuantity() : 0;
            BigDecimal unit = it.getPriceAtOrder() != null ? it.getPriceAtOrder() : BigDecimal.ZERO;
            BigDecimal line = it.getLineTotal() != null ? it.getLineTotal() : unit.multiply(BigDecimal.valueOf(qty));

            table.addCell(cell(new Phrase(String.valueOf(idx), normal), Element.ALIGN_CENTER));
            table.addCell(cell(new Phrase(lineDescription(it), normal), Element.ALIGN_LEFT));
            table.addCell(cell(new Phrase(String.valueOf(qty), normal), Element.ALIGN_CENTER));
            table.addCell(cell(new Phrase(vnd(unit), normal), Element.ALIGN_RIGHT));
            table.addCell(cell(new Phrase(vnd(line), normal), Element.ALIGN_RIGHT));
            idx++;
        }

        doc.add(table);

        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(45);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingBefore(14f);
        totals.setWidths(new float[] { 1.2f, 1f });

        totals.addCell(cell(new Phrase("Tạm tính", normal), Element.ALIGN_LEFT));
        totals.addCell(cell(new Phrase(vnd(subtotal), normal), Element.ALIGN_RIGHT));
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            String coupon = order.getCouponCode() != null && !order.getCouponCode().isBlank()
                    ? " (" + order.getCouponCode() + ")"
                    : "";
            totals.addCell(cell(new Phrase("Giảm giá" + coupon, normal), Element.ALIGN_LEFT));
            totals.addCell(cell(new Phrase("-" + vnd(discount), normal), Element.ALIGN_RIGHT));
        }
        totals.addCell(cell(new Phrase("Phí vận chuyển", normal), Element.ALIGN_LEFT));
        totals.addCell(cell(new Phrase(ship.compareTo(BigDecimal.ZERO) == 0 ? "Miễn phí" : vnd(ship), normal), Element.ALIGN_RIGHT));
        totals.addCell(cell(new Phrase("Tổng thanh toán", headFont), Element.ALIGN_LEFT));
        totals.addCell(cell(new Phrase(vnd(total), headFont), Element.ALIGN_RIGHT));

        doc.add(totals);
        doc.close();
        return baos.toByteArray();
    }
}
