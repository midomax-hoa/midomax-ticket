package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceEntryRepository extends JpaRepository<InvoiceEntry, Long> {

    /**
     * Lọc sổ hóa đơn. Mọi tham số đều tùy chọn — null/rỗng nghĩa là không lọc theo
     * tiêu chí đó, nên một câu này phục vụ được cả trang danh sách lẫn export.
     */
    @Query("SELECT i FROM InvoiceEntry i WHERE " +
           "(:period IS NULL OR i.periodKey = :period) AND " +
           "(:category IS NULL OR i.category = :category) AND " +
           "(:vendor IS NULL OR i.vendor = :vendor) AND " +
           "(:expenseType IS NULL OR i.expenseType = :expenseType) AND " +
           "(:paymentStatus IS NULL OR i.paymentStatus = :paymentStatus) AND " +
           "(:search IS NULL OR " +
           "  LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(i.vendor) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(i.poRef) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "  LOWER(i.subCategory) LIKE LOWER(CONCAT('%', :search, '%'))" +
           ") ORDER BY i.transDate DESC, i.id DESC")
    List<InvoiceEntry> filter(
            @Param("period") String period,
            @Param("category") String category,
            @Param("vendor") String vendor,
            @Param("expenseType") String expenseType,
            @Param("paymentStatus") String paymentStatus,
            @Param("search") String search
    );

    /** Các kỳ kế toán đang có dữ liệu, mới nhất trước — đổ vào ô lọc "Tháng/Kỳ". */
    @Query("SELECT DISTINCT i.periodKey FROM InvoiceEntry i WHERE i.periodKey IS NOT NULL ORDER BY i.periodKey DESC")
    List<String> findDistinctPeriods();

    @Query("SELECT DISTINCT i.category FROM InvoiceEntry i WHERE i.category IS NOT NULL AND i.category <> '' ORDER BY i.category")
    List<String> findDistinctCategories();

    @Query("SELECT DISTINCT i.subCategory FROM InvoiceEntry i WHERE i.subCategory IS NOT NULL AND i.subCategory <> '' ORDER BY i.subCategory")
    List<String> findDistinctSubCategories();

    @Query("SELECT DISTINCT i.vendor FROM InvoiceEntry i WHERE i.vendor IS NOT NULL AND i.vendor <> '' ORDER BY i.vendor")
    List<String> findDistinctVendors();

    /**
     * Tìm chứng từ trùng: cùng nhà cung cấp, cùng số tiền, cùng ngày giao dịch.
     * Dùng để cảnh báo lúc nhập (vd hai dòng Adobe "đợt 1" cùng 108.150.000đ).
     */
    List<InvoiceEntry> findByVendorAndAmountAndTransDate(String vendor, long amount, java.time.LocalDate transDate);
}
