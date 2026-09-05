package vn.midomax.helpdesk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    List<Shift> findByActiveTrueOrderByStartTimeAsc();
    Optional<Shift> findFirstByIsDefaultTrue();
}
