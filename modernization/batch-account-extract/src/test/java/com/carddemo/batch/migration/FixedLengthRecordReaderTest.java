package com.carddemo.batch.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;

/** 2100-READ-EXPORT-RECORD: the sequential read of the fixed length export files. */
class FixedLengthRecordReaderTest {

    @TempDir
    private Path directory;

    /** The export files are read one after the other, in the order they were configured. */
    @Test
    void itReadsEveryRecordOfEveryFileInOrder() throws IOException {
        Path first = write("first", "AAAABBBB");
        Path second = write("second", "CCCC");

        FixedLengthRecordReader reader = open(List.of(first, second));

        assertThat(records(reader)).containsExactly("AAAA", "BBBB", "CCCC");
        reader.close();
    }

    /** File status 10 on the last file ends the run normally. */
    @Test
    void theEndOfTheLastFileEndsTheRead() throws IOException {
        Path file = write("only", "AAAA");

        FixedLengthRecordReader reader = open(List.of(file));

        assertThat(reader.read()).isNotNull();
        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();
        reader.close();
    }

    /** A step of the export that produced no file leaves nothing to import. */
    @Test
    void aMissingFileIsSkipped() throws IOException {
        Path file = write("present", "AAAA");

        FixedLengthRecordReader reader = open(List.of(directory.resolve("absent"), file));

        assertThat(records(reader)).containsExactly("AAAA");
        reader.close();
    }

    /** A record shorter than the LRECL is not a record: the source program abends on it. */
    @Test
    void aTruncatedRecordFailsTheRun() throws IOException {
        Path file = write("short", "AAAAB");

        FixedLengthRecordReader reader = open(List.of(file));

        assertThat(reader.read()).isNotNull();
        assertThatThrownBy(reader::read)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Truncated record of 1 bytes");
        reader.close();
    }

    private static FixedLengthRecordReader open(List<Path> files) {
        FixedLengthRecordReader reader = new FixedLengthRecordReader(files, 4);
        reader.open(new ExecutionContext());
        return reader;
    }

    private static List<String> records(FixedLengthRecordReader reader) throws IOException {
        List<String> records = new ArrayList<>();
        byte[] record;
        while ((record = reader.read()) != null) {
            records.add(new String(record, StandardCharsets.ISO_8859_1));
        }
        return records;
    }

    private Path write(String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.ISO_8859_1));
        return file;
    }
}
