package com.example.reserve.job;

import java.util.List;
import java.util.UUID;

public interface ExpireRecoveryJob {
    List<UUID> runOnce(int batchSize, String workerId);

//    List<UUID> runMulti(int batchSize, String workerId);
}