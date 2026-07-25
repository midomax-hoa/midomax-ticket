package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BudgetItemRepository extends JpaRepository<BudgetItem, Long> {

    List<BudgetItem> findByFundIdOrderByGroupCategoryAscSubCategoryAscIdAsc(Long fundId);

    void deleteByFundId(Long fundId);

    @Query("SELECT COALESCE(SUM(b.allocatedAmount), 0) FROM BudgetItem b WHERE b.fundId = :fundId")
    long sumAllocatedAmountByFundId(@Param("fundId") Long fundId);

    @Query("SELECT COALESCE(SUM(b.spentAmount), 0) FROM BudgetItem b WHERE b.fundId = :fundId")
    long sumSpentAmountByFundId(@Param("fundId") Long fundId);
}
