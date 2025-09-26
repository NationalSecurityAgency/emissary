package emissary.util;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * POJO for parsing and returning values from a git properties file. The properties file is created during the maven
 * build process by the git-commit-id-maven-plugin
 *
 */
@SuppressWarnings("unused")
public class GitRepositoryState {

    private static final String UNKNOWN = "UNKNOWN";

    private static final Logger LOG = LoggerFactory.getLogger(GitRepositoryState.class);

    private final String tags;
    private final String branch;
    private final String dirty;
    private final String remoteOriginUrl;
    private final String commitIdAbbrev;
    private final String commitId;
    private final String describe;
    private final String describeShort;
    private final String commitUserName;
    private final String commitUserEmail;
    private final String commitMessageFull;
    private final String commitMessageShort;
    private final String commitTime;
    private final String closestTagName;
    private final String closestTagCommitCount;
    private final String buildUserName;
    private final String buildUserEmail;
    private final String buildTime;
    private final String buildHost;
    private final String buildVersion;

    private GitRepositoryState(Properties properties) {

        this.tags = properties.getOrDefault("git.tags", UNKNOWN).toString();
        this.branch = properties.getOrDefault("git.branch", UNKNOWN).toString();
        this.dirty = properties.getOrDefault("git.dirty", UNKNOWN).toString();
        this.remoteOriginUrl = properties.getOrDefault("git.remote.origin.url", UNKNOWN).toString();

        this.commitIdAbbrev = properties.getOrDefault("git.commit.id.abbrev", UNKNOWN).toString();
        this.commitId = properties.getOrDefault("git.commit.id.full", UNKNOWN).toString();
        this.describe = properties.getOrDefault("git.commit.id.describe", UNKNOWN).toString();
        this.describeShort = properties.getOrDefault("git.commit.id.describe-short", UNKNOWN).toString();
        this.commitUserName = properties.getOrDefault("git.commit.user.name", UNKNOWN).toString();
        this.commitUserEmail = properties.getOrDefault("git.commit.user.email", UNKNOWN).toString();
        this.commitMessageFull = properties.getOrDefault("git.commit.message.full", UNKNOWN).toString();
        this.commitMessageShort = properties.getOrDefault("git.commit.message.short", UNKNOWN).toString();
        this.commitTime = properties.getOrDefault("git.commit.time", UNKNOWN).toString();
        this.closestTagName = properties.getOrDefault("git.closest.tag.name", UNKNOWN).toString();
        this.closestTagCommitCount = properties.getOrDefault("git.closest.tag.commit.count", UNKNOWN).toString();

        this.buildUserName = properties.getOrDefault("git.build.user.name", UNKNOWN).toString();
        this.buildUserEmail = properties.getOrDefault("git.build.user.email", UNKNOWN).toString();
        this.buildTime = properties.getOrDefault("git.build.time", UNKNOWN).toString();
        this.buildHost = properties.getOrDefault("git.build.host", UNKNOWN).toString();
        this.buildVersion = properties.getOrDefault("git.build.version", UNKNOWN).toString();
    }

    public static GitRepositoryState getRepositoryState() {
        return getRepositoryState("emissary.git.properties");
    }

    public static GitRepositoryState getRepositoryState(String gitProperties) {
        Properties properties = new Properties();
        try (InputStream inStream = GitRepositoryState.class.getClassLoader().getResourceAsStream(gitProperties)) {
            if (inStream != null) {
                properties.load(inStream);
            } else {
                LOG.error("Failed to get repository state. {} not found.", gitProperties);
            }
        } catch (Exception e) {
            LOG.error("Failed to get repository state", e);
        }
        return new GitRepositoryState(properties);
    }

    public static GitRepositoryState getRepositoryState(Path gitProperties) {
        Properties properties = new Properties();
        try (InputStream propertiesStream = Files.newInputStream(gitProperties)) {
            properties.load(propertiesStream);
        } catch (Exception e) {
            LOG.error("Failed to get repository state", e);
        }
        return new GitRepositoryState(properties);
    }

    public static String dumpVersionInfo(@Nullable GitRepositoryState gitRepositoryState, String applicationName) {
        String buildVersion = UNKNOWN;
        String buildTime = UNKNOWN;
        String commitIdAbbrev = UNKNOWN;

        if (null != gitRepositoryState) {
            buildVersion = gitRepositoryState.getBuildVersion();
            buildTime = gitRepositoryState.getBuildTime();
            commitIdAbbrev = gitRepositoryState.getCommitIdAbbrev();
        }
        return String.format("%s Version: %s - built on: %s - git hash: %s", applicationName, buildVersion, buildTime, commitIdAbbrev);
    }

    public String getTags() {
        return tags;
    }

    public String getBranch() {
        return branch;
    }

    public String getDirty() {
        return dirty;
    }

    public String getRemoteOriginUrl() {
        return remoteOriginUrl;
    }

    public String getCommitIdAbbrev() {
        return commitIdAbbrev;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getDescribe() {
        return describe;
    }

    public String getDescribeShort() {
        return describeShort;
    }

    public String getCommitUserName() {
        return commitUserName;
    }

    public String getCommitUserEmail() {
        return commitUserEmail;
    }

    public String getCommitMessageFull() {
        return commitMessageFull;
    }

    public String getCommitMessageShort() {
        return commitMessageShort;
    }

    public String getCommitTime() {
        return commitTime;
    }

    public String getClosestTagName() {
        return closestTagName;
    }

    public String getClosestTagCommitCount() {
        return closestTagCommitCount;
    }

    public String getBuildUserName() {
        return buildUserName;
    }

    public String getBuildUserEmail() {
        return buildUserEmail;
    }

    public String getBuildTime() {
        return buildTime;
    }

    public String getBuildHost() {
        return buildHost;
    }

    public String getBuildVersion() {
        return buildVersion;
    }
}
