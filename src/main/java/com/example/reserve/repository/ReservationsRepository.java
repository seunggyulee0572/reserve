package com.example.reserve.repository;

import com.example.reserve.entity.Reservations;
import com.example.reserve.model.dto.ReservationForPayment;
import com.example.reserve.model.dto.ReservationRefs;
import com.example.reserve.model.enums.ReservationStatus;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationsRepository extends JpaRepository<Reservations, UUID> {

    int countByEvent_IdAndSeats_Id( UUID eventId, UUID seatsId );

    Page<Reservations> findByStatusAndExpiresAtBefore(Pageable pageable,
                                                      ReservationStatus status,
                                                      LocalDateTime now);

    Optional<Reservations> findReservationsBySeats_SeatNumberAndStatus( String seatNumber, ReservationStatus status );

    @Query(value =
            "SELECT BIN_TO_UUID(r.id) AS reservationId, " +
                    "       BIN_TO_UUID(s.id) AS seatId, " +
                    "       s.seat_number AS seatNumber, " +
                    "       r.user_id AS userId, " +
                    "       r.total_amount AS totalAmount " +
                    "FROM reservations r " +
                    "JOIN seats s ON r.seat_id = s.id " +
                    "WHERE r.event_id = :eventId " +
                    "  AND r.status = :status " +
                    "  AND r.expires_at > NOW(6) " +
                    "LIMIT :limit",
            nativeQuery = true)
    List<ReservationForPayment> findActiveReservationsNative(
            @Param("eventId") UUID eventId,
            @Param("status") String status,
            @Param("limit") int limit
    );
    //    @Query(value = """
//                SELECT r.id FROM reservations r
//                WHERE r.status = 'PENDING' AND r.expires_at < NOW()
//                ORDER BY r.expires_at ASC
//                LIMIT :limit """, nativeQuery = true)
//    List<UUID> findExpiredPendingIds(@Param("limit") int limit);

    // (방식 1) naive: 만료된 PENDING id 조회 (락 없음)
    @Query(value = """
        SELECT BIN_TO_UUID(r.id)       AS reservationId,
              BIN_TO_UUID(r.event_id) AS eventId,
              BIN_TO_UUID(r.seat_id)  AS seatId,
              r.user_id               AS userId
          FROM reservations r
         WHERE r.status = 'PENDING'
           AND r.expires_at < NOW(6)
         ORDER BY r.expires_at ASC
         LIMIT :limit
         FOR UPDATE
        """, nativeQuery = true)
    List<ReservationRefs> findExpiredPendingIdsForUpdate(@Param("limit") int limit);

    @Query(value = """
        SELECT BIN_TO_UUID(r.id)       AS reservationId,
              BIN_TO_UUID(r.event_id) AS eventId,
              BIN_TO_UUID(r.seat_id)  AS seatId,
              r.user_id               AS userId
          FROM reservations r
         WHERE r.status = 'PENDING'
           AND r.expires_at < NOW(6)
         ORDER BY r.expires_at ASC
         LIMIT :limit
         FOR UPDATE SKIP LOCKED;
        """, nativeQuery = true)
    List<ReservationRefs> findExpiredPendingIdsForUpdateSkip(@Param("limit") int limit);


    @Modifying
    @Query(value = """
        UPDATE reservations r
           SET r.status = 'EXPIRED',
               r.worker_id = :workerId,
               r.updated_at = NOW(6)
         WHERE r.id IN (:ids)
           AND r.status = 'PENDING'
           AND r.expires_at < NOW(6)
        """, nativeQuery = true)
    int updateExpiredBatch(@Param("ids") List<UUID> ids,
                           @Param("workerId") String workerId);

//    @Modifying
//    @Query(value = """
//        UPDATE reservations r
//           SET r.status = 'EXPIRED',
//               r.updated_at = NOW()
//         WHERE r.id = :id
//           AND r.status = 'PENDING'
//           AND r.expires_at < NOW()
//        """, nativeQuery = true)
//    int markExpiredIfPendingAndExpired(@Param("id") UUID id);

    // PENDING -> EXPIRING 선점, 중복 실행 방지
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE reservations r
           SET r.status    = 'EXPIRING',
               r.worker_id = :workerId,
               r.locked_at = NOW(6),
               r.updated_at = NOW(6)
         WHERE r.status = 'PENDING'
           AND r.expires_at < NOW(6)
         ORDER BY r.expires_at ASC
         LIMIT :limit
        """, nativeQuery = true)
    int claimExpiredPending(@Param("workerId") String workerId, @Param("limit") int limit);

    // 선점된(EXPIRING) 것들 id 조회
    @Query(value = """
        SELECT BIN_TO_UUID(r.id)       AS reservationId,
              BIN_TO_UUID(r.event_id) AS eventId,
              BIN_TO_UUID(r.seat_id)  AS seatId,
              r.user_id               AS userId
          FROM reservations r
         WHERE r.status = 'EXPIRING'
           AND r.worker_id = :workerId
         ORDER BY r.locked_at ASC
         LIMIT :limit
        """, nativeQuery = true)
    List<ReservationRefs> findClaimedExpiringIds(@Param("workerId") String workerId, @Param("limit") int limit);

    // (방식 3) SKIP LOCKED로 만료된 PENDING을 잠그고 가져오기 (MySQL 8)
    @Query(value = """
        SELECT r.id
          FROM reservations r
         WHERE r.status = 'PENDING'
           AND r.expires_at < NOW()
         ORDER BY r.expires_at ASC
         LIMIT :limit
         FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<UUID> lockAndFetchExpiredPendingIdsSkipLocked(@Param("limit") int limit);

    @Modifying
    @Transactional
    @Query(value = """
        UPDATE reservations r
           SET r.status='EXPIRED', r.updated_at=NOW(6), r.worker_id = :workerId
         WHERE r.id = :id
           AND r.status IN ('PENDING', 'EXPIRING' )
        """, nativeQuery = true)
    int updateExpired(@Param("id") UUID id,
                      @Param("workerId") String workerId);
    // (공통) 최종 확정: EXPIRING -> EXPIRED (혹은 PENDING -> EXPIRED)
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE reservations r
           SET r.status='EXPIRED', r.updated_at=NOW()
         WHERE r.id = :id
           AND r.status IN ('PENDING' )
        """, nativeQuery = true)
    int markExpired(@Param("id") UUID id);

    // 결제와 레이스 방지: 결제가 CONFIRMED면 만료로 바꾸지 못하게 조건 강화 버전(권장)
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE reservations r
           SET r.status='EXPIRED', r.updated_at=NOW()
         WHERE r.id = :id
           AND r.status IN ('PENDING')
           AND r.expires_at < NOW()
        """, nativeQuery = true)
    int markExpiredIfStillExpired(@Param("id") UUID id);

    @Query(value = """
    SELECT BIN_TO_UUID(r.event_id) as eventId,
           BIN_TO_UUID(r.seat_id)  as seatId,
           r.user_id               as userId,
           BIN_TO_UUID(r.id)       as reservationId
      FROM reservations r
     WHERE r.id = :id
    """, nativeQuery = true)
    ReservationRefs findRefsById(@Param("id") UUID id);

    @Query("""
        select r from Reservations r
        join fetch r.seats
        join fetch r.event
        where r.id in :ids
        """)
    List<Reservations> findsByReservationsIds(@Param("ids") List<UUID> ids);

    @Query(value = """
        SELECT r.id       AS reservationId,
               r.event_id AS eventId,
               r.seat_id  AS seatId,
               r.user_id  AS userId
          FROM reservations r
         WHERE r.status = :status
           AND r.expires_at < :now
         ORDER BY r.expires_at ASC
         LIMIT :limit
    """, nativeQuery = true)
    List<ReservationRefs> findExpiredPendingRefs(
            @Param("limit") int limit,
            @Param("status") ReservationStatus status,
            @Param("now") LocalDateTime now
    );
}
