package nu.marginalia.wmsa.edge.dbcommon;

import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.id.EdgeId;
import nu.marginalia.wmsa.edge.model.id.EdgeIdCollection;
import nu.marginalia.wmsa.edge.model.search.EdgePageScoreAdjustment;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;
import nu.marginalia.wmsa.edge.search.model.BrowseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;


public class EdgeDataStoreDaoImpl implements EdgeDataStoreDao {
    private final HikariDataSource dataSource;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Cache<EdgeUrl, EdgeId<EdgeUrl>> urlIdCache = CacheBuilder.newBuilder().maximumSize(100_000).build();
    private final Cache<EdgeDomain, EdgeId<EdgeDomain>> domainIdCache = CacheBuilder.newBuilder().maximumSize(10_000).build();

    public static double QUALITY_LOWER_BOUND_CUTOFF = -15.;
    @Inject
    public EdgeDataStoreDaoImpl(HikariDataSource dataSource)
    {
        this.dataSource = dataSource;
    }


    public synchronized void clearCaches()
    {
        urlIdCache.invalidateAll();
        domainIdCache.invalidateAll();
    }

    @SneakyThrows
    @Override
    public EdgeId<EdgeDomain> getDomainId(EdgeDomain domain) {
        try (var connection = dataSource.getConnection()) {

            return domainIdCache.get(domain, () -> {
                try (var stmt = connection.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE DOMAIN_NAME=?")) {
                    stmt.setString(1, domain.toString());
                    var rsp = stmt.executeQuery();
                    if (rsp.next()) {
                        return new EdgeId<>(rsp.getInt(1));
                    }
                }
                throw new NoSuchElementException();
            });
        }
        catch (UncheckedExecutionException ex) {
            throw ex.getCause();
        }
    }

    private <T> String idList(EdgeIdCollection<EdgeUrl> ids) {
        StringJoiner j = new StringJoiner(",", "(", ")");
        for (var id : ids.values()) {
            j.add(Integer.toString(id));
        }
        return j.toString();
    }

    @SneakyThrows
    @Override
    public List<EdgeUrlDetails> getUrlDetailsMulti(EdgeIdCollection<EdgeUrl> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<EdgeUrlDetails> result = new ArrayList<>(ids.size());

        try (var connection = dataSource.getConnection()) {

            String idString = idList(ids);

            try (var stmt = connection.prepareStatement(
                    """
                            SELECT ID, URL,
                                    TITLE, DESCRIPTION,
                                    QUALITY,
                                    WORDS_TOTAL, FORMAT, FEATURES,
                                    IP, DOMAIN_STATE,
                                    DATA_HASH
                                    FROM EC_URL_VIEW
                                    WHERE TITLE IS NOT NULL 
                                    AND ID IN
                            """ + idString)) {
                stmt.setFetchSize(ids.size());

                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    EdgeUrl url = new EdgeUrl(rsp.getString(2));
                    var val = new EdgeUrlDetails(rsp.getInt(1), url,
                            rsp.getString(3), // title
                            rsp.getString(4), // description
                            rsp.getDouble(5), // quality
                            rsp.getInt(6), // wordsTotal
                            rsp.getString(7), // format
                            rsp.getInt(8), // features
                            rsp.getString(9), // ip
                            EdgeDomainIndexingState.valueOf(rsp.getString(10)), // domainState
                            rsp.getInt(11), // dataHash
                            EdgePageScoreAdjustment.zero(), // urlQualityAdjustment
                            Integer.MAX_VALUE, // rankingId
                            Double.MAX_VALUE, // termScore
                            1, // resultsFromSameDomain
                            "", // positions
                            null // result item
                            );
                    if (val.urlQuality <= QUALITY_LOWER_BOUND_CUTOFF
                    && Strings.isNullOrEmpty(val.description)
                    && val.url.path.length() > 1) {
                        continue;
                    }
                    result.add(val);

                }
            }
        }

