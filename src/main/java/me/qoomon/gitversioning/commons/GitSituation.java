package me.qoomon.gitversioning.commons;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static java.time.Instant.EPOCH;
import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static me.qoomon.gitversioning.commons.GitUtil.NO_COMMIT;
import static org.eclipse.jgit.lib.Constants.HEAD;

public class GitSituation {

    private final Repository repository;
    private final File rootDirectory;

    private final ObjectId head;
    private final String rev;
    private final Supplier<ZonedDateTime> timestamp = Lazy.by(this::timestamp);
    private Supplier<String> branch = Lazy.by(this::branch);

    private Supplier<List<String>> tags = Lazy.by(this::tags);

    private final Supplier<Boolean> clean = Lazy.by(this::clean);

    private Pattern describeTagPattern = Pattern.compile(".*");
    private Supplier<GitDescription> description = Lazy.by(this::describe);

    public GitSituation(Repository repository) throws IOException {
        this.repository = repository;
        this.rootDirectory = getWorkTree(repository);
        this.head = repository.resolve(HEAD);
        this.rev = head != null ? head.getName() : NO_COMMIT;
    }

    /**
     * fixed version repository.getWorkTree()
     * handle worktrees as well
     *
     * @param repository
     * @return .git directory
     */
    private File getWorkTree(Repository repository) throws IOException {
        try {
            return repository.getWorkTree();
        } catch (NoWorkTreeException e) {
            File gitDirFile = new File(repository.getDirectory(), "gitdir");
            if (gitDirFile.exists()) {
                String gitDirPath = Files.readAllLines(gitDirFile.toPath()).get(0);
                return new File(gitDirPath).getParentFile();
            }
            throw e;
        }
    }

    public File getRootDirectory() {
        return rootDirectory;
    }

    public String getRev() {
        return rev;
    }

    public ZonedDateTime getTimestamp() {
        return timestamp.get();
    }

    public String getBranch() {
        return branch.get();
    }

    public void setBranch(String branch) {
        this.branch = () -> branch;
    }

    public boolean isDetached() {
        return branch.get() == null;
    }

    public List<String> getTags() {
        return tags.get();
    }

    public void setTags(List<String> tags) {
        this.tags = () -> requireNonNull(tags);
    }

    public boolean isClean() {
        return clean.get();
    }

    public void setDescribeTagPattern(Pattern describeTagPattern) {
        this.describeTagPattern = requireNonNull(describeTagPattern);
        this.description = Lazy.by(this::describe);
    }

    public Pattern getDescribeTagPattern() {
        return describeTagPattern;
    }

    public GitDescription getDescription() {
        return description.get();
    }

    // ----- initialization methods ------------------------------------------------------------------------------------

    private ZonedDateTime timestamp() throws IOException {
        return head != null
                ? GitUtil.revTimestamp(repository, head)
                : ZonedDateTime.ofInstant(EPOCH, UTC);
    }

    private String branch() throws IOException {
        return GitUtil.branch(repository);
    }

    private List<String> tags() throws IOException {
        return head != null ? GitUtil.tagsPointAt(head, repository) : emptyList();
    }

    private boolean clean() throws GitAPIException {
        return GitUtil.status(repository).isClean();
    }

    private GitDescription describe() throws IOException {
        return GitUtil.describe(head, describeTagPattern, repository);
    }
}