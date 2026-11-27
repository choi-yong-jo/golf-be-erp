package com.golf.erp.round;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoundAssignmentRepository extends JpaRepository<RoundAssignment, Long> {
	List<RoundAssignment> findByRoundIdOrderByAssignedAtAscIdAsc(Long roundId);

	List<RoundAssignment> findByRoundIdAndReleasedAtIsNull(Long roundId);

	List<RoundAssignment> findByRoundIdInAndReleasedAtIsNull(Collection<Long> roundIds);

	Optional<RoundAssignment> findFirstByCaddieIdAndReleasedAtIsNull(Long caddieId);

	Optional<RoundAssignment> findFirstByCartIdAndReleasedAtIsNull(Long cartId);
}
