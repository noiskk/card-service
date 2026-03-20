-- Card Authorization Service 테스트 데이터

-- 카드 데이터 삽입
-- PIN은 BCrypt로 암호화된 "1234"
-- Luhn 알고리즘을 통과하는 유효한 테스트 카드 번호 사용
-- BCrypt 해시: $2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli (strength=10, "1234")
INSERT INTO cards (id, card_number, card_type, card_status, expiry_date, credit_limit, used_amount, pin, customer_id, created_at, version) VALUES
(1, '4111111111111111', 'DEBIT', 'ACTIVE', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-001', NOW(), 0),
(2, '5555555555554444', 'DEBIT', 'ACTIVE', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-002', NOW(), 0),
(3, '378282246310005', 'DEBIT', 'ACTIVE', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-003', NOW(), 0),
(4, '6011111111111117', 'CREDIT', 'ACTIVE', '2027-12-31', 5000000.00, 0.00, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-004', NOW(), 0),
(5, '3530111333300000', 'CREDIT', 'ACTIVE', '2027-12-31', 3000000.00, 2500000.00, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-005', NOW(), 0),
(6, '5105105105105100', 'DEBIT', 'SUSPENDED', '2027-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-006', NOW(), 0),
(7, '4012888888881881', 'DEBIT', 'ACTIVE', '2024-12-31', NULL, NULL, '$2a$10$vg3Gcw.lMS4okoXKpIML.eaRt5Sni8zObcwADbcm1SZS0ymxJXkli', 'CUST-007', NOW(), 0);
