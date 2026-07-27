package com.card.payment.batch;

import com.card.payment.entity.ReconciliationStatus;
import com.card.payment.entity.UncertainTransaction;
import com.card.payment.repository.UncertainTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemReader;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 대사 대상(PENDING) 거래를 id 커서로 읽는 Reader.
 *
 * 페이지 번호 기반(JpaPagingItemReader) 대신 keyset paging을 쓰는 이유:
 * 배치가 처리한 건은 PENDING에서 빠지므로 결과 집합이 계속 줄어든다.
 * 이때 page=1, 2로 넘기면 남은 행이 앞으로 당겨져 일부가 조회에서 누락된다.
 * 마지막으로 읽은 id 이후만 가져오면 결과 집합이 변해도 건너뛰지 않는다.
 */
@RequiredArgsConstructor
public class PendingUncertainReader implements ItemReader<UncertainTransaction> {

    private final UncertainTransactionRepository repository;
    private final int pageSize;

    private final Deque<UncertainTransaction> buffer = new ArrayDeque<>();
    private Long lastId = 0L;

    @Override
    public UncertainTransaction read() {
        if (buffer.isEmpty()) {
            List<UncertainTransaction> page = repository.findByStatusAndIdGreaterThanOrderByIdAsc(
                    ReconciliationStatus.PENDING, lastId, PageRequest.of(0, pageSize));
            if (page.isEmpty()) {
                return null; // null을 반환해야 Step이 끝난다
            }
            buffer.addAll(page);
            lastId = page.get(page.size() - 1).getId();
        }
        return buffer.poll();
    }
}
