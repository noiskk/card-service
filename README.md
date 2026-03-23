# 💳 Card Service (카드 결제 처리)

## 📖 개요

카드사의 결제 승인 처리를 담당하는 서비스
<br>
VAN으로부터 결제 요청을 수신하면, FDS 이상거래 탐지를 거친 뒤 카드 타입(체크/신용)에 따라 결제를 승인한다.
<br>
모든 결제 내역은 승인 이력(Authorization)으로 기록된다.


## 🏗️ MSA 구조

card-service는 하나의 카드사 도메인을 FDS(이상거래탐지)와 Payment(결제처리) 두 개의 마이크로서비스로 분리하여 구성했다.

```
card-service/
├── card-fds-service       (port: 9090)  ← 1단계: FDS 이상거래 탐지
└── card-payment-service   (port: 9091)  ← 2단계: 결제 승인 처리
```

| 서비스 | 포트 | 역할 | DB |
|---|---|---|---|
| `card-fds-service` | `9090` | 이상거래 탐지 (중복결제 차단, 카드 상태 검증) | `card_db` (ReadOnly) |
| `card-payment-service` | `9091` | 결제 승인 처리 (체크/신용 분기, 한도 검사, 승인 이력 저장) | `card_db` (Read/Write) |

### 서비스 분리 포인트

- **FDS**는 카드 정보를 **조회만** 하므로 `ReadOnly Repository`를 사용하고, **Payment**는 사용 금액 누적 등 **쓰기 작업**이 필요하므로 `JpaRepository`를 사용
- 같은 `card_db`를 공유하지만, 각 서비스의 책임 범위에 맞게 Repository 접근 수준을 다르게 설정

### MSA 분리를 통해 얻는 이점

- **독립 배포**: FDS에 ML 모델을 도입하거나 탐지 규칙을 변경해도 `card-fds-service`만 재배포하면 되며, `card-payment-service`는 영향 없이 운영 가능
- **독립 스케일링**: FDS 검증에 트래픽이 집중될 경우 FDS 인스턴스만 수평 확장하여 대응 가능
- **기술 스택 자유**: FDS를 추후 Python 기반 ML 서비스로 전환하더라도, API 스펙만 유지하면 Payment 서비스는 수정 없이 연동 가능
- **장애 격리**: FDS 서비스에 장애가 발생해도 Payment 서비스 자체가 다운되지 않아, 장애 범위를 최소화할 수 있음


### OpenFeign 기반 서비스 간 통신

서비스 간 HTTP 통신은 `Spring Cloud OpenFeign`을 사용하여 인터페이스 선언만으로 구현했다.

```
POS (APIDOG)
    │
    ▼  POST /api/van/payments
VAN-service
    │
    ▼  OpenFeign (port: 9090)
card-fds-service ── FDS 검증 (3초 내 중복결제 차단, 카드 상태 확인)
    │
    ▼  OpenFeign (port: 9091)
card-payment-service ── 카드 타입 분기
    │
    ├─ DEBIT  ──▶ OpenFeign (port: 8080) ──▶ bank-service (계좌 출금)
    └─ CREDIT ──▶ 신용 한도 검사 후 사용액 누적 (내부 처리)
```

