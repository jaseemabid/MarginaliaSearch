package nu.marginalia.index;

import nu.marginalia.array.LongArray;
import nu.marginalia.btree.BTreeReader;
import nu.marginalia.index.query.EmptyEntrySource;
import nu.marginalia.index.query.EntrySource;
import nu.marginalia.index.query.ReverseIndexRejectFilter;
import nu.marginalia.index.query.ReverseIndexRetainFilter;
import nu.marginalia.index.query.filter.QueryFilterLetThrough;
import nu.marginalia.index.query.filter.QueryFilterNoPass;
import nu.marginalia.index.query.filter.QueryFilterStepIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReverseIndexReader {
    private final LongArray words;
    private final LongArray documents;
    private final long wordsDataOffset;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final BTreeReader wordsBTreeReader;



    public ReverseIndexReader(Path words, Path documents) throws IOException {
        if (!Files.exists(words) || !Files.exists(documents)) {
            this.words = null;
            this.documents = null;
            this.wordsBTreeReader = null;
            this.wordsDataOffset = -1;
            return;
        }

        logger.info("Switching reverse index");

        this.words = LongArray.mmapRead(words);
        this.documents = LongArray.mmapRead(documents);

        wordsBTreeReader = new BTreeReader(this.words, ReverseIndexParameters.wordsBTreeContext, 0);
        wordsDataOffset = wordsBTreeReader.getHeader().dataOffsetLongs();
    }


    private long wordOffset(long wordId) {
        long idx = wordsBTreeReader.findEntry(wordId);

        if (idx < 0)
            return -1L;

        return words.get(wordsDataOffset + idx + 1);
    }

    public EntrySource documents(long wordId) {
        if (null == words) {
            logger.warn("Reverse index is not ready, dropping query");
            return new EmptyEntrySource();
        }

        long offset = wordOffset(wordId);

        if (offset < 0) return new EmptyEntrySource();

        return new ReverseIndexEntrySource(createReaderNew(offset), 2, wordId);
    }

    public QueryFilterStepIf also(long wordId) {
        long offset = wordOffset(wordId);

        if (offset < 0) return new QueryFilterNoPass();

        return new ReverseIndexRetainFilter(createReaderNew(offset), "full", wordId);
    }

    public QueryFilterStepIf not(long wordId) {
        long offset = wordOffset(wordId);

        if (offset < 0) return new QueryFilterLetThrough();

        return new ReverseIndexRejectFilter(createReaderNew(offset));
    }

    public int numDocuments(long wordId) {
        long offset = wordOffset(wordId);

        if (offset < 0)
            return 0;

        return createReaderNew(offset).numEntries();
    }

    private BTreeReader createReaderNew(long offset) {
        return new BTreeReader(documents, ReverseIndexParameters.docsBTreeContext, offset);
    }

    public long[] getTermMeta(long wordId, long[] docIds) {
        long offset = wordOffset(wordId);

        if (offset < 0) {
            return new long[docIds.length];
        }

        assert isSorted(docIds) : "The input array docIds is assumed to be sorted";

        var reader = createReaderNew(offset);
        return reader.queryData(docIds, 1);
    }

    private boolean isSorted(long[] ids) {
        if (ids.length == 0)
            return true;
        long prev = ids[0];

        for (int i = 1; i < ids.length; i++) {
            if(ids[i] <= prev)
                return false;
        }

        return true;
    }

}