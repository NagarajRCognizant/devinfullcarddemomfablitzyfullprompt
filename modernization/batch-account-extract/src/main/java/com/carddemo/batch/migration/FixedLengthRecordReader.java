package com.carddemo.batch.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.support.AbstractItemStreamItemReader;

/**
 * Reads fixed length record images from one or more sequential files.
 *
 * <p>The export file has {@code RECORDING MODE IS F} with {@code RECORD CONTAINS 500 CHARACTERS}
 * and holds COMP and COMP-3 fields, so it is not line oriented: a record is exactly as many bytes
 * as the layout declares and the reader hands those bytes on untouched. A short final record is a
 * truncated file, which is the non zero file status the source program abends on.
 */
public class FixedLengthRecordReader extends AbstractItemStreamItemReader<byte[]> {

    private final List<Path> files;
    private final int recordLength;
    private int current;
    private InputStream stream;

    public FixedLengthRecordReader(List<Path> files, int recordLength) {
        this.files = List.copyOf(files);
        this.recordLength = recordLength;
        setExecutionContextName(FixedLengthRecordReader.class.getSimpleName());
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        current = 0;
        stream = null;
    }

    @Override
    public void close() throws ItemStreamException {
        closeStream();
    }

    @Override
    public byte[] read() throws IOException {
        while (true) {
            if (stream == null) {
                if (current >= files.size()) {
                    return null;
                }
                Path file = files.get(current++);
                if (!Files.exists(file)) {
                    // An input file the run was not given is an empty input, not a failure: the
                    // export of a context that has no records produces no file.
                    continue;
                }
                stream = Files.newInputStream(file);
            }
            byte[] record = stream.readNBytes(recordLength);
            if (record.length == recordLength) {
                return record;
            }
            if (record.length != 0) {
                throw new IllegalStateException(
                        "Truncated record of " + record.length + " bytes in " + files.get(current - 1));
            }
            closeStream();
        }
    }

    private void closeStream() {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                throw new ItemStreamException("Cannot close export file", e);
            }
            stream = null;
        }
    }
}
