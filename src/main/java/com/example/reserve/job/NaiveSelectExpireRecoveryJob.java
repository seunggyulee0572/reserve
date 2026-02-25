package com.example.reserve.job;

import com.example.reserve.domain.ExpireRecoverySupport;
import com.example.reserve.repository.ReservationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NaiveSelectExpireRecoveryJob implements ExpireRecoveryJob {

    private final ReservationsRepository reservationsRepository;
    private final ExpireRecoverySupport support;

    @Override
    @Transactional
    public int runOnce(int batchSize, String workerId) {
        var ids = reservationsRepository.findExpiredPendingIds(batchSize);

        int done = 0;
        for (UUID id : ids) {
            // 락 오래 잡는 실험용(나쁜 케이스)
//            sleepQuietly(1000);

            // 경쟁 없음,
            if (support.expireOne(id)) done++;
        }
        return done;
    }

    private void sleepQuietly(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
    }
}