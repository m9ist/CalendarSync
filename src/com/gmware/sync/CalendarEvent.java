package com.gmware.sync;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class CalendarEvent {
    String start = null;
    String end = null;
    String subject = null;
    String location = null;
    String author = null;
    String invited = null;
    String modified = null;
    String uid = null;

    CalendarEvent() {
    }

    static final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy.MM.dd HH:mm"));

    boolean isActual() throws ParseException {
        return isInRageWithCurrentDate(1);
    }

    private boolean isInRageWithCurrentDate(final int numDays) throws ParseException {
        return start != null
            && dateFormat.get().parse(start).after(new Date(new Date().getTime() - TimeUnit.DAYS.toMillis(numDays)))
            && dateFormat.get().parse(start).before(new Date(new Date().getTime() + TimeUnit.DAYS.toMillis(ConnectMicrosoftExchange.MAX_DISTANCE_REPEATED)));
    }

    boolean needToProcess(final boolean isRepeatedEvent) throws ParseException {
        return isInRageWithCurrentDate(isRepeatedEvent ? ConnectMicrosoftExchange.MAX_DISTANCE_REPEATED : 1);
    }

    @Override
    public String toString() {
        return "CalendarEvent{" +
                "start='" + start + '\'' +
                ", end='" + end + '\'' +
                ", subject='" + subject + '\'' +
                ", location='" + location + '\'' +
                ", author='" + author + '\'' +
                ", invited='" + invited + '\'' +
                ", modified='" + modified + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final CalendarEvent that = (CalendarEvent) o;

        return start.equals(that.start)
                && end.equals(that.end)
                && subject.equals(that.subject)
                && Objects.equals(location, that.location);
    }
}
