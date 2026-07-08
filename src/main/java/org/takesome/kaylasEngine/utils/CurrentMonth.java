package org.takesome.kaylasEngine.utils;

import java.time.LocalDate;
import java.time.Month;

public enum CurrentMonth {
    JANUARY(Month.JANUARY),
    FEBRUARY(Month.FEBRUARY),
    MARCH(Month.MARCH),
    APRIL(Month.APRIL),
    MAY(Month.MAY),
    JUNE(Month.JUNE),
    JULY(Month.JULY),
    AUGUST(Month.AUGUST),
    SEPTEMBER(Month.SEPTEMBER),
    OCTOBER(Month.OCTOBER),
    NOVEMBER(Month.NOVEMBER),
    DECEMBER(Month.DECEMBER);

    private final Month month;

    CurrentMonth(Month month) {
        this.month = month;
    }

    public static CurrentMonth getCurrentMonth() {
        LocalDate currentDate = LocalDate.now();
        return valueOf(currentDate.getMonth().name());
    }

    public String getMonthName() {
        return month.toString();
    }

    public int getMonthValue() {
        return month.getValue();
    }
}
