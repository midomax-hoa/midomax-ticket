package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ToolExpenseRepository extends JpaRepository<ToolExpense, Long> {

    List<ToolExpense> findByFundIdOrderByCreatedAtDesc(Long fundId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM ToolExpense e WHERE e.fundId = :fundId")
    long sumAmountByFundId(@Param("fundId") Long fundId);

    long countByFundId(Long fundId);
}
