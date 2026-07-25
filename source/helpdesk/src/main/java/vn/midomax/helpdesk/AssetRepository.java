package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findAllByOrderByCreatedAtDesc();

    List<Asset> findByCategoryIdOrderByCreatedAtDesc(Long categoryId);

    boolean existsByInventoryCode(String inventoryCode);

    long countByCategoryId(Long categoryId);

    long countByStatus(String status);

    /**
     * Lọc theo danh mục / trạng thái / từ khóa. Tham số null hoặc rỗng thì bỏ qua điều kiện đó.
     * Từ khóa tìm trên mã kiểm kê, người được giao, model, serial, nhà sản xuất.
     */
    @Query("SELECT a FROM Asset a WHERE "
            + "(:categoryId IS NULL OR a.categoryId = :categoryId) AND "
            + "(:status IS NULL OR :status = '' OR a.status = :status) AND "
            + "(:keyword IS NULL OR :keyword = '' OR "
            + " LOWER(a.inventoryCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + " LOWER(a.assignedToName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + " LOWER(a.model) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + " LOWER(a.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR "
            + " LOWER(a.manufacturer) LIKE LOWER(CONCAT('%', :keyword, '%'))) "
            + "ORDER BY a.createdAt DESC")
    List<Asset> search(@Param("categoryId") Long categoryId,
                       @Param("status") String status,
                       @Param("keyword") String keyword);

    /** Mã kiểm kê lớn nhất theo tiền tố, dùng để sinh mã kế tiếp. */
    @Query("SELECT MAX(a.inventoryCode) FROM Asset a WHERE a.inventoryCode LIKE CONCAT(:prefix, '%')")
    String findMaxInventoryCodeByPrefix(@Param("prefix") String prefix);
}
