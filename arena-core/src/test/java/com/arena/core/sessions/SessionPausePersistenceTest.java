package com.arena.core.sessions;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

class SessionPausePersistenceTest {
  @Test void pausedStateAndClosedHistorySurviveSeparateDatabaseTransactions() {
    try(var factory=new Configuration().addAnnotatedClass(AcademyRunningSession.class)
        .setProperty("hibernate.connection.driver_class","org.h2.Driver")
        .setProperty("hibernate.connection.url","jdbc:h2:mem:session_pause_persistence;DB_CLOSE_DELAY=-1")
        .setProperty("hibernate.hbm2ddl.auto","create-drop")
        .buildSessionFactory()) {
      var start=LocalDateTime.of(2026,10,30,15,25,0,123456000);
      var value=new AcademyRunningSession();value.setAcademyId(1L);value.setSportId(1L);value.setFacilityId(1L);
      value.setStatus("STARTED");value.setStartedAt(start);value.setStartedBy("owner");value.setRatePerHour(new BigDecimal("220.00"));
      value.setTotalPausedSeconds(90L);value.setPausedAt(start.plusSeconds(480));
      try(var db=factory.openSession()) {var tx=db.beginTransaction();db.persist(value);tx.commit();}
      try(var db=factory.openSession()) {
        var tx=db.beginTransaction();var saved=db.find(AcademyRunningSession.class,value.getId());
        assertEquals(90L,saved.getTotalPausedSeconds());assertEquals(start.plusSeconds(480),saved.getPausedAt());
        saved.setPausedAt(null);saved.setTotalPausedSeconds(210L);saved.setEndedAt(start.plusSeconds(600));saved.setStoppedAt(start.plusSeconds(600));
        saved.setStatus("STOPPED");saved.setTotalAmount(new BigDecimal("23.00"));saved.setPaidAmount(new BigDecimal("23.00"));tx.commit();
      }
      try(var db=factory.openSession()) {
        var saved=db.find(AcademyRunningSession.class,value.getId());assertEquals(210L,saved.getTotalPausedSeconds());assertNull(saved.getPausedAt());
        assertEquals(start,saved.getStartedAt());assertEquals(start.plusSeconds(600),saved.getEndedAt());
        assertEquals(new BigDecimal("23.00"),saved.getTotalAmount());assertEquals(new BigDecimal("23.00"),saved.getPaidAmount());
      }
    }
  }
}
