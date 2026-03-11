# reserve
예약/결제 시스템 운영환경 테스트

<img width="1408" height="768" alt="설계도" src="https://github.com/user-attachments/assets/7d576b78-3718-4db3-b7b6-fda511b92682" />

## 서비스 운영
Spring Boot, Mysql, Kafka, Redis, K6, Prometheus, Alert Manager, Grafana

## 시나리오

### 1. 좌석 예약 동시성 제어 — 비관적 락 vs 낙관적 락 vs 원자적 업데이트 비교

좌석 예약, 결제 시스템에서 비관적 lock vs 낙관적 lock 차이 및 구현 방식

versio을 통한 낙관적 lock 방식

동시에 읽고, 수정을 요청하는 거 모두 허용 (선착순 1명만 선점 가능 / 나머지는 error)
- 속도 빠름, 하지만 error 처리 필요

비관적 lock을 통한 비관적 lock 방식

최초로 lock을 가진 사용자만 일기, 수정 가능

select for update 방식 vs 원자적 update vs Pessimistic Lock

원자적 update ( Row-lock(베타적락) 획득, 동시에 읽기 가능, lock 유지시간 짧음 )

select for update ( 베타적락 획득, 다른 트랜잭션 읽기 가능, lock 유지시간 긺 )

낙관적 lock ( lock 획득 x, 동시에 읽기,업데이트 가능, version을 기준으로 하나만 update )

일반적으로 DB에서 쓰기 lock(베타적lock)을 획득하게 되면 다른 트랜잭션에서 읽기,쓰기시 wait 

하지만 innodb에서 version 을 통해서 row를 관리하고 있기 때문에 읽기 요청시 이전 버전의 snapshot은 접근 가능

## 테스트 해석

### 테스트 조건
- 동시 요청: 500 threads
- 동일 좌석 대상
- CountDownLatch로 동시 시작 보장
- InnoDB 내부 lock 지표 기반 분석


## pessimistic_lock

```
pessimistic_lock , total : 537ms
=== pessimistic_lock : InnoDB row lock status diff ===
Innodb_row_lock_current_waits: before=0 after=0 diff=0
Innodb_row_lock_time: before=162830 after=165946 diff=3116
Innodb_row_lock_time_avg: before=44 after=39 diff=-5
Innodb_row_lock_time_max: before=170 after=170 diff=0
Innodb_row_lock_waits: before=3667 after=4166 diff=499
==== pessimistic_lock ====
threads=500 success=1 fail=499
fail reasons={IllegalStateException:already reserved=499}
fastest success=[AttemptResult[idx=284, success=true, reservationId=d458a4c2-7480-4167-a5a2-7b11e0e7e91d, errorType=null, errorMsg=null, tookMs=100]]
[DB] seatStatus=RESERVED reservedBy=u-325 reservationCnt=1 availableCount=973


비관적 락에서는 최초 1개의 트랜잭션이 row lock을 획득한 이후,  
나머지 요청들이 동일 row에 접근하면서 lock wait 상태에 진입한다.

InnoDB 내부 지표에서 lock wait 횟수가 499 증가한 것은  
500개의 동시 요청 중 1개를 제외한 거의 모든 요청이 실제로 row lock을 기다렸다는 의미다.

이 방식은 정합성은 가장 확실하게 보장되지만,  
락을 잡은 트랜잭션이 커밋될 때까지 다른 요청이 대기하게 되므로  
총 수행 시간(total time)이 가장 길게 나타난다.

```

## no_lock

