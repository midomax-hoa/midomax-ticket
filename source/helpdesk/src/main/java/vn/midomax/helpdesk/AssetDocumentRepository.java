package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetDocumentRepository extends JpaRepository<AssetDocument, Long> {

    List<AssetDocument> findByAssetIdOrderByUploadedAtDesc(Long assetId);

    long countByAssetId(Long assetId);

    void deleteByAssetId(Long assetId);

    /** Đếm số biên bản của nhiều tài sản một lượt, tránh N+1 khi vẽ danh sách. */
    List<AssetDocument> findByAssetIdIn(List<Long> assetIds);
}
