package nu.marginalia.tools;

import com.google.inject.Guice;
import com.google.inject.Injector;
import nu.marginalia.converting.ConverterModule;
import nu.marginalia.loading.ConvertedDomainReader;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.process.log.WorkLog;
import nu.marginalia.service.module.DatabaseModule;
import nu.marginalia.tools.experiments.*;
import plan.CrawlPlanLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ExperimentRunnerMain {

    private static Map<String, Class<? extends Experiment>> experiments = Map.of(
            "test", TestExperiment.class,
            "adblock", AdblockCrawlDataExperiment.class,
            "topic", TopicExperiment.class,
            "sentence-statistics", SentenceStatisticsExperiment.class,
            "site-statistics", SiteStatisticsExperiment.class
    );

    public static void main(String... args) throws IOException {
        if (args.length != 2) {
            System.err.println("Expected arguments: plan.yaml experiment-name");
            return;
        }

        if (!experiments.containsKey(args[1])) {
            System.err.println("Valid experiment names: " + experiments.keySet());
            return;
        }

        var plan = new CrawlPlanLoader().load(Path.of(args[0]));

        Injector injector = Guice.createInjector(
                new DatabaseModule(),
                new ConverterModule(plan)
        );

        Experiment experiment = injector.getInstance(experiments.get(args[1]));

        if (experiment instanceof CrawlDataExperiment crawlDataExperiment) {

            Map<String, String> idToDomain = new HashMap<>();
            plan.forEachCrawlingSpecification(spec -> {
                idToDomain.put(spec.id, spec.domain);
            });


            plan.forEachCrawledDomain(
                    id -> crawlDataExperiment.isInterested(idToDomain.get(id)),
                    crawlDataExperiment::process
            );

            crawlDataExperiment.onFinish();
        }
        else if (experiment instanceof ProcessedDataExperiment processedDataExperiment) {
            var logFile = plan.process.getLogFile();
            ConvertedDomainReader domainReader = new ConvertedDomainReader(GsonFactory.get());

            WorkLog.readLog(logFile, entry -> {
                Path destDir = plan.getProcessedFilePath(entry.path());
                try {
                    var data = domainReader.read(destDir, entry.cnt());
                    processedDataExperiment.process(data);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            processedDataExperiment.onFinish();;
        }

    }
}