        return result;
    }



    public List<BrowseResult> getDomainNeighborsAdjacentCosine(EdgeId<EdgeDomain> domainId, EdgeDomainBlacklist blacklist, int count) {
        List<BrowseResult> domains = new ArrayList<>(count);

        String q = """
                        SELECT
                            EC_DOMAIN.ID,
                            NV.NEIGHBOR_NAME,
                            NV.RELATEDNESS
                        FROM EC_NEIGHBORS_VIEW NV
                        INNER JOIN DATA_DOMAIN_SCREENSHOT ON DATA_DOMAIN_SCREENSHOT.DOMAIN_NAME=NV.NEIGHBOR_NAME
                        INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=NV.NEIGHBOR_ID
                        WHERE NV.DOMAIN_ID=?
                        GROUP BY NV.NEIGHBOR_ID
                        ORDER BY NV.RELATEDNESS DESC
                        """;

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement(q)) {
                stmt.setFetchSize(count);
                stmt.setInt(1, domainId.id());
                stmt.setInt(2, count);
                var rsp = stmt.executeQuery();
                while (rsp.next() && domains.size() < count) {
                    int id = rsp.getInt(1);
                    String domain = rsp.getString(2);
                    double relatedness = rsp.getDouble(3);

                    if (!blacklist.isBlacklisted(id)) {
                        domains.add(new BrowseResult(new EdgeDomain(domain).toRootUrl(), id, relatedness));
                    }
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        return domains;
    }

    @Override
    public List<BrowseResult> getDomainNeighborsAdjacent(EdgeId<EdgeDomain> domainId, EdgeDomainBlacklist blacklist, int count) {
        final Set<BrowseResult> domains = new HashSet<>(count*3);

        final String q = """
                            SELECT EC_DOMAIN.ID AS NEIGHBOR_ID, DOMAIN_NAME, COUNT(*) AS CNT 
                            FROM EC_DOMAIN_NEIGHBORS 
                            INNER JOIN EC_DOMAIN ON NEIGHBOR_ID=EC_DOMAIN.ID 
                            INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID 
                            INNER JOIN EC_DOMAIN_LINK ON DEST_DOMAIN_ID=EC_DOMAIN.ID 
                            WHERE 
                                STATE<2 
                            AND KNOWN_URLS<1000 
                            AND DOMAIN_ALIAS IS NULL 
                            AND EC_DOMAIN_NEIGHBORS.DOMAIN_ID = ? 
                            GROUP BY EC_DOMAIN.ID 
                            HAVING CNT < 100 
                            ORDER BY ADJ_IDX 
                            LIMIT ?
                            """;

        try (var connection = dataSource.getConnection()) {
            try (var stmt = connection.prepareStatement(q)) {
                stmt.setFetchSize(count);
                stmt.setInt(1, domainId.id());
                stmt.setInt(2, count);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    int id = rsp.getInt(1);
                    String domain = rsp.getString(2);

                    if (!blacklist.isBlacklisted(id)) {
                        domains.add(new BrowseResult(new EdgeDomain(domain).toRootUrl(), id, 0));
                    }
                }
            }

            if (domains.size() < count/2) {
                final String q2 = """
                        SELECT EC_DOMAIN.ID, DOMAIN_NAME
                        FROM EC_DOMAIN
                        INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID 
                        INNER JOIN EC_DOMAIN_LINK B ON DEST_DOMAIN_ID=EC_DOMAIN.ID 
                        INNER JOIN EC_DOMAIN_LINK O ON O.DEST_DOMAIN_ID=EC_DOMAIN.ID
                        WHERE B.SOURCE_DOMAIN_ID=? 
                        AND STATE<2 
                        AND KNOWN_URLS<1000 
                        AND DOMAIN_ALIAS IS NULL 
                        GROUP BY EC_DOMAIN.ID 
                        HAVING COUNT(*) < 100 ORDER BY RANK ASC LIMIT ?""";
                try (var stmt = connection.prepareStatement(q2)) {

                    stmt.setFetchSize(count/2);
                    stmt.setInt(1, domainId.id());
                    stmt.setInt(2, count/2 - domains.size());
                    var rsp = stmt.executeQuery();
                    while (rsp.next()  && domains.size() < count/2) {
                        int id = rsp.getInt(1);
                        String domain = rsp.getString(2);

                        if (!blacklist.isBlacklisted(id)) {
                            domains.add(new BrowseResult(new EdgeDomain(domain).toRootUrl(), id, 0));
                        }
                    }
                }
            }

            if (domains.size() < count/2) {
                final String q3 = """
                    SELECT EC_DOMAIN.ID, DOMAIN_NAME
                    FROM EC_DOMAIN
                    INNER JOIN DOMAIN_METADATA ON EC_DOMAIN.ID=DOMAIN_METADATA.ID
                    INNER JOIN EC_DOMAIN_LINK B ON B.SOURCE_DOMAIN_ID=EC_DOMAIN.ID
                    INNER JOIN EC_DOMAIN_LINK O ON O.DEST_DOMAIN_ID=EC_DOMAIN.ID
                    WHERE B.DEST_DOMAIN_ID=? 
                    AND STATE<2 
                    AND KNOWN_URLS<1000 
                    AND DOMAIN_ALIAS IS NULL 
                    GROUP BY EC_DOMAIN.ID
                    HAVING COUNT(*) < 100 
                    ORDER BY RANK ASC 
                    LIMIT ?""";
                try (var stmt = connection.prepareStatement(q3)) {
                    stmt.setFetchSize(count/2);
                    stmt.setInt(1, domainId.id());
                    stmt.setInt(2, count/2 - domains.size());

                    var rsp = stmt.executeQuery();
                    while (rsp.next() && domains.size() < count/2) {
                        int id = rsp.getInt(1);
                        String domain = rsp.getString(2);

                        if (!blacklist.isBlacklisted(id)) {
                            domains.add(new BrowseResult(new EdgeDomain(domain).toRootUrl(), id, 0));
                        }
                    }
                }
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }


        return new ArrayList<>(domains);
    }

    @Override
    public List<BrowseResult> getRandomDomains(int count, EdgeDomainBlacklist blacklist, int set) {

        final String q = """
                SELECT DOMAIN_ID, DOMAIN_NAME
                FROM EC_RANDOM_DOMAINS
                INNER JOIN EC_DOMAIN ON EC_DOMAIN.ID=DOMAIN_ID
                WHERE STATE<2
                AND DOMAIN_SET=?
                AND DOMAIN_ALIAS IS NULL
                ORDER BY RAND()
                LIMIT ?
                """;
        List<BrowseResult> domains = new ArrayList<>(count);
        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.prepareStatement(q)) {
                stmt.setInt(1, set);;
                stmt.setInt(2, count);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    int id = rsp.getInt(1);
                    String domain = rsp.getString(2);

                    if (!blacklist.isBlacklisted(id)) {
                        domains.add(new BrowseResult(new EdgeDomain(domain).toRootUrl(), id, 0));
                    }
                 }
            }
        }
        catch (SQLException ex) {
            logger.error("SQL error", ex);
        }
        return domains;
    }

    @Override
    public List<BrowseResult> getBrowseResultFromUrlIds(EdgeIdCollection<EdgeUrl> urlIds) {
        if (urlIds.isEmpty())
            return Collections.emptyList();

        List<BrowseResult> ret = new ArrayList<>(urlIds.size());

        try (var conn = dataSource.getConnection()) {
            try (var stmt = conn.createStatement()) {

                String inStmt = idList(urlIds);

                var rsp = stmt.executeQuery("""
                    SELECT DOMAIN_ID, DOMAIN_NAME
                    FROM EC_URL_VIEW 
                    INNER JOIN DOMAIN_METADATA ON EC_URL_VIEW.DOMAIN_ID=DOMAIN_METADATA.ID 
                    WHERE 
                        KNOWN_URLS<5000 
                    AND QUALITY>-10 
                    AND EC_URL_VIEW.ID IN 
                    """ + inStmt); // this injection is safe, inStmt is derived from concatenating a list of integers
                while (rsp.next()) {
                    int id = rsp.getInt(1);
                    String domain = rsp.getString(2);

                    ret.add(new BrowseResult(new EdgeDomain(domain).toRootUrl(), id, 0));
                }
            }
        }
        catch (SQLException ex) {
            logger.error("SQL error", ex);
        }

        return ret;
    }

    @Override
    @SneakyThrows
    public Optional<EdgeDomain> getDomain(EdgeId<EdgeDomain> id) {
        try (var connection = dataSource.getConnection()) {

            try (var stmt = connection.prepareStatement("SELECT DOMAIN_NAME FROM EC_DOMAIN WHERE ID=?")) {
                stmt.setInt(1, id.id());
                var rsp = stmt.executeQuery();
                if (rsp.next()) {
                    return Optional.of(new EdgeDomain(rsp.getString(1)));
                }
                return Optional.empty();
            }
        }
    }
}