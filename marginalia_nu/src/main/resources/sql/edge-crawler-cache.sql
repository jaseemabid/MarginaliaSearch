DROP TABLE IF EXISTS DOMAIN_METADATA;
DROP TABLE IF EXISTS EC_FEED_URL;
DROP TABLE IF EXISTS EC_DOMAIN_LINK;
DROP TABLE IF EXISTS EC_PAGE_DATA;
DROP TABLE IF EXISTS EC_URL;
DROP TABLE IF EXISTS EC_DOMAIN_NEIGHBORS;
DROP TABLE IF EXISTS EC_DOMAIN;


CREATE TABLE IF NOT EXISTS DOMAIN_METADATA (
    ID INT PRIMARY KEY,
    KNOWN_URLS INT DEFAULT 0,
    VISITED_URLS INT DEFAULT 0,
    GOOD_URLS INT DEFAULT 0
);


CREATE TABLE IF NOT EXISTS EC_DOMAIN (
    ID INT PRIMARY KEY AUTO_INCREMENT,

    DOMAIN_NAME VARCHAR(255) UNIQUE NOT NULL,
    DOMAIN_TOP VARCHAR(255) NOT NULL,

    INDEXED INT DEFAULT 0 NOT NULL COMMENT "~number of documents visited / 100",
    STATE ENUM('ACTIVE', 'EXHAUSTED', 'SPECIAL', 'SOCIAL_MEDIA', 'BLOCKED', 'REDIR', 'ERROR', 'UNKNOWN') NOT NULL DEFAULT 'active' COMMENT "@see EdgeDomainIndexingState",

    RANK DOUBLE,
    DOMAIN_ALIAS INTEGER,
    IP VARCHAR(32),

    INDEX_DATE TIMESTAMP DEFAULT NOW(),
    DISCOVER_DATE TIMESTAMP DEFAULT NOW(),

    IS_ALIVE BOOLEAN AS (STATE='ACTIVE' OR STATE='EXHAUSTED' OR STATE='SPECIAL' OR STATE='SOCIAL_MEDIA') VIRTUAL
)
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS EC_DOMAIN_BLACKLIST (
    ID INT PRIMARY KEY AUTO_INCREMENT,
    URL_DOMAIN VARCHAR(255) UNIQUE NOT NULL
)
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS EC_URL (
    ID INT PRIMARY KEY AUTO_INCREMENT,
    DOMAIN_ID INT NOT NULL,

    PROTO ENUM('http','https','gemini') NOT NULL COLLATE utf8mb4_unicode_ci,
    PATH VARCHAR(255) NOT NULL,
    PORT INT,
    PARAM VARCHAR(255),

    PATH_HASH BIGINT NOT NULL COMMENT "Hash of PATH for uniqueness check by domain",

    VISITED BOOLEAN NOT NULL DEFAULT FALSE,

    STATE ENUM('ok', 'redirect', 'dead', 'archived', 'disqualified') NOT NULL DEFAULT 'ok' COLLATE utf8mb4_unicode_ci,

    CONSTRAINT CONS UNIQUE (DOMAIN_ID, PATH_HASH),
    FOREIGN KEY (DOMAIN_ID) REFERENCES EC_DOMAIN(ID) ON DELETE CASCADE
)
CHARACTER SET utf8mb4
COLLATE utf8mb4_bin;

