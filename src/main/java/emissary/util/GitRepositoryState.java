package emissary.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * POJO for parsing and returning values from a git properties file. The properties file is created during the maven
 * build process by the git-commit-id-maven-plugin
 *
 */
public class GitRepositoryState {

    private static final Logger LOG = LoggerFactory.getLogger(GitRepositoryState.class);

    private final String tags;
    private final String branch;
    private final String dirty;
    private final String remoteOriginUrl;
    private final String commitIdAbbrev;
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

    public GitRepositoryState(Properties properties) {

        this.tags = properties.get("git.tags").toString();
        this.branch = properties.get("git.branch").toString();
        this.dirty = properties.get("git.dirty").toString();
        this.remoteOriginUrl = properties.get("git.remote.origin.url").toString();

        this.commitIdAbbrev = properties.get("git.commit.id.abbrev").toString();
        this.describe = properties.get("git.commit.id.describe").toString();
        this.describeShort = properties.get("git.commit.id.describe-short").toString();
        this.commitUserName = properties.get("git.commit.user.name").toString();
        this.commitUserEmail = properties.get("git.commit.user.email").toString();
        this.commitMessageFull = properties.get("git.commit.message.full").toString();
        this.commitMessageShort = properties.get("git.commit.message.short").toString();
        this.commitTime = properties.get("git.commit.time").toString();
        this.closestTagName = properties.get("git.closest.tag.name").toString();
        this.closestTagCommitCount = properties.get("git.closest.tag.commit.count").toString();

        this.buildUserName = properties.get("git.build.user.name").toString();
        this.buildUserEmail = properties.get("git.build.user.email").toString();
        this.buildTime = properties.get("git.build.time").toString();
        this.buildHost = properties.get("git.build.host").toString();
        this.buildVersion = properties.get("git.build.version").toString();
    }

    public static GitRepositoryState getRepositoryState() {
        return getRepositoryState("emissary.git.properties");
    }

    public static GitRepositoryState getRepositoryState(String gitProperties) {
        Properties properties = new Properties();
        try {
            properties.load(GitRepositoryState.class.getClassLoader().getResourceAsStream(gitProperties));
        } catch (IOException ie) {
            LOG.error("Failed to get repository state", ie);
        }
        return new GitRepositoryState(properties);
    }

    public static String dumpVersionInfo(GitRepositoryState gitRepositoryState, String applicationName) {
        return String.format("%s Version: %s - built on %s - git hash: %s", applicationName, gitRepositoryState.buildVersion,
                gitRepositoryState.buildTime, gitRepositoryState.getCommitIdAbbrev());
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
