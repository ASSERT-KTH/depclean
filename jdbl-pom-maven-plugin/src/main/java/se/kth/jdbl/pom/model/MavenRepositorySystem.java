package se.kth.jdbl.pom.model;

import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.*;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.repository.internal.MavenServiceLocator;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.connector.file.FileRepositoryConnectorFactory;
import org.sonatype.aether.connector.wagon.WagonProvider;
import org.sonatype.aether.connector.wagon.WagonRepositoryConnectorFactory;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyFilter;
import org.sonatype.aether.graph.Exclusion;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.resolution.DependencyResult;
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.artifact.JavaScopes;
import org.sonatype.aether.util.filter.DependencyFilterUtils;
import org.sonatype.aether.util.filter.PatternExclusionsDependencyFilter;
import org.sonatype.aether.util.graph.PreorderNodeListGenerator;

import java.io.File;
import java.util.*;

/**
 * Resolved dependencies from Maven style repositories.
 */
public class MavenRepositorySystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenRepositorySystem.class.getName());

    private RepositorySystem repositorySystem;
    private RemoteRepository centralRepository;
    private LocalRepository localRepository;
    private List<RemoteRepository> additionalRepositories;
    private Set<String> globalExclusions;

    /**
     * Creates a new MavenRepositorySystem.
     */
    public MavenRepositorySystem() {
        this.repositorySystem = initRepositorySystem();
        Settings settings = buildMavenSettings();
        this.localRepository = new LocalRepository(getDefaultLocalRepository(settings));
        this.centralRepository = createCentralRepository();
        this.additionalRepositories = new ArrayList<>();
        this.globalExclusions = new HashSet<>();
    }

    /**
     * Resolve an artifact and all its runtime dependencies.
     *
     * @param groupId    the artifact group ID.
     * @param artifactId the artifact ID.
     * @param version    the artifact version.
     * @return The artifact and all its runtime dependencies as files.
     * @throws DependencyResolutionException If the artifact can not be properly resolved.
     */
    public List<File> resolveDependencies(String groupId, String artifactId, String version) throws DependencyResolutionException {
        Artifact artifact = new DefaultArtifact(groupId, artifactId, "", "jar", version);
        return resolveDependencies(artifact, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
    }

    /**
     * Resolve an artifact and all its runtime dependencies.
     *
     * <p>
     * The artifact specification uses the Maven/Aether artifact coords syntax:
     * </p>
     * <p>
     * Format: &lt;groupId&gt;:&lt;artifactId&gt;[:&lt;extension&gt;[:&lt;classifier&gt;]]:&lt;version&gt;
     * <br/>
     * Example: my.group:my.artifact:1.0-SNAPSHOT
     * </p>
     *
     * @param coords the artifact specification.
     * @return The artifact and all its runtime dependencies as files.
     * @throws DependencyResolutionException If the artifact can not be properly resolved.
     */
    public List<File> resolveDependencies(String coords) throws DependencyResolutionException {
        Artifact artifact = new DefaultArtifact(coords);
        return resolveDependencies(artifact, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
    }

    /**
     * Resolve all dependencies and transitive runtime dependencies specified in
     * a POM file.
     *
     * <p>
     * Additional information extracted from the POM:
     * <ul>
     * <li>Dependency exclusions.</li>
     * <li>Repository specifications.</li>
     * <li>Parent POMs are parsed as well (if they can be found).</li>
     * </ul>
     * </p>
     *
     * @param pom the POM (core.xml) file.
     * @return All dependencies specified in the POM and their transitive runtime
     * dependencies as files.
     * @throws DependencyResolutionException If some artifact can not be properly resolved.
     */
    public List<File> resolveDependenciesFromPom(File pom) throws DependencyResolutionException {
        Model model = getEffectiveModel(pom);
        HashSet<File> files = new HashSet();
        for (org.apache.maven.model.Dependency dependency : model.getDependencies()) {
            Artifact artifact = new DefaultArtifact(dependency.getGroupId(),
                    dependency.getArtifactId(), dependency.getType(),
                    dependency.getVersion());
            files.addAll(resolveDependencies(artifact, model.getRepositories(),
                    getSonatypeExclusions(dependency)));
        }

        return new ArrayList<>(files);
    }

    /**
     * Resolve an artifact and its transitive runtime dependencies given a list
     * of repositories and artifact exclusions.
     *
     * @param artifact     The artifact to resolve.
     * @param repositories Additional repositories to use for dependency resolution.
     * @param exclusions   Artifacts not to include in the final list of files.
     * @return The artifact and its transitive runtime dependencies as files.
     * @throws DependencyResolutionException If the artifact can not be properly resolved.
     */
    private List<File> resolveDependencies(Artifact artifact, List<Repository> repositories, List<Exclusion> exclusions)
            throws DependencyResolutionException {

        RepositorySystemSession session = createSession();
        Dependency dependency = new Dependency(artifact, JavaScopes.RUNTIME,
                false, exclusions);
        DependencyFilter classpathFilter =
                DependencyFilterUtils.classpathFilter(JavaScopes.RUNTIME);

        PatternExclusionsDependencyFilter patternExclusionFilter =
                new PatternExclusionsDependencyFilter(globalExclusions);
        DependencyFilter filter = DependencyFilterUtils.andFilter(classpathFilter,
                patternExclusionFilter);

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(dependency);
        for (RemoteRepository repository : getRepositories(toRemoteRepositories(repositories))) {
            collectRequest.addRepository(repository);
        }
        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest(collectRequest);
        dependencyRequest.setFilter(filter);
        DependencyResult result = repositorySystem.resolveDependencies(session,
                dependencyRequest);

        PreorderNodeListGenerator listGen = new PreorderNodeListGenerator();
        result.getRoot().accept(listGen);

        return listGen.getFiles();
    }

    /**
     * Add an additional repository.
     * <p>
     * NOTE: the local repository and the Maven central repository are added
     * by default.
     *
     * @param repository The repository to use.
     */
    public void addRepository(RemoteRepository repository) {
        this.additionalRepositories.add(repository);
    }

    /**
     * Remove a repository.
     *
     * @param repository the repository to remove.
     */
    public void removeRepository(RemoteRepository repository) {
        this.additionalRepositories.remove(repository);
    }

    /**
     * Specify a pattern that excludes all matching artifacts from any
     * dependency resolution.
     * <p>
     * Each pattern segment is optional and supports full and partial * wildcards.
     * An empty pattern segment is treated as an implicit wildcard.
     * <p>
     * For example, org.apache.* would match all artifacts whose group id started
     * with org.apache. , and :::*-SNAPSHOT would match all snapshot artifacts.
     *
     * @param pattern [groupId]:[artifactId]:[extension]:[version]
     */
    public void addGlobalExclusion(String pattern) {
        globalExclusions.add(pattern);
    }

    /**
     * Resolve the effective Maven model (core) for a POM file.
     * <p>
     * This resolves the POM hierarchy (parents and modules) and creates an
     * overall model.
     *
     * @param pom the POM file to resolve.
     * @return the effective model.
     */
    public Model getEffectiveModel(File pom) {
        ModelBuildingRequest req = new DefaultModelBuildingRequest();
        req.setProcessPlugins(false);
        req.setPomFile(pom);
        req.setModelResolver(new SimpleModelResolver(repositorySystem, createSession(), getRepositories(Collections.EMPTY_LIST)));
        req.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);

        ModelBuilder builder = new DefaultModelBuilderFactory().newInstance();
        try {
            return builder.build(req).getEffectiveModel();
        } catch (ModelBuildingException ex) {
            LOGGER.warn("Could not build invoke model.", ex);
        }

        return new Model();
    }

    /**
     * Get all configured repositories.
     *
     * @param repositories additional repositories to include.
     * @return an unmodifyable list of repositories.
     */
    public List<RemoteRepository> getRepositories(List<RemoteRepository> repositories) {
        int size = additionalRepositories.size() + repositories.size() + 1;
        List<RemoteRepository> remoteRepositories = new ArrayList<>(
                size);
        HashSet<RemoteRepository> set = new HashSet<>(size);
        set.add(centralRepository);
        remoteRepositories.add(centralRepository);
        for (RemoteRepository repository : additionalRepositories) {
            if (set.add(repository)) {
                remoteRepositories.add(repository);
            }
        }
        for (RemoteRepository repository : repositories) {
            if (set.add(repository)) {
                remoteRepositories.add(repository);
            }
        }

        set.clear();

        return Collections.unmodifiableList(remoteRepositories);
    }

    /**
     * Creates a repository session.
     *
     * @return the repository session.
     */
    protected RepositorySystemSession createSession() {
        MavenRepositorySystemSession session = new MavenRepositorySystemSession();
        session.setLocalRepositoryManager(
                repositorySystem.newLocalRepositoryManager(localRepository));
        session.setTransferListener(
                new LoggingTransferListener(LogLevel.TRACE, LogLevel.TRACE));
        session.setRepositoryListener(
                new LoggingRepositoryListener(LogLevel.TRACE, LogLevel.TRACE));
        return session;
    }

    /**
     * Creates Sonatype exclusions from Maven dependency exclusions.
     *
     * @param dependency the Maven dependency.
     * @return the Sonatype exclusion.
     */
    protected List<Exclusion> getSonatypeExclusions(org.apache.maven.model.Dependency dependency) {
        List<org.apache.maven.model.Exclusion> mavenExclusions = dependency.getExclusions();
        List<Exclusion> exclusions = new ArrayList<Exclusion>(mavenExclusions.size());
        for (org.apache.maven.model.Exclusion mavenExclusion : mavenExclusions) {
            exclusions.add(new Exclusion(stringOrWildcard(mavenExclusion.getGroupId()),
                    stringOrWildcard(mavenExclusion.getArtifactId()), "*", "*"));
        }
        return exclusions;
    }

    private String stringOrWildcard(String string) {
        if (string == null || string.isEmpty()) {
            return "*";
        }
        return string;
    }

    private RepositorySystem initRepositorySystem() {
        MavenServiceLocator locator = new MavenServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, FileRepositoryConnectorFactory.class);
        locator.addService(RepositoryConnectorFactory.class, WagonRepositoryConnectorFactory.class);
        locator.setServices(WagonProvider.class, new SimpleWagonProvider());

        return locator.getService(RepositorySystem.class);
    }

    private Settings buildMavenSettings() {
        try {
            File settingsXml = new File(new File(System.getProperty("user.home"), ".m2"), "settings.xml");
            if (settingsXml.canRead()) {
                SettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
                SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
                request.setSystemProperties(System.getProperties());
                request.setUserSettingsFile(settingsXml);

                return settingsBuilder.build(request).getEffectiveSettings();
            }
        } catch (SettingsBuildingException ex) {
            LOGGER.warn("Could not build settings from user settings.xml.", ex);
        }

        return new Settings();
    }

    private File getDefaultLocalRepository(Settings settings) {
        String repoPath = settings.getLocalRepository();
        if (repoPath == null || repoPath.isEmpty()) {
            return new File(new File(System.getProperty("user.home"), ".m2"), "repository");
        } else {
            return new File(repoPath);
        }
    }

    /**
     * Creates the Maven central repository specification.
     *
     * @return the Maven central repository specification.
     */
    private RemoteRepository createCentralRepository() {
        return createRemoteRepository("central", "default", "http://repo1.invoke.org/maven2/");
    }

    /**
     * Creates a repository specification.
     *
     * @param id   some user defined ID for the repository
     * @param type the repository type. typically "default".
     * @param url  the repository URL.
     * @return the repository specification.
     */
    private RemoteRepository createRemoteRepository(String id, String type, String url) {
        return new RemoteRepository(id, type, url);
    }

    private List<RemoteRepository> toRemoteRepositories(List<Repository> repositories) {
        List<RemoteRepository> remoteRepositories = new ArrayList<>(repositories.size());
        for (Repository repository : repositories) {
            remoteRepositories.add(ArtifactDescriptorUtils.toRemoteRepository(repository));
        }

        return remoteRepositories;
    }
}