CREATE TABLE IF NOT EXISTS EC_PAGE_DATA (
    ID INT PRIMARY KEY AUTO_INCREMENT,

    TITLE VARCHAR(255) NOT NULL,
    DESCRIPTION VARCHAR(255) NOT NULL,

    WORDS_TOTAL INTEGER NOT NULL,
    FORMAT ENUM('PLAIN', 'UNKNOWN', 'HTML123', 'HTML4', 'XHTML', 'HTML5', 'MARKDOWN') NOT NULL,
    FEATURES INT COMMENT "Bit-encoded feature set of document, @see HtmlFeature" NOT NULL,

    DATA_HASH INTEGER NOT NULL,
    QUALITY DOUBLE NOT NULL,

    PUB_YEAR SMALLINT,

    FOREIGN KEY (ID) REFERENCES EC_URL(ID) ON DELETE CASCADE
)
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE EC_FEED_URL (
    URL VARCHAR(255) PRIMARY KEY,
    DOMAIN_ID INT,

    FOREIGN KEY (DOMAIN_ID) REFERENCES EC_DOMAIN(ID) ON DELETE CASCADE
)
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE EC_DOMAIN_NEIGHBORS (
    ID INT PRIMARY KEY AUTO_INCREMENT,
    DOMAIN_ID INT NOT NULL,
    NEIGHBOR_ID INT NOT NULL,
    ADJ_IDX INT NOT NULL,

    CONSTRAINT CONS UNIQUE (DOMAIN_ID, ADJ_IDX),
    FOREIGN KEY (DOMAIN_ID) REFERENCES EC_DOMAIN(ID) ON DELETE CASCADE
)
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE EC_DOMAIN_NEIGHBORS_2 (
    DOMAIN_ID INT NOT NULL,
    NEIGHBOR_ID INT NOT NULL,
    RELATEDNESS DOUBLE NOT NULL,

    PRIMARY KEY (DOMAIN_ID, NEIGHBOR_ID),
    FOREIGN KEY (DOMAIN_ID) REFERENCES EC_DOMAIN(ID) ON DELETE CASCADE,
    FOREIGN KEY (NEIGHBOR_ID) REFERENCES EC_DOMAIN(ID) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS EC_DOMAIN_LINK (
    ID INT PRIMARY KEY AUTO_INCREMENT,
    SOURCE_DOMAIN_ID INT NOT NULL,
    DEST_DOMAIN_ID INT NOT NULL,

    CONSTRAINT CONS UNIQUE (SOURCE_DOMAIN_ID, DEST_DOMAIN_ID),

    FOREIGN KEY (SOURCE_DOMAIN_ID) REFERENCES EC_DOMAIN(ID) ON DELETE CASCADE,
    FOREIGN KEY (DEST_DOMAIN_ID) REFERENCES EC_DOMAIN(ID) ON DELETE CASCADE
);

CREATE OR REPLACE VIEW EC_URL_VIEW AS
    SELECT
        CONCAT(EC_URL.PROTO,
               '://',
               EC_DOMAIN.DOMAIN_NAME,
               IF(EC_URL.PORT IS NULL, '', CONCAT(':', EC_URL.PORT)),
               EC_URL.PATH,
               IF(EC_URL.PARAM IS NULL, '', CONCAT('?', EC_URL.PARAM))
               ) AS URL,
        EC_URL.PATH_HASH AS PATH_HASH,
        EC_URL.PATH AS PATH,
        EC_DOMAIN.DOMAIN_NAME AS DOMAIN_NAME,
        EC_DOMAIN.DOMAIN_TOP AS DOMAIN_TOP,
        EC_URL.ID AS ID,
        EC_DOMAIN.ID AS DOMAIN_ID,

        EC_URL.VISITED AS VISITED,

        EC_PAGE_DATA.QUALITY AS QUALITY,
        EC_PAGE_DATA.DATA_HASH AS DATA_HASH,
        EC_PAGE_DATA.TITLE AS TITLE,
        EC_PAGE_DATA.DESCRIPTION AS DESCRIPTION,
        EC_PAGE_DATA.WORDS_TOTAL AS WORDS_TOTAL,
        EC_PAGE_DATA.FORMAT AS FORMAT,
        EC_PAGE_DATA.FEATURES AS FEATURES,

        EC_DOMAIN.IP AS IP,
        EC_URL.STATE AS STATE,
        EC_DOMAIN.RANK AS RANK,
        EC_DOMAIN.STATE AS DOMAIN_STATE
    FROM EC_URL
    LEFT JOIN EC_PAGE_DATA
        ON EC_PAGE_DATA.ID = EC_URL.ID
    INNER JOIN EC_DOMAIN
        ON EC_URL.DOMAIN_ID = EC_DOMAIN.ID;

CREATE OR REPLACE VIEW EC_NEIGHBORS_VIEW AS
  SELECT
    DOM.DOMAIN_NAME AS DOMAIN_NAME,
    DOM.ID AS DOMAIN_ID,
    NEIGHBOR.DOMAIN_NAME AS NEIGHBOR_NAME,
    NEIGHBOR.ID AS NEIGHBOR_ID,
    ROUND(100 * RELATEDNESS) AS RELATEDNESS
  FROM EC_DOMAIN_NEIGHBORS_2
  INNER JOIN EC_DOMAIN DOM ON DOMAIN_ID=DOM.ID
  INNER JOIN EC_DOMAIN NEIGHBOR ON NEIGHBOR_ID=NEIGHBOR.ID;


CREATE OR REPLACE VIEW EC_RELATED_LINKS_VIEW AS
    SELECT
        SOURCE_DOMAIN_ID,
        SOURCE_DOMAIN.DOMAIN_NAME AS SOURCE_DOMAIN,
        SOURCE_DOMAIN.DOMAIN_TOP AS SOURCE_TOP_DOMAIN,
        DEST_DOMAIN_ID,
        DEST_DOMAIN.DOMAIN_NAME AS DEST_DOMAIN,
        DEST_DOMAIN.DOMAIN_TOP AS DEST_TOP_DOMAIN
    FROM EC_DOMAIN_LINK
    INNER JOIN EC_DOMAIN AS SOURCE_DOMAIN
        ON SOURCE_DOMAIN.ID=SOURCE_DOMAIN_ID
    INNER JOIN EC_DOMAIN AS DEST_DOMAIN
        ON DEST_DOMAIN.ID=DEST_DOMAIN_ID
    ;

CREATE OR REPLACE VIEW EC_RELATED_LINKS_IN AS
    SELECT
        IN_URL.ID AS SRC_URL_ID,
        OUT_URL.ID AS DEST_URL_ID
    FROM EC_DOMAIN_LINK
    INNER JOIN EC_URL AS IN_URL ON IN_URL.DOMAIN_ID=EC_DOMAIN_LINK.SOURCE_DOMAIN_ID
    INNER JOIN EC_URL AS OUT_URL ON OUT_URL.DOMAIN_ID=EC_DOMAIN_LINK.DEST_DOMAIN_ID
    WHERE IN_URL.VISITED AND IN_URL.STATE = 'ok'
     AND OUT_URL.VISITED AND OUT_URL.STATE = 'ok';

CREATE TABLE IF NOT EXISTS EC_API_KEY (
    LICENSE_KEY VARCHAR(255) UNIQUE,
    LICENSE VARCHAR(255) NOT NULL,
    NAME VARCHAR(255) NOT NULL,
    EMAIL VARCHAR(255) NOT NULL,
    RATE INT DEFAULT 10
);

CREATE INDEX IF NOT EXISTS EC_DOMAIN_INDEXED_INDEX ON EC_DOMAIN (INDEXED);
CREATE INDEX IF NOT EXISTS EC_DOMAIN_TOP_DOMAIN ON EC_DOMAIN (DOMAIN_TOP);

---;

DROP TABLE IF EXISTS REF_DICTIONARY;

CREATE TABLE IF NOT EXISTS REF_DICTIONARY (
    TYPE VARCHAR(16),
    WORD VARCHAR(255),
    DEFINITION VARCHAR(255)
)
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

---;

CREATE INDEX IF NOT EXISTS REF_DICTIONARY_WORD ON REF_DICTIONARY (WORD);

CREATE TABLE IF NOT EXISTS REF_WIKI_ARTICLE (
    NAME VARCHAR(255) PRIMARY KEY,
    REF_NAME VARCHAR(255) COMMENT "If this is a redirect, it redirects to this REF_WIKI_ARTICLE.NAME",
    ENTRY LONGBLOB
)
ROW_FORMAT=DYNAMIC
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

---;

CREATE TABLE IF NOT EXISTS DATA_DOMAIN_SCREENSHOT (
  DOMAIN_NAME VARCHAR(255) PRIMARY KEY,
  CONTENT_TYPE ENUM ('image/png', 'image/webp', 'image/svg+xml') NOT NULL,
  DATA LONGBLOB NOT NULL
)
ROW_FORMAT=DYNAMIC
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

CREATE TABLE DATA_DOMAIN_HISTORY (
    DOMAIN_NAME VARCHAR(255) PRIMARY KEY,
    SCREENSHOT_DATE DATE DEFAULT NOW()
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE CRAWL_QUEUE(
    DOMAIN_NAME VARCHAR(255) UNIQUE,
    SOURCE VARCHAR(255)
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE DOMAIN_COMPLAINT(
    ID INT PRIMARY KEY AUTO_INCREMENT,
    DOMAIN_ID INT NOT NULL,

    CATEGORY VARCHAR(255) NOT NULL,
    DESCRIPTION TEXT,
    SAMPLE VARCHAR(255),
    FILE_DATE TIMESTAMP NOT NULL DEFAULT NOW(),

    REVIEWED BOOLEAN AS (REVIEW_DATE > 0) VIRTUAL,
    DECISION VARCHAR(255),
    REVIEW_DATE TIMESTAMP,

    FOREIGN KEY (DOMAIN_ID) REFERENCES EC_DOMAIN(ID) ON DELETE CASCADE
);

---

CREATE TABLE WMSA_PROCESS(
    ID BIGINT PRIMARY KEY,
    NAME VARCHAR(255) NOT NULL,
    TYPE ENUM('SERVICE', 'TASK') NOT NULL,
    START DATETIME NOT NULL DEFAULT NOW(),
    UPDATED DATETIME,
    FINISHED DATETIME,
    PROGRESS DOUBLE DEFAULT 0,
    PROCESS_STATUS ENUM('RUNNING', 'FINISHED', 'DEAD') NOT NULL DEFAULT 'RUNNING',
    PROCESS_SUBSTATUS ENUM('NA', 'OK', 'FAIL') NOT NULL DEFAULT 'NA',
    MUTEX VARCHAR(255),
    TIMEOUT INT NOT NULL DEFAULT 60
);
