package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetUsageHistoryRepository extends JpaRepository<AssetUsageHistory, Long> {
    List<AssetUsageHistory> findByAssetIdOrderByAssignedDateDesc(Long assetId);
    Optional<AssetUsageHistory> findFirstByAssetIdAndReturnedDateIsNullOrderByAssignedDateDesc(Long assetId);
}