```
no_lock , total : 339ms
=== no_lock : InnoDB row lock status diff ===
Innodb_row_lock_current_waits: before=0 after=0 diff=0
Innodb_row_lock_time: before=165946 after=166308 diff=362
Innodb_row_lock_time_avg: before=39 after=39 diff=0
Innodb_row_lock_time_max: before=170 after=170 diff=0
Innodb_row_lock_waits: before=4166 after=4173 diff=7
==== no_lock ====
threads=500 success=8 fail=492
fail reasons={IllegalArgumentException:seat not found=492}
fastest success=[AttemptResult[idx=466, success=true, reservationId=fd703051-9c17-46cf-b8be-59d1e8afd62e, errorType=null, errorMsg=null, tookMs=107], AttemptResult[idx=122, success=true, reservationId=6b1dfc8f-d82d-4d01-a64a-061b4bbe9d0f, errorType=null, errorMsg=null, tookMs=112], AttemptResult[idx=14, success=true, reservationId=871633d7-4413-4197-81b4-0280a08095a7, errorType=null, errorMsg=null, tookMs=133], AttemptResult[idx=123, success=true, reservationId=e62bd3e2-7dff-4ec7-a67d-74159b2fb458, errorType=null, errorMsg=null, tookMs=137], AttemptResult[idx=106, success=true, reservationId=dd28a88b-5cb7-4088-a4f0-b45a4fa18cf1, errorType=null, errorMsg=null, tookMs=147]]
[DB] seatStatus=RESERVED reservedBy=u-121 reservationCnt=8 availableCount=976


락 없이 처리한 경우 여러 트랜잭션이 동시에 동일 좌석을 읽고 수정하게 되면서  
중복 예약이 실제로 발생하였다.

lock wait 지표는 거의 증가하지 않았으며,  
이는 DB 레벨에서 경쟁을 제어하지 않았기 때문이다.

속도는 가장 빠르게 보이지만,  
좌석 시스템에서는 사용하기 어려운 방식이다.

```

## atomic_update

```
atomic_update , total : 401ms
=== atomic_update : InnoDB row lock status diff ===
Innodb_row_lock_current_waits: before=0 after=0 diff=0
Innodb_row_lock_time: before=166308 after=168180 diff=1872
Innodb_row_lock_time_avg: before=39 after=35 diff=-4
Innodb_row_lock_time_max: before=170 after=170 diff=0
Innodb_row_lock_waits: before=4173 after=4672 diff=499
atomic_update , total : 396
==== atomic_update ====
threads=500 success=1 fail=499
fail reasons={RuntimeException:sold out=499}
fastest success=[AttemptResult[idx=162, success=true, reservationId=a416ec2f-49fa-453b-864f-3e82de7c0697, errorType=null, errorMsg=null, tookMs=99]]
[DB] seatStatus=RESERVED reservedBy=u-203 reservationCnt=1 availableCount=975


원자적 UPDATE는 WHERE 조건으로 선점 여부를 판단하는 방식이다.


UPDATE seats
SET status='RESERVED'
WHERE id=? AND status='AVAILABLE'


동시에 많은 UPDATE가 들어오면 실제로는 row lock 경쟁이 발생한다.  
테스트 결과에서도 pessimistic과 동일하게 lock wait이 499 증가하였다.

다만 lock을 잡는 구간이 짧고,  
조건 불일치 시 빠르게 실패하기 때문에  
비관적 락보다는 total 시간이 줄어든 모습을 보였다.

즉, atomic update는 lock이 없다는 의미가 아니라  
lock 유지 시간이 상대적으로 짧은 구조라고 보는 것이 맞다.

```

## optimistic

```
optimistic , total : 339ms
=== optimistic : InnoDB row lock status diff ===
Innodb_row_lock_current_waits: before=0 after=0 diff=0
Innodb_row_lock_time: before=168180 after=168398 diff=218
Innodb_row_lock_time_avg: before=35 after=35 diff=0
Innodb_row_lock_time_max: before=170 after=170 diff=0
Innodb_row_lock_waits: before=4672 after=4679 diff=7
==== optimistic ====
threads=500 success=1 fail=499
fail reasons={ObjectOptimisticLockingFailureException:Batch update returned unexpected row count from update 0 (expected row count 1 but was 0) [update seats set base_price=?,event_id=?,reserved_at=?,reserved_by=?,seat_number=?,status=?,version=? where id=? and version=?] for entity [com.example.reserve.entity.Seats with id '38f13c18-4d53-4078-bef3-16afdd1a775e']=7, IllegalArgumentException:sold out=492}
fastest success=[AttemptResult[idx=56, success=true, reservationId=8b3b0381-a155-4a30-a9ca-3781b9a228f8, errorType=null, errorMsg=null, tookMs=113]]
[DB] seatStatus=RESERVED reservedBy=u-97 reservationCnt=1 availableCount=974


낙관적 락에서는 먼저 SELECT를 통해 여러 요청이 동시에 좌석을 읽는다.

이후 일부 트랜잭션이 동시에 UPDATE를 시도하지만,  
version 조건에 의해 실제 반영되는 것은 하나뿐이다.

여기서 중요한 점은:

- UPDATE 시도 시 row lock 경쟁이 일부 발생할 수는 있지만
- 대부분은 version 조건 불일치로 빠르게 실패한다는 점이다.

즉, pessimistic이나 atomic처럼 대기(wait) 중심의 경쟁이 아니라  
조건 기반 충돌(conflict) 방식으로 경쟁을 해결한다.

lock wait 증가량이 7 정도로 작게 나온 것도  
실제로 UPDATE 단계까지 진입한 요청 수가 적었기 때문으로 해석할 수 있다.

```

