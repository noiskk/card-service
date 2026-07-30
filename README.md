# 💳 Card Service (카드사)

> 전체 시스템 개요·아키텍처·실행 방법 → **[card-payment-system](https://github.com/noiskk/card-payment-system)**
> 관련 저장소: [pos-client](https://github.com/noiskk/pos-client) · [van-service](https://github.com/noiskk/van-service) · [bank-service](https://github.com/noiskk/bank-service)

## 📖 개요

카드사의 승인 처리를 담당한다. VAN으로부터 승인 요청을 받으면 이상거래 판정 → 카드 확인 → (체크카드) 은행 출금 → 승인 원장 기록 순으로 처리하고, 중간에 실패하면 되돌린다.

이 저장소는 **카드사 한 조직 내부**를 5개 모듈로 나눈 MSA다. POS·VAN·은행은 다른 회사이므로 기업 간 연동으로 취급한다.

---

## 🏗️ 모듈 구성

```
card-service/
├── eureka-server           (8761)  ← 내부 서비스 레지스트리
├── card-gateway            (9000)  ← 카드사 단일 진입점
├── card-payment-service    (9091)  ← 승인 오케스트레이션·멱등성·보상·대사 배치
├── card-fds-service        (9090)  ← 이상거래 판정 (leaf)
└── ledger-service          (9094)  ← INSERT-only 승인 원장·정산 배치
```

| 모듈 | 포트 | 역할 | DB |
|---|---|---|---|
| `eureka-server` | 8761 | 내부 서비스 등록/조회 | - |
| `card-gateway` | 9000 | 라우팅 (VAN이 아는 유일한 주소) | - |
| `card-payment-service` | 9091 | 승인 조율, 멱등성, 보상, 망취소 대사 | `card_db` |
| `card-fds-service` | 9090 | 사기 판정만 반환 | `card_db` (ReadOnly) |
| `ledger-service` | 9094 | 승인 원장 기록·조회, 가맹점 정산 | `ledger_db` |

## 🔀 승인 흐름

```
VAN
 │  HTTP
 ▼
card-gateway :9000                       ← 라우팅 (lb://card-payment-service)
 │
 ▼
card-payment-service :9091               ← 오케스트레이터
 ├─(1)─▶ card-fds-service :9090           사기 판정 (차단이면 여기서 거절 원장만 기록)
 ├─(2)   카드 확인 · 한도 검사
 ├─(3)─▶ bank-service :8080               체크카드만 출금 (외부 회사, 고정 주소)
 └─(4)─▶ ledger-service :9094             승인/거절 원장 기록
              │
              └ 실패 시 → 은행 취소로 보상 (출금이 확정된 경우)
```

**서비스 위치를 어떻게 찾는가** — 내부는 이름으로, 외부는 고정 주소로.

```java
// 카드사 내부 — 주소를 모른다. 레지스트리가 알려준다.
@FeignClient(name = "card-fds-service")
@FeignClient(name = "ledger-service")

// 은행 — 다른 회사이므로 디스커버리 대상이 아니다.
@FeignClient(name = "bank-service", url = "${bank.service.url}")
```

---

## 🔑 멱등성 (card-payment-service)

네트워크가 끊기면 단말은 성공 여부를 모르고 재시도한다. 이를 새 결제로 처리하면 이중결제가 된다.

**예약-후-실행** — 출금 전에 멱등키를 UNIQUE 컬럼에 먼저 넣어 선점한다. 조회 후 처리하는 방식은 동시 요청에서 둘 다 통과할 수 있어, 동시성 판단을 DB에 맡겼다.

```java
public PaymentResponse process(PaymentRequest request) {
    String key = request.getIdempotencyKey();

    // 1. 이미 처리된 키면 저장된 결과 반환 (출금하지 않는다)
    Optional<IdempotencyRecord> existing = idempotencyRepository.findByIdempotencyKey(key);
    if (existing.isPresent()) return toResponse(existing.get());

    // 2. PENDING으로 선점 — 동시 재시도는 UNIQUE 위반으로 걸러진다
    try {
        idempotencyService.reserve(key);
    } catch (DataIntegrityViolationException e) {
        return toResponse(idempotencyRepository.findByIdempotencyKey(key).orElseThrow());
    }

    // 3. 실제 결제 — 여기 도달하는 건 딱 한 번
    PaymentResponse result;
    try {
        result = paymentExecutor.execute(request);
    } catch (RuntimeException e) {
        idempotencyService.remove(key);   // 실패 시 예약 해제
        throw e;
    }

    // 4. 완료 기록
    idempotencyService.complete(key, result);
    return result;
}
```

`reserve`/`complete`는 `REQUIRES_NEW`다. 출금 전에 확실히 커밋해 키를 선점해야 하고, 바깥 결제가 롤백돼도 예약 흔적은 남아야 한다.
`execute`를 별도 빈(`PaymentExecutor`)으로 분리한 이유는 self-invocation으로 `@Transactional`이 무시되는 것을 피하기 위해서다.

**멱등키 원점** — 단말이 거래마다 부여하는 STAN(ISO 8583 field 11). 6자리라 단말 내에서만 유일하므로 VAN에서 가맹점ID와 조합한다.

---

## ♻️ 보상 트랜잭션 / 망취소 (card-payment-service)

정합성이 깨질 수 있는 구간은 두 곳이고, 처리가 다르다.

| 상황 | 우리가 아는 것 | 조치 |
|---|---|---|
| 출금 성공 후 **원장 기록 실패** | 출금이 확정됐다 | 은행 취소로 즉시 보상 |
| **은행 호출 타임아웃** | 출금 여부를 모른다 | 취소하지 않고 대사 대상으로만 기록 |

출금이 안 된 상태에서 취소를 보내면 계좌에 없던 돈이 생긴다. 그래서 **"모른다"를 "실패했다"로 바꿔 기록하지 않는다.**

```java
try {
    withdrawResponse = bankClient.withdraw(...);
} catch (Exception e) {
    // 출금 여부 불명 → 취소를 보내지 않고 대사 대상으로만 남긴다
    compensationService.recordUncertainWithdrawal(transactionId, request);
    throw new DownstreamCallFailedException("bank-service", transactionId, request.getAmount(), e);
}
```

`CompensationService`의 모든 메서드는 `REQUIRES_NEW`다. 호출하는 쪽이 예외를 던지며 롤백되는 흐름이라, 같은 트랜잭션에 묶이면 **실패했을 때 남겨야 하는 기록이 실패 때문에 사라진다.**

보상 순서도 중요하다 — **불확실 거래를 먼저 커밋한 뒤 취소를 시도**한다. 취소 도중 프로세스가 죽어도 배치가 이어받을 수 있다.

---

## 📒 승인 원장 (ledger-service)

```java
@Entity
@Table(name = "authorizations")
@Getter                                              // setter 없음 — 생성 후 수정 불가
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Authorization {
    @Column(name = "transaction_id", unique = true, nullable = false)
    private String transactionId;

    // 다른 서비스(카드)의 엔티티를 FK로 참조하지 않는다. 카드번호를 값으로 저장.
    @Column(name = "card_number", nullable = false)
    private String cardNumber;
    ...
}
```

- **INSERT-only** — 취소·매출확정도 UPDATE가 아니라 새 원장 레코드로 기록한다
- **FK 없음** — 카드 정보는 승인 서비스 소유다. 조인으로 묶이면 서비스를 분리할 수 없다
- **기록 멱등성** — 같은 `transactionId`로 두 번 요청이 와도 기존 원장을 반환하고 새로 INSERT하지 않는다

| Method | URI | 설명 |
|---|---|---|
| `POST` | `/ledger/records` | 승인/거절 결과 기록 |
| `GET` | `/ledger/records/{transactionId}` | 원장 조회 (대사·감사용) |
| `POST` | `/ledger/settlements/run?targetDate=` | 정산 배치 실행 |
| `GET` | `/ledger/settlements?targetDate=` | 정산 결과 조회 |

---

## ⏱ 배치 (Spring Batch)

배치는 데이터를 소유한 모듈에 둔다.

### 망취소 대사 — card-payment-service

불확실 거래를 은행에 재조회해 정리한다. 은행 취소 API가 `originalFound`를 반환하므로 **한 번의 호출로 "출금 자체가 없었음"과 "출금됐고 취소함"을 구분**한다.

```
originalFound = false  →  출금 미발생, 취소할 것 없음 → 종결
success = true         →  출금 확인, 취소 완료      → 종결
호출 실패              →  3회까지 재시도 후 수동 확인 대상(FAILED)
```

Reader는 **id 커서(keyset paging)**를 쓴다. 처리한 건이 PENDING에서 빠지며 결과 집합이 줄어드는데, 페이지 번호 방식은 남은 행이 앞으로 당겨져 일부를 건너뛴다. 또 Reader가 커서를 상태로 들고 있으므로 `@StepScope`로 실행마다 새로 만든다.

| Method | URI | 설명 |
|---|---|---|
| `POST` | `/api/card/reconciliation/run` | 대사 배치 즉시 실행 (스케줄러는 5분 주기) |
| `GET` | `/api/card/reconciliation/pending` | 미해결 불확실 거래 |
| `GET` | `/api/card/reconciliation/failed` | 수동 확인 필요 건 |

### 가맹점 정산 — ledger-service

승인 원장을 가맹점별로 집계해 수수료를 차감한다. **적용한 수수료율을 결과와 함께 저장**해서, 요율이 바뀐 뒤에도 과거 정산의 계산 근거를 재현할 수 있게 했다.

정산은 중복 실행이 곧 이중 지급이므로 `(가맹점, 정산일)` UNIQUE 제약 + 처리 단계 중복 검사로 이중 방어한다.

---

## 🛡 이상거래 탐지 (card-fds-service)

판정만 반환하는 leaf다. 승인 흐름을 이어가지 않는다 — 사기 판정과 승인 오케스트레이션의 책임을 분리했다.

| 규칙 | 내용 | 코드 |
|---|---|---|
| VELOCITY | 동일 카드 3초 내 재요청 | `94` |
| CARD_STATUS | 미등록 / 정지 카드 | `14` |
| TYPE_RESOLVE | 카드 종류를 DB 기준으로 보정 | - |

차단도 **HTTP 200 + 응답코드**로 응답한다. 비2xx로 주면 호출하는 쪽의 Feign이 예외로 처리해 "카드사 장애"로 오인한다.

| Method | URI | 설명 |
|---|---|---|
| `POST` | `/api/fds/inspect` | 사기 판정 (통과 시 실제 카드 종류 반환) |

---

## 🔢 응답 코드

| 코드 | 의미 |
|---|---|
| `00` | 승인 |
| `14` | 카드 상태 문제 (미등록·정지) |
| `51` | 잔액 부족 / 신용 한도 초과 |
| `61` | 1회 결제 한도 초과 |
| `94` | 중복 거래 의심 |
| `96` | 시스템 오류 |

**비즈니스 거절은 200 + 응답코드, 시스템 실패만 5xx.** 이 구분이 없으면 정상 거절과 장애를 구별할 수 없고, 보상이 필요한지도 판단할 수 없다.

---

## ▶️ 실행

MySQL 없이 H2로 바로 띄울 수 있다. **레지스트리를 먼저** 띄운다.

```bash
cd eureka-server           && sh gradlew bootRun
cd ledger-service          && sh gradlew bootRun --args='--spring.profiles.active=local'
cd card-fds-service        && sh gradlew bootRun --args='--spring.profiles.active=local'
cd card-payment-service    && sh gradlew bootRun --args='--spring.profiles.active=local'
cd card-gateway            && sh gradlew bootRun
```

MySQL로 실행하려면 `--args` 없이 `export DB_PASSWORD=<비밀번호>` 후 기동한다.

### 관제 화면

| 주소 | 내용 |
|---|---|
| http://localhost:9091 | 승인 관제 — 불확실 거래 + 대사 배치 실행, 멱등키 현황, 카드 마스터 |
| http://localhost:9094 | 승인 원장 — 원장, 정산 배치 실행, 가맹점별 정산 결과 |
| http://localhost:9090 | 이상거래 탐지 — 판정 이력, 차단율 |
| http://localhost:8761 | 서비스 레지스트리 |

### 테스트

```bash
sh gradlew test    # 각 모듈에서
```

정합성 로직 중심으로 작성했다 — 멱등성, 보상, 대사 배치, 정산 "정확히 한 번", 원장 기록 멱등성. DTO 변환이나 단순 relay는 테스트하지 않는다.

---

## ⚠️ 알려진 한계

- **카드 마스터가 공유 DB** — `card_db.cards`를 payment와 fds가 공유한다. 실제로 같은 컬럼을 서로 다른 타입으로 매핑해 기동이 실패하는 문제가 있었다. 조회 API로 전환하는 것이 다음 과제다
- **서비스 간 인증 없음** — 현재 전부 `permitAll`
- **카드번호 평문 처리** — 실제 서비스라면 PCI-DSS상 마스킹·토큰화가 필요하다
- **신용카드 `usedAmount` 동시성 제어 없음** — 동일 카드 동시 요청 시 누적 금액이 어긋날 수 있다. bank-service처럼 비관적 락이 필요하다
