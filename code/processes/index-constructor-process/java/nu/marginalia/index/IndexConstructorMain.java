package nu.marginalia.index;

import nu.marginalia.service.ProcessMainClass;
import nu.marginalia.index.construction.ReverseIndexConstructor;
import nu.marginalia.index.journal.reader.IndexJournalReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public class IndexConstructorMain extends ProcessMainClass {

    private static final Logger logger = LoggerFactory.getLogger(IndexConstructorMain.class);
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Arguments: input-dir output-dir");
            return;
        }

        Path inDir = Path.of(args[0]);
        Path outDir = Path.of(args[1]);
        Path tmpDir = Path.of(args[1]);

        Path outputFileDocs = outDir.resolve("docs.dat");
        Path outputFileWords = outDir.resolve("words.dat");

        try {
            if (!Files.isDirectory(inDir))
                throw new IllegalArgumentException("Input dir does not exist");

            if (!Files.isDirectory(tmpDir)) Files.createDirectories(tmpDir);

            new ReverseIndexConstructor(outputFileDocs, outputFileWords,
                    IndexJournalReader::singleFile,
                    l -> l, tmpDir)
                    .createReverseIndex(inDir);


        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