## 종합 비교

| 방식 | lock wait 증가 | total 시간 | 특징 |
|---|---|---|---|
| pessimistic_lock | 499 | 가장 김 | 강한 정합성, 높은 대기 |
| atomic_update | 499 | 중간 | lock 사용하지만 유지시간 짧음 |
| optimistic | 7 | 짧음 | wait 대신 version 충돌 |
| no_lock | 7 | 짧음 | 빠르지만 정합성 깨짐 |

---

## 정리

이번 테스트에서는 InnoDB 내부 지표를 통해  
각 동시성 전략이 실제 DB에서 어떻게 동작하는지 확인할 수 있었다.

비관적 락과 원자적 UPDATE는 모두 row-level lock 경쟁이 발생하며,  
특히 동일 row에 대량 요청이 들어오는 상황에서는  
대기 시간이 증가하는 경향을 보인다.

반면 낙관적 락은 lock wait보다는 version 충돌로 경쟁을 해결하기 때문에  
동시 요청 수가 많아도 DB 대기 시간 자체는 크게 증가하지 않는다.

좌석 예약과 같이 선점 경쟁이 강한 시스템에서는  
정합성과 처리량 사이의 균형을 고려하여  
atomic update 또는 optimistic 전략을 상황에 맞게 선택하는 것이 필요하다.

## 시나리오 2. 만료 예약 처리 스케줄러 동시성 분석

### 개요

예약 만료 처리 스케줄러가 다중 서버(5 worker) 환경에서 동시에 실행될 때,
각 동시성 제어 방식이 어떻게 동작하는지 비교한다.

---

## 테스트 조건

| 항목 | 값 |
| :--- | :--- |
| 동시 worker | 5 threads |
| 처리 대상 | 만료된 예약 (`status=PENDING`, `expires_at < NOW()`) |
| 측정 지표 | InnoDB row lock 지표, total 시간, success/fail 수 |
| 비교 케이스 | 경합 없을 때 (데이터 적음) vs 경합 발생 시 (데이터 많음) |

---

## 테스트 방식 설명

| 테스트 방식 | 설명 |
| :--- | :--- |
| **naive** | 락 없이 단순 SELECT + UPDATE, Lost Update 발생 가능 (version 없으면) |
| **select_for_update** | `FOR UPDATE`로 대상 row 전체를 잠금, 순차 처리 |
| **select_for_update_skip** | `SKIP LOCKED`로 잠긴 row 건너뜀, worker별 분산 처리 |
| **select_for_update_skip_batch** | `SKIP LOCKED` + 배치 처리, 한 번에 여러 row 처리 |
| **claim_update** | `UPDATE WHERE status='PENDING'`으로 원자적 선점 |

---

## 결과 비교

### 경합 없을 때 (데이터 적음)

| 방식 | total | success | fail | lock wait 증가 |
| :--- | :---: | :---: | :---: | :---: |
| **naive** | 175ms | 5 | 0 | 4 |
| **select_for_update** | 113ms | 5 | 0 | 4 |
| **select_for_update_skip** | 105ms | 5 | 0 | 4 |
| **select_for_update_skip_batch** | 113ms | 5 | 0 | 4 |
| **claim_update** | 121ms | 5 | 0 | 13 |

### 경합 발생 시 (데이터 많음)

| 방식 | total | success | fail | lock wait 증가 | lock time 증가 |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **naive** | 175ms | 5 | 0 | 4 | Lost Update 발생 |
| **select_for_update** | 6095ms | 1 | 4 | 9 | 18123ms |
| **select_for_update_skip** | 3125ms | 5 | 0 | 4 | 119ms |
| **select_for_update_skip_batch** | 2564ms | 5 | 0 | 4 | 52ms |
| **claim_update** | 6122ms | 1 | 4 | 9 | 18112ms |

---

## 방식별 상세 분석

### naive — 락 없음, Lost Update 발생

락을 전혀 적용하지 않은 경우 여러 worker가 동시에 동일한 row를 읽고 수정하게 되면서 Lost Update가 실제로 발생한다.

