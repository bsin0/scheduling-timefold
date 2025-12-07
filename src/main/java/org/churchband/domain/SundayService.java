package org.churchband.domain;

import java.time.LocalDate;

public class SundayService {
    private LocalDate date;

    public SundayService(LocalDate date) {
        this.date = date;
    }

    public LocalDate getDate() {
        return date;
    }

    @Override
    public String toString() {
        return "SundayService{" +
                "date=" + date +
                '}';
    }
}