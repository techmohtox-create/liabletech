package com.liabletech.exportdocs.check;

import java.time.LocalDate;

import com.liabletech.exportdocs.reading.InsuranceReading;
import com.liabletech.exportdocs.reading.InvoiceReading;
import com.liabletech.exportdocs.reading.LcReading;
import com.liabletech.exportdocs.reading.OriginReading;
import com.liabletech.exportdocs.reading.PackingListReading;
import com.liabletech.exportdocs.reading.TransportDocReading;

/**
 * The documents of one presentation, any of which may be missing.
 *
 * @param presentationDate the day the documents will reach the bank
 */
public record CheckInput(LcReading lc, InvoiceReading invoice, PackingListReading packing,
		TransportDocReading transport, OriginReading origin, InsuranceReading insurance, LocalDate presentationDate,
		LocalDate today) {
}
