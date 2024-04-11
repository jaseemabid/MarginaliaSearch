package nu.marginalia.index.construction;

import lombok.SneakyThrows;
import nu.marginalia.process.control.ProcessHeartbeat;
import nu.marginalia.index.journal.IndexJournalFileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

public class ReverseIndexConstructor {

    private static final Logger logger = LoggerFactory.getLogger(ReverseIndexConstructor.class);

    public enum CreateReverseIndexSteps {
        CONSTRUCT,
        FINALIZE,
        FINISHED
    }

    private final Path outputFileDocs;
    private final Path outputFileWords;
    private final JournalReaderSource readerSource;
    private final DocIdRewriter docIdRewriter;
    private final Path tmpDir;

    public ReverseIndexConstructor(Path outputFileDocs,
                                   Path outputFileWords,
                                   JournalReaderSource readerSource,
                                   DocIdRewriter docIdRewriter,
                                   Path tmpDir) {
        this.outputFileDocs = outputFileDocs;
        this.outputFileWords = outputFileWords;
        this.readerSource = readerSource;
        this.docIdRewriter = docIdRewriter;
        this.tmpDir = tmpDir;
    }

    public void createReverseIndex(Path sourceBaseDir) throws IOException
    {
        var inputs = IndexJournalFileNames.findJournalFiles(sourceBaseDir);
        if (inputs.isEmpty()) {
            logger.error("No journal files in base dir {}", sourceBaseDir);
            return;
        }

        inputs
            .parallelStream()
            .map(this::construct)
            .reduce(this::merge)
            .ifPresent(this::finalizeIndex);
    }

    @SneakyThrows
    private ReversePreindexReference construct(Path input) {
        return ReversePreindex
                .constructPreindex(readerSource.construct(input), docIdRewriter, tmpDir)
                .closeToReference();
    }

    @SneakyThrows
    private ReversePreindexReference merge(ReversePreindexReference leftR, ReversePreindexReference rightR) {

        var left = leftR.open();
        var right = rightR.open();

        try {
            return ReversePreindex.merge(tmpDir, left, right).closeToReference();
        }
        finally {
            left.delete();
            right.delete();
        }


    }

    @SneakyThrows
    private void finalizeIndex(ReversePreindexReference finalPR) {
        var finalP = finalPR.open();
        finalP.finalizeIndex(outputFileDocs, outputFileWords);
        finalP.delete();
    }


}
