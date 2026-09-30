package com.carddemo.batch.transaction;

import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.transaction.client.StatementParty;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 5000-CREATE-STATEMENT, 5100-WRITE-HTML-HEADER, 5200-WRITE-HTML-NMADBS, 6000-WRITE-TRANS and the
 * closing writes of 4000-TRNXFILE-GET, as one rendering of one account.
 *
 * <p>The source interleaves its writes to STMTFILE and HTMLFILE; the two sequences are independent
 * of one another, so they are produced here as two lists in the order the program wrote them.
 *
 * <p>The transaction table of the source held ten transactions for each of fifty one cards, which
 * is a working storage limit rather than a business rule: a card with more than ten transactions
 * silently lost the rest. That limit is not reproduced, and the difference is recorded in the
 * parity register.
 */
@Component
public class StatementRenderer {

    /** The statement of one account: an 80 column listing and the HTML of the same content. */
    public record Statement(String cardNumber, List<String> textLines, List<String> htmlLines,
                            BigDecimal total) {
    }

    public Statement render(StatementParty party, List<TransactionEntity> transactions) {
        List<String> text = new ArrayList<>();
        List<String> html = new ArrayList<>();

        text.add(StatementLines.text(StatementLines.START_OF_STATEMENT));
        writeHtmlHeader(html, party);

        String nameLine = StatementLines.nameLine(
                party.firstName(), party.middleName(), party.lastName());
        String addressLine1 = StatementLines.addressLine(party.addressLine1());
        String addressLine2 = StatementLines.addressLine(party.addressLine2());
        String addressLine3 = StatementLines.cityStateZipLine(
                party.addressLine3(), party.stateCode(), party.countryCode(), party.zip());

        writeHtmlNameAddressBasics(html, party, nameLine, addressLine1, addressLine2, addressLine3);

        text.add(nameLine);
        text.add(addressLine1);
        text.add(addressLine2);
        text.add(addressLine3);
        text.add(StatementLines.RULE);
        text.add(StatementLines.BASIC_DETAILS);
        text.add(StatementLines.RULE);
        text.add(StatementLines.accountIdLine(party.accountId()));
        text.add(StatementLines.currentBalanceLine(party.currentBalance()));
        text.add(StatementLines.ficoScoreLine(party.ficoScore()));
        text.add(StatementLines.RULE);
        text.add(StatementLines.TRANSACTION_SUMMARY);
        text.add(StatementLines.RULE);
        text.add(StatementLines.text(StatementLines.COLUMN_HEADINGS));
        text.add(StatementLines.RULE);

        BigDecimal total = BigDecimal.ZERO;
        for (TransactionEntity transaction : transactions) {
            writeTransaction(text, html, transaction);
            total = total.add(transaction.getTranAmt() == null
                    ? BigDecimal.ZERO : transaction.getTranAmt());
        }

        text.add(StatementLines.RULE);
        text.add(StatementLines.totalLine(total));
        text.add(StatementLines.text(StatementLines.END_OF_STATEMENT));

        html.add(StatementLines.html(StatementHtmlLines.TRS));
        html.add(StatementLines.html(StatementHtmlLines.L10));
        html.add(StatementLines.html(StatementHtmlLines.L75));
        html.add(StatementLines.html(StatementHtmlLines.TDE));
        html.add(StatementLines.html(StatementHtmlLines.TRE));
        html.add(StatementLines.html(StatementHtmlLines.L78));
        html.add(StatementLines.html(StatementHtmlLines.L79));
        html.add(StatementLines.html(StatementHtmlLines.L80));

        return new Statement(party.cardNumber(), List.copyOf(text), List.copyOf(html), total);
    }

    /** 5100-WRITE-HTML-HEADER. */
    private void writeHtmlHeader(List<String> html, StatementParty party) {
        addHtml(html, StatementHtmlLines.L01, StatementHtmlLines.L02, StatementHtmlLines.L03,
                StatementHtmlLines.L04, StatementHtmlLines.L05, StatementHtmlLines.L06,
                StatementHtmlLines.L07, StatementHtmlLines.L08, StatementHtmlLines.TRS,
                StatementHtmlLines.L10);
        addHtml(html, StatementHtmlLines.accountHeading(party.accountId()),
                StatementHtmlLines.TDE, StatementHtmlLines.TRE, StatementHtmlLines.TRS,
                StatementHtmlLines.L15, StatementHtmlLines.L16, StatementHtmlLines.L17,
                StatementHtmlLines.L18, StatementHtmlLines.TDE, StatementHtmlLines.TRE,
                StatementHtmlLines.TRS, StatementHtmlLines.L22_35);
    }

    /** 5200-WRITE-HTML-NMADBS. */
    private void writeHtmlNameAddressBasics(List<String> html, StatementParty party, String nameLine,
            String addressLine1, String addressLine2, String addressLine3) {
        addHtml(html, StatementHtmlLines.nameParagraph(nameLine),
                StatementHtmlLines.addressParagraph(addressLine1),
                StatementHtmlLines.addressParagraph(addressLine2),
                StatementHtmlLines.addressParagraph(addressLine3),
                StatementHtmlLines.TDE, StatementHtmlLines.TRE, StatementHtmlLines.TRS,
                StatementHtmlLines.L30_42, StatementHtmlLines.L31, StatementHtmlLines.TDE,
                StatementHtmlLines.TRE, StatementHtmlLines.TRS, StatementHtmlLines.L22_35);
        addHtml(html, StatementHtmlLines.accountIdParagraph(party.accountId()),
                StatementHtmlLines.currentBalanceParagraph(party.currentBalance()),
                StatementHtmlLines.ficoScoreParagraph(party.ficoScore()),
                StatementHtmlLines.TDE, StatementHtmlLines.TRE, StatementHtmlLines.TRS,
                StatementHtmlLines.L30_42, StatementHtmlLines.L43, StatementHtmlLines.TDE,
                StatementHtmlLines.TRE, StatementHtmlLines.TRS, StatementHtmlLines.L47,
                StatementHtmlLines.L48, StatementHtmlLines.TDE, StatementHtmlLines.L50,
                StatementHtmlLines.L51, StatementHtmlLines.TDE, StatementHtmlLines.L53,
                StatementHtmlLines.L54, StatementHtmlLines.TDE, StatementHtmlLines.TRE);
    }

    /** 6000-WRITE-TRANS. */
    private void writeTransaction(List<String> text, List<String> html, TransactionEntity transaction) {
        String tranId = StatementLines.fixed(transaction.getTranId(), 16);
        String description = StatementLines.fixed(transaction.getTranDesc(), 49);
        String amount = StatementLines.suppressedAmount(transaction.getTranAmt());

        text.add(StatementLines.transactionLine(tranId, description, transaction.getTranAmt()));

        addHtml(html, StatementHtmlLines.TRS, StatementHtmlLines.L58,
                StatementHtmlLines.paragraph(tranId), StatementHtmlLines.TDE,
                StatementHtmlLines.L61, StatementHtmlLines.paragraph(description),
                StatementHtmlLines.TDE, StatementHtmlLines.L64,
                StatementHtmlLines.paragraph(amount), StatementHtmlLines.TDE,
                StatementHtmlLines.TRE);
    }

    private void addHtml(List<String> html, String... lines) {
        for (String line : lines) {
            html.add(StatementLines.html(line));
        }
    }
}