**card-fds-service → card-payment-service**
* [PaymentFeignClient.java](https://github.com/fisa-msa-project/card-service/blob/main/card-fds-service/src/main/java/com/card/fds/client/PaymentFeignClient.java)
```java
@FeignClient(name = "payment-service", url = "${payment.service.url}")
public interface PaymentFeignClient {

    @PostMapping("/api/card/payments/process")
    ResponseEntity<Object> processPayment(@RequestBody FdsRequestDto request);
}
```

**card-payment-service → bank-service**
* [BankClient.java](https://github.com/fisa-msa-project/card-service/blob/main/card-payment-service/src/main/java/com/card/payment/client/BankClient.java)
```java
@FeignClient(name = "bank-service", url = "${bank.service.url}")
public interface BankClient {

    @PostMapping("/api/bank/accounts/withdraw")
    WithdrawResponse withdraw(@RequestBody WithdrawRequest request);
}
```

* 의존성 추가
* [build.gradle](https://github.com/fisa-msa-project/card-service/blob/main/card-payment-service/build.gradle#L7-L32)
```groovy
ext {
    set('springCloudVersion', "2023.0.0")
}

dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-openfeign'
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}
```


---


## 🔍 1단계: FDS 이상거래 탐지 (card-fds-service)

### 🌐 API Endpoints
| Method | URI | Description |
|---|---|---|
| `POST` | `/api/fds/inspect` | VAN으로부터 결제 요청을 수신하여 FDS 검증 수행 |

### ✨ 주요 기능

**1. 3초 이내 중복 결제 차단 (Rule 1)**
* `ConcurrentHashMap` 기반 In-Memory 세션으로 카드번호별 마지막 요청 시간을 추적
* 동일 카드로 3초 이내 재요청 시 이상거래로 판단하여 즉시 차단
* [FdsInspectionService.java](https://github.com/fisa-msa-project/card-service/blob/main/card-fds-service/src/main/java/com/card/fds/service/FdsInspectionService.java#L46-L61)
```java
private void checkSessionFrequency(String cardNum) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime lastRequestTime = sessionMemory.get(cardNum);

    if (lastRequestTime != null) {
        long secondsBetween = ChronoUnit.SECONDS.between(lastRequestTime, now);

        if (secondsBetween < 3) {
            throw new FdsException("FDS_BLOCKED: 단기 다발성 거래로 차단되었습니다.");
        }
    }

    // 검증 통과 시 현재 시간으로 타임스탬프 갱신
    sessionMemory.put(cardNum, now);
}
```

**2. 카드 활성화 상태 검증 (Rule 2)**
* DB에서 카드 정보를 조회하여 `ACTIVE` 상태인지 확인
* 정지(SUSPENDED), 분실(LOST), 해지(TERMINATED) 상태의 카드는 결제 차단
* [FdsInspectionService.java](https://github.com/fisa-msa-project/card-service/blob/main/card-fds-service/src/main/java/com/card/fds/service/FdsInspectionService.java#L66-L74)
```java
private void checkCardStatus(String cardNum) {
    Card card = cardRepo.findByCardNumber(cardNum)
            .orElseThrow(() -> new FdsException("카드를 찾을 수 없습니다."));

    if (card.getCardStatus() != CardStatus.ACTIVE) {
        throw new FdsException("INVALID_STATE: 사용 불가능한 카드입니다.");
    }
}
```

**3. ReadOnly Repository를 통한 접근 제한**
* JpaRepository가 아닌 최상위 `Repository` 인터페이스만 상속하여 조회 메서드만 노출
* FDS 서비스에서 카드 데이터를 실수로 변경하는 것을 원천 차단
* [CardInfoReadOnlyRepo.java](https://github.com/fisa-msa-project/card-service/blob/main/card-fds-service/src/main/java/com/card/fds/repository/CardInfoReadOnlyRepo.java)
```java
public interface CardInfoReadOnlyRepo extends Repository<Card, Long> {

    Optional<Card> findByCardNumber(String cardNumber);
}
```


---


## 💳 2단계: 결제 승인 처리 (card-payment-service)

### 🌐 API Endpoints
| Method | URI | Description |
|---|---|---|
| `POST` | `/api/card/payments/process` | FDS 검증 통과 후 카드 결제 처리 (체크카드/신용카드 분기) |

### ✨ 주요 기능

**1. 카드 타입별 결제 분기 처리**
* FDS를 통과한 결제 요청을 수신하여 카드 타입(DEBIT/CREDIT)에 따라 처리 로직을 분기
* [PaymentProcessorService.java](https://github.com/fisa-msa-project/card-service/blob/main/card-payment-service/src/main/java/com/card/payment/service/PaymentProcessorService.java#L33-L53)
```java
@Transactional
public PaymentResponse process(PaymentRequest request) {
    String transactionId = UUID.randomUUID().toString();

    // 카드 조회
    Card card = cardInfoRepository.findByCardNumber(request.getCardNum())
            .orElseThrow(() -> new IllegalArgumentException("카드 정보를 찾을 수 없습니다."));

    // 카드 타입에 따라 분기
    CardType cardType = CardType.valueOf(request.getCardType());

    PaymentResponse response;
    if (cardType == CardType.DEBIT) {
        response = processDebit(transactionId, card, request);
    } else {
        response = processCredit(transactionId, card, request);
    }

    return response;
}
```

**2. 체크카드(DEBIT) 결제 - 은행 출금 연동**
* 1회 결제 한도(`perTransactionLimit`) 초과 여부 검사
* `Spring Cloud OpenFeign`을 통해 bank-service 출금 API 호출
* 출금 성공/실패에 따라 승인 이력 저장
* [PaymentProcessorService.java](https://github.com/fisa-msa-project/card-service/blob/main/card-payment-service/src/main/java/com/card/payment/service/PaymentProcessorService.java#L60-L92)
```java
private PaymentResponse processDebit(String transactionId, Card card, PaymentRequest request) {
    // 1. 1회 결제 한도 확인
    if (card.getPerTransactionLimit() != null && request.getAmount() > card.getPerTransactionLimit()) {
        return saveAndRespond(transactionId, card, request, "61", "1회 결제 한도 초과", false);
    }

    // 2. 은행 출금 요청
    WithdrawResponse withdrawResponse = bankClient.withdraw(
            new WithdrawRequest(request.getCardNum(), request.getAmount()));

    // 3. 출금 실패
    if (!withdrawResponse.isSuccess()) {
        return saveAndRespond(transactionId, card, request, "51", "출금 실패", false);
    }

    // 4. 성공
    return saveAndRespond(transactionId, card, request, "00", "결제 성공", true);
}
```

**3. 신용카드(CREDIT) 결제 - 한도 검사 및 사용액 누적**
* 1회 결제 한도 검사 후, 신용 잔여 한도(`creditLimit - usedAmount`) 검증
* 결제 승인 시 사용 금액을 누적하여 카드 정보 업데이트
* [PaymentProcessorService.java](https://github.com/fisa-msa-project/card-service/blob/main/card-payment-service/src/main/java/com/card/payment/service/PaymentProcessorService.java#L99-L128)
```java
private PaymentResponse processCredit(String transactionId, Card card, PaymentRequest request) {
    // 1. 1회 결제 한도 확인
    if (card.getPerTransactionLimit() != null && request.getAmount() > card.getPerTransactionLimit()) {
        return saveAndRespond(transactionId, card, request, "61", "1회 결제 한도 초과", false);
    }

    // 2. 신용 잔여 한도 확인 (creditLimit - usedAmount)
    Long creditLimit = card.getCreditLimit();
    Long usedAmount = card.getUsedAmount() != null ? card.getUsedAmount() : 0L;

    if (creditLimit == null || request.getAmount() > (creditLimit - usedAmount)) {
        return saveAndRespond(transactionId, card, request, "51", "신용 한도 초과", false);
    }

    // 3. 사용 금액 누적
    card.setUsedAmount(usedAmount + request.getAmount());
    cardInfoRepository.save(card);

    return saveAndRespond(transactionId, card, request, "00", "결제 성공", true);
}
```


---


## 🔢 금융권 표준 에러 코드

| 코드 | 설명 |
|---|---|
| `00` | 정상 승인 |
| `51` | 잔액 부족 / 신용 한도 초과 |
| `61` | 1회 결제 한도 초과 |
| `96` | 시스템 오류 |


## ⚙️ DB Schema

### cards

[Card.java](https://github.com/fisa-msa-project/card-service/blob/main/card-payment-service/src/main/java/com/card/payment/entity/Card.java)

| **필드명 (Field)** | **데이터 타입 (Type)** | **DB 컬럼명 (Column)** | **제약 조건 (Constraints)** | **설명 (Description)** |
| --- | --- | --- | --- | --- |
| **`id`** | `Long` | `id` | `PK`, `Auto Increment` | 카드 테이블 기본키 (고유 식별자) |
| **`cardNumber`** | `String` | `card_number` | `NOT NULL`, `UNIQUE` | 카드 번호 |
| **`creditLimit`** | `Long` | `credit_limit` | Nullable | 신용 한도 (신용카드 전용) |
| **`usedAmount`** | `Long` | `used_amount` | Nullable | 누적 사용 금액 (신용카드 전용) |
| **`perTransactionLimit`** | `Long` | `per_transaction_limit` | Nullable | 1회 결제 한도 (신용/체크 공통) |
| **`cardStatus`** | `CardStatus` (Enum) | `card_status` | `NOT NULL` | 카드 상태 (ACTIVE, SUSPENDED, LOST, TERMINATED) |
| **`cardType`** | `CardType` (Enum) | `card_type` | `NOT NULL` | 카드 유형 (CREDIT, DEBIT) |
| **`customerId`** | `Long` | `customer_id` | `NOT NULL` | 고객 식별 ID |
| **`createdAt`** | `LocalDateTime` | `created_at` | `NOT NULL`, `Updatable=false` | 카드 등록 일시 (Auditing 자동 생성) |
| **`expiryDate`** | `LocalDateTime` | `expiry_date` | Nullable | 카드 유효기간 |

### authorizations

[Authorization.java](https://github.com/fisa-msa-project/card-service/blob/main/card-payment-service/src/main/java/com/card/payment/entity/Authorization.java)

| **필드명 (Field)** | **데이터 타입 (Type)** | **DB 컬럼명 (Column)** | **제약 조건 (Constraints)** | **설명 (Description)** |
| --- | --- | --- | --- | --- |
| **`id`** | `Long` | `id` | `PK`, `Auto Increment` | 승인 테이블 기본키 (고유 식별자) |
| **`transactionId`** | `String` | `transaction_id` | `NOT NULL`, `UNIQUE` | 거래 고유 번호 (UUID) |
| **`card`** | `Card` | `card_id` | `NOT NULL`, `FK` | 카드 테이블 참조 (ManyToOne) |
| **`amount`** | `Long` | `amount` | `NOT NULL` | 거래 금액 |
| **`merchantId`** | `String` | `merchant_id` | `NOT NULL` | 가맹점 ID |
| **`responseCode`** | `String` | `response_code` | `NOT NULL`, `Length=2` | 응답 코드 (00, 51, 61, 96 등) |
| **`status`** | `AuthorizationStatus` (Enum) | `status` | `NOT NULL` | 승인 상태 (APPROVED, REJECTED, CONFIRMED) |
| **`createdAt`** | `LocalDateTime` | `created_at` | `NOT NULL`, `Updatable=false` | 생성 일시 (Auditing 자동 생성) |
| **`authorizationDate`** | `LocalDateTime` | `authorization_date` | `NOT NULL`, `Updatable=false` | 승인 일시 (Auditing 자동 생성) |


---


## 🔧 추가 구현/확장 가능 사항

**1. 신용카드 동시성 제어 (Pessimistic Lock)**

현재 신용카드 `usedAmount` 누적 시 동시성 제어가 적용되어 있지 않아, 동일 카드로 동시 요청이 들어올 경우 누적 금액이 정확하지 않을 수 있다.
bank-service와 동일하게 `@Lock(LockModeType.PESSIMISTIC_WRITE)`를 적용하여 race condition을 방지할 수 있다.

**2. 신용카드 월말 정산 시스템**

현재는 결제 승인(Authorization) 단계까지만 구현되어 있으며, 실제 계좌 출금은 이루어지지 않는다.
`@Scheduled` 기반 정산 배치를 추가하여 매월 말 `usedAmount`만큼 청구 계좌에서 출금하고, `usedAmount`를 초기화하는 방식으로 확장할 수 있다.
