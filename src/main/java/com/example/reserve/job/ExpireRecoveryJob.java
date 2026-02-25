package com.example.reserve.job;

public interface ExpireRecoveryJob {
    int runOnce(int batchSize, String workerId);
}