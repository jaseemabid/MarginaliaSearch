package nu.marginalia.tools.experiments;
import cc.mallet.types.*;
import cc.mallet.pipe.*;
import cc.mallet.topics.*;

import com.google.inject.Inject;
import lombok.SneakyThrows;
import nu.marginalia.WmsaHome;
import nu.marginalia.converting.instruction.Instruction;
import nu.marginalia.converting.instruction.Interpreter;
import nu.marginalia.converting.instruction.instructions.DomainLink;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocument;
import nu.marginalia.converting.instruction.instructions.LoadProcessedDocumentWithError;
import nu.marginalia.converting.processor.DomainProcessor;
import nu.marginalia.keyword.model.DocumentKeywords;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.model.idx.WordMetadata;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import nu.marginalia.tools.ProcessedDataExperiment;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class SiteStatisticsExperiment implements ProcessedDataExperiment {


    private final DomainProcessor domainProcessor;
    private static final TermFrequencyDict dict = new TermFrequencyDict(WmsaHome.getLanguageModels());

    int numTopics = 64;
    double alpha = 1.0;
    double beta = 0.01;

    int numProcessed = 0;

    List<Pipe> pipeList = new ArrayList<Pipe>();

    InstanceList instances;

    @Inject
    public SiteStatisticsExperiment(DomainProcessor domainProcessor) {
        this.domainProcessor = domainProcessor;

        pipeList.add(new TokenSequence2FeatureSequence());
        instances = new InstanceList(new SerialPipes(pipeList));
    }

    @Override
    public synchronized boolean process(List<Instruction> instructions) {
        DomainKeywordAccumulator accumulator = new DomainKeywordAccumulator();

        for (Instruction instruction : instructions) {
            instruction.apply(accumulator);
        }

        var tokens = accumulator.keywords;
        if (!tokens.isEmpty()) {
            for (var t : tokens) {
                instances.addThruPipe(new Instance(new TokenSequence(t), null, null, null));
            }
        }

        return false;
    }

    static class DomainKeywordAccumulator implements Interpreter {
        public final List<List<Token>> keywords = new ArrayList<>();

        @Override
        public void loadUrl(EdgeUrl[] url) {

        }

        @Override
        public void loadDomain(EdgeDomain[] domain) {

        }

        @Override
        public void loadRssFeed(EdgeUrl[] rssFeed) {

        }

        @Override
        public void loadDomainLink(DomainLink[] links) {

        }

        @Override
        public void loadProcessedDomain(EdgeDomain domain, DomainIndexingState state, String ip) {

        }

        @Override
        public void loadProcessedDocument(LoadProcessedDocument loadProcessedDocument) {
        }

        @Override
        public void loadProcessedDocumentWithError(LoadProcessedDocumentWithError loadProcessedDocumentWithError) {

        }

        @Override
        public void loadKeywords(EdgeUrl url, DocumentMetadata metadata, DocumentKeywords words) {
            var kw = new ArrayList<Token>();
            for (int i = 0; i < words.size(); i++) {
                if (isWordEligible(words.keywords()[i], words.metadata()[i])) {
                    kw.add(new Token(words.keywords()[i]));
                }
            }
            if (!kw.isEmpty())
                keywords.add(kw);
        }

        @Override
        public void loadDomainRedirect(DomainLink link) {

        }

        @Override
        public void loadDomainMetadata(EdgeDomain domain, int knownUrls, int goodUrls, int visitedUrls) {

        }
    }

    static private boolean isWordEligible(String w, long m) {
        if (w.contains("_"))
            return false;

        if (Long.bitCount(WordMetadata.decodePositions(m)) < 2)
            return false;

        if (w.length() < 3)
            return false;

        return true;
    }




    @SneakyThrows
    @Override
    public void onFinish() {

        ParallelTopicModel model = new ParallelTopicModel(numTopics, alpha, beta);
        model.addInstances(instances);
        model.setNumThreads(16); // Set the number of threads for parallel processing
        model.setNumIterations(1000);

        model.estimate();

        int numTopWords = 48; // The number of top words you want to display for each topic
        Object[][] topWords = model.getTopWords(numTopWords);
        for (int topic = 0; topic < model.getNumTopics(); topic++) {
            System.out.print("Topic " + topic + ": ");
            for (int word = 0; word < topWords[topic].length; word++) {
                System.out.print(topWords[topic][word] + " ");
            }
            System.out.println();
        }

    }
}
