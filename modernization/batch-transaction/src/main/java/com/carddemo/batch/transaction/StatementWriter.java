package com.carddemo.batch.transaction;

import com.carddemo.batch.transaction.StatementRenderer.Statement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/**
 * The two WRITE streams of CBSTM03A: the 80 column STMTFILE and the 100 column HTMLFILE.
 *
 * <p>A statement is written to both files, so a rendered statement is split here rather than in
 * the renderer, and both writes happen inside the same chunk transaction.
 */
public class StatementWriter implements ItemWriter<Statement> {

    private final ItemWriter<String> textLines;
    private final ItemWriter<String> htmlLines;

    public StatementWriter(ItemWriter<String> textLines, ItemWriter<String> htmlLines) {
        this.textLines = textLines;
        this.htmlLines = htmlLines;
    }

    @Override
    public void write(Chunk<? extends Statement> chunk) throws Exception {
        List<String> text = new ArrayList<>();
        List<String> html = new ArrayList<>();
        for (Statement statement : chunk) {
            text.addAll(statement.textLines());
            html.addAll(statement.htmlLines());
        }
        textLines.write(new Chunk<>(text));
        htmlLines.write(new Chunk<>(html));
    }
}
