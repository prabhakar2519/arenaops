package com.arena.core.sessions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static com.arena.core.sessions.RunningSessionContracts.SessionView;

/** Receives already-authorized history projections; never loads raw financial entities. */
final class SessionHistoryWorkbook {
  private SessionHistoryWorkbook() {}
  static byte[] write(List<SessionView> rows,boolean financial) {
    try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
      var sheet=workbook.createSheet("Session History");
      String[] headings=financial?new String[]{"Facility","Game type","Player","Start time","End time","Duration","Paused duration","Original Bill","Approved Discount","Final Payable","Collected","Pending","Financial Status","Discount Approval","Session status"}:new String[]{"Facility","Game type","Player","Start time","End time","Duration","Paused duration","Original Bill","Bill Paid","Session status"};
      var header=workbook.createCellStyle();var font=workbook.createFont();font.setBold(true);header.setFont(font);
      var dates=workbook.createCellStyle();dates.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
      var duration=workbook.createCellStyle();duration.setDataFormat(workbook.createDataFormat().getFormat("[h]:mm:ss"));
      var money=workbook.createCellStyle();money.setDataFormat(workbook.createDataFormat().getFormat("\"₹\"#,##0.00"));
      var first=sheet.createRow(0);for(int c=0;c<headings.length;c++){var cell=first.createCell(c);cell.setCellValue(headings[c]);cell.setCellStyle(header);sheet.setColumnWidth(c,(c==3||c==4?23:22)*256);}
      for(var s:rows){var r=sheet.createRow(sheet.getLastRowNum()+1);int c=0;
        text(r,c++,s.facilityName());text(r,c++,s.facilityType().replace('_',' '));text(r,c++,s.playerName()==null?"Walk-in customer":s.playerName());
        date(r,c++,s.startedAt(),dates);date(r,c++,s.endedAt()==null?s.stoppedAt():s.endedAt(),dates);
        number(r,c++,BigDecimal.valueOf(s.durationSeconds()==null?0:s.durationSeconds()).divide(BigDecimal.valueOf(86400),12,java.math.RoundingMode.HALF_UP),duration);
        number(r,c++,BigDecimal.valueOf(s.totalPausedSeconds()==null?0:s.totalPausedSeconds()).divide(BigDecimal.valueOf(86400),12,java.math.RoundingMode.HALF_UP),duration);
        number(r,c++,s.originalAmount(),money);
        if(financial){number(r,c++,s.approvedDiscount(),money);number(r,c++,s.totalAmount(),money);number(r,c++,s.paidAmount(),money);number(r,c++,s.outstandingAmount(),money);text(r,c++,s.financialStatus());text(r,c++,s.discountStatus());}
        else text(r,c++,billPaid(s.paymentStatus()));
        text(r,c,s.status());
      }
      sheet.createFreezePane(0,1);sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,sheet.getLastRowNum(),0,headings.length-1));workbook.write(out);return out.toByteArray();
    }catch(IOException e){throw new IllegalStateException("Session workbook could not be generated",e);}
  }
  static String billPaid(String status){return "PAID".equals(status)?"Paid":("UNPAID".equals(status)||"PARTIALLY_PAID".equals(status))?"Unpaid":"Not Finalized";}
  private static void text(Row r,int c,String value){r.createCell(c,CellType.STRING).setCellValue(value==null?"":value);}
  private static void number(Row r,int c,BigDecimal value,CellStyle style){var cell=r.createCell(c);if(value!=null)cell.setCellValue(value.doubleValue());cell.setCellStyle(style);}
  private static void date(Row r,int c,LocalDateTime value,CellStyle style){var cell=r.createCell(c);if(value!=null)cell.setCellValue(value);cell.setCellStyle(style);}
}
