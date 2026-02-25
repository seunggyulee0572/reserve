# reserve
예약/결제 시스템 운영환경 테스트

<img width="1408" height="768" alt="설계도" src="https://github.com/user-attachments/assets/7d576b78-3718-4db3-b7b6-fda511b92682" />

## 서비스 운영
Spring Boot, Mysql, Kafka, Redis, K6, Prometheus, Alert Manager, Grafana

## 시나리오

### 1.
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


# reserve
예약/결제 시스템 운영환경 테스트

<img width="1408" height="768" alt="설계도" src="https://github.com/user-attachments/assets/7d576b78-3718-4db3-b7b6-fda511b92682" />

## 서비스 운영
Spring Boot, Mysql, Kafka, Redis, K6, Prometheus, Alert Manager, Grafana


## 시나리오

### 1.
좌석 예약, 결제 시스템에서 비관적 lock vs 낙관적 lock 차이 및 구현 방식

version을 통한 낙관적 lock 방식

동시에 읽고, 수정을 요청하는 거 모두 허용 (선착순 1명만 선점 가능 / 나머지는 error)

- 속도 빠름, 하지만 error 처리 필요

비관적 lock을 통한 비관적 lock 방식

최초로 lock을 가진 사용자만 읽기, 수정 가능

select for update 방식 vs 원자적 update vs Pessimistic Lock

원자적 update ( Row-lock(배타적락) 획득, 동시에 읽기 가능, lock 유지시간 짧음 )

select for update ( 배타적락 획득, 다른 트랜잭션 읽기 가능, lock 유지시간 긺 )

낙관적 lock ( lock 획득 x, 동시에 읽기,업데이트 가능, version을 기준으로 하나만 update )

일반적으로 DB에서 쓰기 lock(배타적lock)을 획득하게 되면 다른 트랜잭션에서 읽기,쓰기시 wait 

하지만 InnoDB는 MVCC 기반으로 version snapshot을 유지하기 때문에  
읽기 요청은 이전 버전 snapshot을 통해 접근이 가능하다.

## 테스트 해석

### 테스트 조건
- 동시 요청: 500 threads
- 동일 좌석 대상
- CountDownLatch로 동시 시작 보장
- InnoDB 내부 lock 지표 기반 분석


## pessimistic_lock

```
total : 537ms
Innodb_row_lock_waits diff = 499
Innodb_row_lock_time diff = 3116
success = 1
fail = 499


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

total : 339ms
Innodb_row_lock_waits diff = 7
success = 8
reservationCnt = 8


락 없이 처리한 경우 여러 트랜잭션이 동시에 동일 좌석을 읽고 수정하게 되면서  
중복 예약이 실제로 발생하였다.

lock wait 지표는 거의 증가하지 않았으며,  
이는 DB 레벨에서 경쟁을 제어하지 않았기 때문이다.

속도는 가장 빠르게 보이지만,  
좌석 시스템에서는 사용하기 어려운 방식이다.

```

## atomic_update

```
total : 396~401ms
Innodb_row_lock_waits diff = 499
Innodb_row_lock_time diff = 1872
success = 1
fail = 499


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
total : 339ms
Innodb_row_lock_waits diff = 7
optimistic lock fail = 7
sold out = 492


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