경합이 없을 때는 5개 worker 모두 성공하는 것처럼 보이지만, 실제로는 같은 row를 여러 worker가 중복 처리한 상태다.
나중에 쓴 값이 이전 값을 덮어써도 감지하지 못하기 때문에 처리 건수가 실제보다 과다 집계될 수 있다.

```
=== naive : InnoDB row lock status diff ===
Innodb_row_lock_waits: before=1051 after=1055 diff=4
naive_schedule , total : 175

==== naive_schedule ====
threads=5 success=5 fail=0
fail reasons={}

[DB] seatStatus=AVAILABLE reservationId=ebc6498b reservationStatus=EXPIRED availableCount=1000
[DB] seatStatus=AVAILABLE reservationId=e65dc094 reservationStatus=EXPIRED availableCount=1000
[DB] seatStatus=AVAILABLE reservationId=0c1a0722 reservationStatus=EXPIRED availableCount=1000
```

> ⚠️ `success=5`로 전부 성공처럼 보이지만, 동일 `reservationId`가 여러 worker에서 중복 처리된 상태.
> version 없이는 Lost Update를 감지할 수 없다.

---

### select_for_update — 경합 시 timeout 발생

`FOR UPDATE`로 전체 대상 row를 한 번에 잠그는 방식이다.
worker 하나가 락을 잡고 처리하는 동안 나머지 4개 worker가 같은 row에 접근하려다 대기 상태에 진입한다.

경합이 없을 때는 113ms로 빠르지만, 만료된 예약이 많아 처리 시간이 길어지면 다른 worker들이 transaction timeout(5초)을 초과해
`QueryTimeoutException`과 `TransactionTimedOutException`이 발생한다.

```
=== select_for_update : InnoDB row lock status diff ===
Innodb_row_lock_time:  before=1080955 after=1099078 diff=18123
Innodb_row_lock_waits: before=72539   after=72548   diff=9
select_for_update_schedule , total : 6095

==== select_for_update_schedule ====
threads=5 success=1 fail=4
fail reasons={
  QueryTimeoutException: Statement cancelled due to timeout=3,
  TransactionTimedOutException: Transaction timed out: deadline was Fri Feb 27 03:10:57 UTC 2026=1
}

fastest success=[idx=3, tookMs=3075]
[DB] seatStatus=AVAILABLE reservationId=96b4a38c reservationStatus=EXPIRED availableCount=943
[DB] seatStatus=AVAILABLE reservationId=9134003a reservationStatus=EXPIRED availableCount=943
```

> lock_time이 **18123ms** 증가 → 락 대기로 인해 4개 worker가 timeout으로 실패.
> 다중 서버 환경에서 데이터가 몰릴수록 병목이 심화되는 구조다.

---

### select_for_update_skip — SKIP LOCKED로 분산 처리

`SKIP LOCKED`를 적용하면 이미 잠긴 row를 건너뛰고 잠기지 않은 row부터 처리한다.
5개 worker가 각자 다른 row를 잡아서 처리하기 때문에 timeout 없이 전부 성공한다.

lock wait 증가가 4에 불과한 것도 worker들이 충돌 없이 분산 처리했기 때문이다.
다만 각 worker가 순차적으로 row를 하나씩 처리하는 구조라 처리량이 제한된다.

```
=== select_for_update_skip : InnoDB row lock status diff ===
Innodb_row_lock_time:  before=1099078 after=1099197 diff=119
Innodb_row_lock_waits: before=72548   after=72552   diff=4
select_for_update_skip_schedule , total : 3125

==== select_for_update_skip_schedule ====
threads=5 success=5 fail=0
fail reasons={}

fastest success=[idx=4, tookMs=3086]
[DB] seatStatus=AVAILABLE reservationId=b4d4b3a2 reservationStatus=EXPIRED availableCount=958
[DB] seatStatus=AVAILABLE reservationId=a5a1475e reservationStatus=EXPIRED availableCount=958

[skip_once] tookMs=105, threads ok=5 fail=0, processed=15
throughput=142.86/s, avg=83ms p95=99ms p99=99ms max=99ms
```

> lock_time 증가 **119ms** → 실질적인 lock 대기 없음.
> 5개 worker 모두 성공, 중복 처리 없음.

---

### select_for_update_skip_batch — SKIP LOCKED + 배치, 가장 균형적

`SKIP LOCKED`에 배치 처리를 결합한 방식으로 이번 테스트에서 가장 안정적인 결과를 보였다.
worker당 여러 row를 한 번에 가져와 처리하기 때문에 총 처리 시간이 단축되고 처리한 총 건수도 많다.

