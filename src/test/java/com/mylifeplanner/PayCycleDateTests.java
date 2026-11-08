package com.mylifeplanner;

import com.mylifeplanner.service.FinanceCycleService;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

class PayCycleDateTests {
    @Test
    void cycleChangesExactlyOnPaydayAndCrossesYears() {
        var before = FinanceCycleService.cycleContaining(LocalDate.of(2026, 12, 27), 28);
        assertThat(before.start()).isEqualTo("2026-11-28");
        assertThat(before.end()).isEqualTo("2026-12-27");
        var on = FinanceCycleService.cycleContaining(LocalDate.of(2026, 12, 28), 28);
        assertThat(on.start()).isEqualTo("2026-12-28");
        assertThat(on.end()).isEqualTo("2027-01-27");
        assertThat(on.nextPayday()).isEqualTo("2027-01-28");
    }

    @Test
    void shortMonthsClampWithoutDriftingAndSupportLeapYears() {
        var feb = FinanceCycleService.cycleContaining(LocalDate.of(2027, 2, 28), 31);
        assertThat(feb.start()).isEqualTo("2027-02-28");
        assertThat(feb.nextPayday()).isEqualTo("2027-03-31");
        var leap = FinanceCycleService.cycleContaining(LocalDate.of(2028, 2, 28), 31);
        assertThat(leap.start()).isEqualTo("2028-01-31");
        assertThat(leap.end()).isEqualTo("2028-02-28");
        var first = FinanceCycleService.cycleContaining(LocalDate.of(2026, 11, 30), 1);
        assertThat(first.start()).isEqualTo("2026-11-01");
        assertThat(first.end()).isEqualTo("2026-11-30");
        assertThatThrownBy(() -> FinanceCycleService.cycleContaining(LocalDate.now(), 32))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
