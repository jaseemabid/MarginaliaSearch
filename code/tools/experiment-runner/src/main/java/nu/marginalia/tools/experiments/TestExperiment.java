package nu.marginalia.tools.experiments;

import nu.marginalia.crawling.model.CrawledDomain;
import nu.marginalia.tools.CrawlDataExperiment;

public class TestExperiment implements CrawlDataExperiment {
    @Override
    public boolean process(CrawledDomain domain) {
        return true;
    }

    @Override
    public void onFinish() {
        System.out.println("Tada!");
    }
}
