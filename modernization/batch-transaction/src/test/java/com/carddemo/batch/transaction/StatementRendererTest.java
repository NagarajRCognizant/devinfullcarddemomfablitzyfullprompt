package com.carddemo.batch.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.transaction.StatementRenderer.Statement;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.transaction.client.StatementParty;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The statement layout of CBSTM03A, column for column. */
class StatementRendererTest {

    private final StatementRenderer renderer = new StatementRenderer();

    private static StatementParty party() {
        return new StatementParty("4111111111111111", 11111111111L, 222222222L, true, true,
                new BigDecimal("1234.56"), 720, "JOHN", "Q", "PUBLIC", "410 TERRY AVE N",
                "SUITE 100", "SEATTLE", "WA", "USA", "99999");
    }

    private static TransactionEntity transaction(String id, String description, String amount) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTranId(id);
        transaction.setTranCardNum("4111111111111111");
        transaction.setTranDesc(description);
        transaction.setTranAmt(new BigDecimal(amount));
        return transaction;
    }

    @Test
    void writesEveryStatementLineAtEightyCharacters() {
        Statement statement = renderer.render(party(),
                List.of(transaction("0000000000000001", "GROCERIES", "25.00")));

        assertThat(statement.textLines()).allMatch(line -> line.length() == 80);
        assertThat(statement.htmlLines()).allMatch(line -> line.length() == 100);
    }

    @Test
    void opensAndClosesWithTheSourceBanners() {
        Statement statement = renderer.render(party(), List.of());

        assertThat(statement.textLines().get(0))
                .isEqualTo("*".repeat(31) + "START OF STATEMENT" + "*".repeat(31));
        assertThat(statement.textLines().get(statement.textLines().size() - 1))
                .isEqualTo("*".repeat(32) + "END OF STATEMENT" + "*".repeat(32));
    }

    @Test
    void buildsTheNameAndAddressBlockFromTheCustomerRecord() {
        List<String> lines = renderer.render(party(), List.of()).textLines();

        assertThat(lines.get(1)).startsWith("JOHN Q PUBLIC ");
        assertThat(lines.get(2)).startsWith("410 TERRY AVE N ");
        assertThat(lines.get(3)).startsWith("SUITE 100 ");
        assertThat(lines.get(4)).startsWith("SEATTLE WA USA 99999 ");
    }

    @Test
    void printsTheBasicDetailsInTheirEditedPictures() {
        List<String> lines = renderer.render(party(), List.of()).textLines();

        assertThat(lines.get(8)).startsWith("Account ID         :11111111111         ");
        assertThat(lines.get(9)).startsWith("Current Balance    :000001234.56 ");
        assertThat(lines.get(10)).startsWith("FICO Score         :720                 ");
    }

    @Test
    void printsOneDetailLinePerTransactionAndTheirTotal() {
        Statement statement = renderer.render(party(), List.of(
                transaction("0000000000000001", "GROCERIES", "25.00"),
                transaction("0000000000000002", "FUEL", "40.50")));

        List<String> detail = statement.textLines().stream()
                .filter(line -> line.startsWith("00000000000000")).toList();
        assertThat(detail).hasSize(2);
        assertThat(detail.get(0)).startsWith("0000000000000001 GROCERIES");
        assertThat(detail.get(0)).endsWith("$       25.00 ");
        assertThat(statement.total()).isEqualByComparingTo("65.50");
        assertThat(statement.textLines().get(statement.textLines().size() - 2))
                .isEqualTo(StatementLines.text("Total EXP:" + " ".repeat(56) + "$       65.50 "));
    }

    @Test
    void printsANegativeBalanceWithATrailingMinus() {
        StatementParty owing = new StatementParty("4111111111111111", 11111111111L, 222222222L,
                true, true, new BigDecimal("-99.99"), 700, "JOHN", "Q", "PUBLIC", "ADDR1", "ADDR2",
                "SEATTLE", "WA", "USA", "99999");

        assertThat(renderer.render(owing, List.of()).textLines().get(9))
                .startsWith("Current Balance    :000000099.99-");
    }

    @Test
    void writesTheHtmlDocumentAroundTheSameContent() {
        List<String> html = renderer.render(party(),
                List.of(transaction("0000000000000001", "GROCERIES", "25.00"))).htmlLines();

        assertThat(html.get(0)).startsWith("<!DOCTYPE html>");
        assertThat(html).anyMatch(line ->
                line.startsWith("<h3>Statement for Account Number: 11111111111"));
        assertThat(html).anyMatch(line -> line.startsWith("<p style=\"font-size:16px\">JOHN Q PUBLIC"));
        assertThat(html).anyMatch(line -> line.startsWith("<p>Current Balance    : 000001234.56"));
        assertThat(html).anyMatch(line -> line.startsWith("<p>GROCERIES"));
        assertThat(html.get(html.size() - 1)).startsWith("</html>");
    }
}
