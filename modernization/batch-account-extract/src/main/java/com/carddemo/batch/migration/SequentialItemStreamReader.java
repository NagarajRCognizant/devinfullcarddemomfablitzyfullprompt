package com.carddemo.batch.migration;

import java.util.List;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.support.AbstractItemStreamItemReader;

/**
 * Reads several sources one after another, the way CBEXPORT reads its five input files in turn.
 *
 * <p>The source program performs one {@code PERFORM UNTIL ... EOF} loop per input file and writes
 * every record it reads into the single export file, so the converted step keeps the same single
 * output by chaining the readers: each delegate is drained to its own end of file before the next
 * one starts, which preserves the order the export file records appear in.
 */
public class SequentialItemStreamReader extends AbstractItemStreamItemReader<Object> {

    private final List<ItemStreamReader<?>> delegates;
    private int current;

    public SequentialItemStreamReader(List<ItemStreamReader<?>> delegates) {
        this.delegates = List.copyOf(delegates);
        setExecutionContextName(SequentialItemStreamReader.class.getSimpleName());
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        current = 0;
        delegates.forEach(delegate -> delegate.open(executionContext));
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        delegates.forEach(delegate -> delegate.update(executionContext));
    }

    @Override
    public void close() throws ItemStreamException {
        delegates.forEach(ItemStreamReader::close);
    }

    @Override
    public Object read() throws Exception {
        while (current < delegates.size()) {
            Object item = delegates.get(current).read();
            if (item != null) {
                return item;
            }
            current++;
        }
        return null;
    }
}