```
=== select_for_update_skip_batch : InnoDB row lock status diff ===
Innodb_row_lock_time:  before=1407553 after=1407605 diff=52
Innodb_row_lock_waits: before=132482  after=132486  diff=4
select_for_update_skip_batch_schedule , total : 2564

==== select_for_update_skip_batch_schedule ====
threads=5 success=5 fail=0
fail reasons={}

fastest success=[idx=2, tookMs=2549]
[DB] seatStatus=AVAILABLE reservationId=9c1f3d23 reservationStatus=EXPIRED availableCount=978
[DB] seatStatus=AVAILABLE reservationId=ce5fb06e reservationStatus=EXPIRED availableCount=978

[skip_batch] tookMs=2564, threads ok=5 fail=0, processed=40
throughput=15.60/s, avg=2552ms p95=2557ms p99=2557ms max=2557ms
```

> lock_time 증가 **52ms** → 가장 낮은 lock 경합.
> `processed=40`으로 skip_once(15건) 대비 약 **2.7배** 더 많은 건수를 처리.

---

### claim_update — 원자적 선점, 경합 시 select_for_update와 동일한 문제

`UPDATE WHERE status='PENDING' AND expires_at < NOW()`로 원자적 선점을 시도하는 방식이다.
경합이 없을 때는 121ms로 가장 빠르지만, 데이터가 많아지면 여러 worker가 동시에 같은 row를 UPDATE하려다 lock 경쟁이 발생한다.

결과적으로 select_for_update와 거의 동일한 패턴으로 timeout이 발생한다.

```
=== claim_update : InnoDB row lock status diff ===
Innodb_row_lock_time:  before=1099717 after=1117829 diff=18112
Innodb_row_lock_waits: before=72573   after=72582   diff=9
claim_update_schedule , total : 6122

==== claim_update_schedule ====
threads=5 success=1 fail=4
fail reasons={
  TransactionTimedOutException: Transaction timed out: deadline was Fri Feb 27 03:23:31 UTC 2026=1,
  QueryTimeoutException: Query execution was interrupted=3
}

fastest success=[idx=0, tookMs=3095]
[DB] seatStatus=AVAILABLE reservationId=df151bc3 reservationStatus=EXPIRED availableCount=991
[DB] seatStatus=AVAILABLE reservationId=de911de7 reservationStatus=EXPIRED availableCount=991
```

> lock_time **18112ms** 증가 → select_for_update(18123ms)와 거의 동일.
> 경합 상황에서는 원자적 UPDATE도 결국 row lock 경쟁을 피할 수 없다.

---

## 종합 비교

### 경합 없을 때

모든 방식이 100~175ms 내에서 전부 성공한다.
차이가 크지 않아 어떤 방식을 선택해도 성능상 무관하다.

### 경합 발생 시 ← 실운영 환경 기준

| 방식 | total | success | lock_time 증가 | 비고 |
| :--- | :---: | :---: | :---: | :--- |
| **select_for_update_skip_batch** | 2564ms | 5/5 | +52ms | ✅ 처리량·안정성 가장 균형적 |
| **select_for_update_skip** | 3125ms | 5/5 | +119ms | ✅ 안정적, 처리량은 배치보다 낮음 |
| **select_for_update** | 6095ms | 1/5 | +18123ms | ❌ timeout 발생 |
| **claim_update** | 6122ms | 1/5 | +18112ms | ❌ timeout 발생 |
| **naive** | 175ms | 5/5 | +4ms | ❌ Lost Update 발생, 정합성 깨짐 |

---

## 결론

경합이 없는 환경에서는 모든 방식이 비슷한 성능을 보인다.
하지만 만료된 예약이 대량으로 쌓이는 경합 상황에서는 `SKIP LOCKED` 기반 방식만 안정적으로 동작했다.

- **`select_for_update`** 와 **`claim_update`** 는 단일 서버에서 빠르게 동작하지만, 다중 서버 환경에서 데이터가 몰리면 lock 대기가 누적되어 timeout이 연쇄적으로 발생한다.
- **`naive`** 는 빠르지만 Lost Update가 발생해 같은 예약이 여러 번 처리되는 정합성 문제가 생긴다.

실무에서 스케줄러를 다중 서버로 운영할 경우
**`SKIP LOCKED + 배치 처리` 조합이 처리량, 안정성, lock 경합 최소화 측면에서 가장 적합한 선택이다.**

