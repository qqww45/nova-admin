package com.example.admin.infrastructure.security;

import com.example.admin.domain.model.auth.ChallengeTicket;
import com.example.admin.domain.model.auth.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 票据存储单元测试：重点验证"一次一销"（抗重放）与上下文绑定。
 */
class InMemoryChallengeStoreTest {

    private InMemoryChallengeStore store;

    private static final String DEVICE = "device-1";
    private static final String IP_SEGMENT = "192.168.1.";

    @BeforeEach
    void setUp() {
        store = new InMemoryChallengeStore();
    }

    private static ChallengeTicket ticket(String id, String username, String deviceId,
                                          String ipSegment, int minutesValid) {
        return new ChallengeTicket(id, username, deviceId, ipSegment, 100, RiskLevel.LIGHT,
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(minutesValid));
    }

    @Test
    @DisplayName("消费成功：返回票据")
    void consume_valid_returnsTicket() {
        store.save(ticket("t1", "admin", DEVICE, IP_SEGMENT, 3));

        ChallengeTicket consumed = store.consume("t1", "admin", DEVICE, IP_SEGMENT);

        assertThat(consumed).isNotNull();
        assertThat(consumed.getTicketId()).isEqualTo("t1");
    }

    @Test
    @DisplayName("抗重放：同一票据第二次消费返回 null（用后即焚）")
    void consume_twice_secondReturnsNull() {
        store.save(ticket("t1", "admin", DEVICE, IP_SEGMENT, 3));

        assertThat(store.consume("t1", "admin", DEVICE, IP_SEGMENT)).isNotNull();
        assertThat(store.consume("t1", "admin", DEVICE, IP_SEGMENT)).isNull();
    }

    @Test
    @DisplayName("用户名不匹配：拒绝消费（防止拿别人的票据）")
    void consume_wrongUsername_returnsNull() {
        store.save(ticket("t1", "admin", DEVICE, IP_SEGMENT, 3));

        assertThat(store.consume("t1", "other", DEVICE, IP_SEGMENT)).isNull();
    }

    @Test
    @DisplayName("设备不匹配：拒绝消费")
    void consume_wrongDevice_returnsNull() {
        store.save(ticket("t1", "admin", DEVICE, IP_SEGMENT, 3));

        assertThat(store.consume("t1", "admin", "device-2", IP_SEGMENT)).isNull();
    }

    @Test
    @DisplayName("网段不匹配：拒绝消费")
    void consume_wrongIpSegment_returnsNull() {
        store.save(ticket("t1", "admin", DEVICE, IP_SEGMENT, 3));

        assertThat(store.consume("t1", "admin", DEVICE, "10.0.0.")).isNull();
    }

    @Test
    @DisplayName("票据不存在：返回 null")
    void consume_missing_returnsNull() {
        assertThat(store.consume("ghost", "admin", DEVICE, IP_SEGMENT)).isNull();
    }

    @Test
    @DisplayName("票据已过期：返回 null")
    void consume_expired_returnsNull() {
        store.save(ticket("old", "admin", DEVICE, IP_SEGMENT, -1));

        assertThat(store.consume("old", "admin", DEVICE, IP_SEGMENT)).isNull();
    }

    @Test
    @DisplayName("get 只读不消费：可反复读取（用于下发一次性码等场景）")
    void get_doesNotConsume() {
        store.save(ticket("t1", "admin", DEVICE, IP_SEGMENT, 3));

        assertThat(store.get("t1")).isNotNull();
        assertThat(store.get("t1")).isNotNull();
        // 仍然可以被消费一次
        assertThat(store.consume("t1", "admin", DEVICE, IP_SEGMENT)).isNotNull();
    }

    @Test
    @DisplayName("remove：作废后既读不到也消费不了")
    void remove_makesTicketUnusable() {
        store.save(ticket("t1", "admin", DEVICE, IP_SEGMENT, 3));
        store.remove("t1");

        assertThat(store.get("t1")).isNull();
        assertThat(store.consume("t1", "admin", DEVICE, IP_SEGMENT)).isNull();
    }
}
