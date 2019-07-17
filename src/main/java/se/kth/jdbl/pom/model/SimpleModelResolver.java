package se.kth.jdbl.pom.model;

import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A model resolver to assist building of dependency POMs.
 */
public class SimpleModelResolver implements ModelResolver {

    private final RepositorySystem system;
    private final RepositorySystemSession session;
    private final Set<String> repositoryIds;
    private List<RemoteRepository> repositories;

    public ModelSource resolveModel(Parent parent) {
        return null;
    }

    /**
     * Creates a model resolver to assist building of dependency POMs.
     *
     * @param system             a {@link RepositorySystem}
     * @param session            a {@link RepositorySystemSession}
     * @param remoteRepositories remote repositories to use for resolution.
     */
    public SimpleModelResolver(RepositorySystem system, RepositorySystemSession session,
                               List<RemoteRepository> remoteRepositories) {
        this.system = system;
        this.session = session;
        this.repositories = new ArrayList<RemoteRepository>(remoteRepositories);
        this.repositoryIds = new HashSet<String>(
                remoteRepositories.size() < 3 ? 3 : remoteRepositories.size());

        for (RemoteRepository repository : remoteRepositories) {
            repositoryIds.add(repository.getId());
        }
    }

    /**
     * Clone Constructor.
     *
     * @param original a SimpleModelResolver.
     */
    private SimpleModelResolver(SimpleModelResolver original) {
        this.session = original.session;
        this.system = original.system;
        this.repositoryIds = new HashSet<String>(original.repositoryIds);
    }

    @Override
    public void addRepository(Repository repository) throws InvalidRepositoryException {
        if (!repositoryIds.add(repository.getId())) {
            return;
        }

        this.repositories.add(ArtifactDescriptorUtils.toRemoteRepository(repository));
    }

    @Override
    public ModelResolver newCopy() {
        return new SimpleModelResolver(this);
    }

    @Override
    public ModelSource resolveModel(String groupId, String artifactId, String version)
            throws UnresolvableModelException {
        Artifact pomArtifact = new DefaultArtifact(groupId, artifactId, "", "core", version);

        try {
            ArtifactRequest request = new ArtifactRequest(pomArtifact, repositories, null);
            pomArtifact = system.resolveArtifact(session, request).getArtifact();
        } catch (ArtifactResolutionException ex) {
            throw new UnresolvableModelException(ex.getMessage(), groupId, artifactId, version, ex);
        }

        File pomFile = pomArtifact.getFile();

        return new FileModelSource(pomFile);
    }
}