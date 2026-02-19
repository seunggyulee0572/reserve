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
