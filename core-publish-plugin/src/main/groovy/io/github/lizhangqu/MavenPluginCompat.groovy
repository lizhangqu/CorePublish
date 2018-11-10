/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.lizhangqu

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer
import org.gradle.api.artifacts.maven.MavenPom
import org.gradle.api.artifacts.maven.MavenResolver
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.DefaultProjectPublication
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.*
import org.gradle.api.publication.maven.internal.DefaultDeployerFactory
import org.gradle.api.publication.maven.internal.DefaultMavenRepositoryHandlerConvention
import org.gradle.api.publication.maven.internal.MavenFactory
import org.gradle.api.tasks.Upload
import org.gradle.configuration.project.ProjectConfigurationActionContainer

import javax.inject.Inject

/**
 * <p>A {@link org.gradle.api.Plugin} which allows project artifacts to be deployed to a Maven repository, or installed
 * to the local Maven cache.</p>
 */
public class MavenPluginCompat implements Plugin<ProjectInternal> {
    public static final int COMPILE_PRIORITY = 300;
    public static final int RUNTIME_PRIORITY = 200;
    public static final int TEST_COMPILE_PRIORITY = 150;
    public static final int TEST_RUNTIME_PRIORITY = 100;

    public static final int PROVIDED_COMPILE_PRIORITY = COMPILE_PRIORITY + 100;
    public static final int PROVIDED_RUNTIME_PRIORITY = COMPILE_PRIORITY + 150;

    public static final String INSTALL_TASK_NAME = "install";

    private final FileResolver fileResolver;
    private final ProjectPublicationRegistry publicationRegistry;
    private final ProjectConfigurationActionContainer configurationActionContainer;
    private final MavenSettingsProvider mavenSettingsProvider;
    private final LocalMavenRepositoryLocator mavenRepositoryLocator;

    private Project project;

    @Inject
    public MavenPluginCompat(FileResolver fileResolver,
                             ProjectPublicationRegistry publicationRegistry, ProjectConfigurationActionContainer configurationActionContainer,
                             MavenSettingsProvider mavenSettingsProvider, LocalMavenRepositoryLocator mavenRepositoryLocator) {
        this.fileResolver = fileResolver;
        this.publicationRegistry = publicationRegistry;
        this.configurationActionContainer = configurationActionContainer;
        this.mavenSettingsProvider = mavenSettingsProvider;
        this.mavenRepositoryLocator = mavenRepositoryLocator;
    }

    public void apply(final ProjectInternal project) {
        this.project = project;
        project.getPlugins().apply(BasePlugin.class);

        MavenFactory mavenFactory = getMavenFactory(project)
        MavenPluginConvention pluginConvention = getMavenPluginConvention(project, mavenFactory)
        //noinspection GroovyAssignabilityCheck
        DefaultDeployerFactory deployerFactory = null
        try {
            deployerFactory = new DefaultDeployerFactory(
                    mavenFactory,
                    //兼容2.10->2.14包名发生变化
                    { ->
                        //noinspection GroovyAssignabilityCheck
                        return project.getLogging();
                    },
                    fileResolver,
                    pluginConvention,
                    project.getConfigurations(),
                    pluginConvention.getConf2ScopeMappings(),
                    mavenSettingsProvider,
                    mavenRepositoryLocator);
        } catch (Exception e) {
            project.logger.info(e.getMessage())
        }
        if (deployerFactory == null) {
            deployerFactory = new DefaultDeployerFactory(
                    mavenFactory,
                    //兼容2.10->2.14包名发生变化
                    { ->
                        //noinspection GroovyAssignabilityCheck
                        return project.getLogging();
                    },
                    fileResolver,
                    pluginConvention,
                    project.getConfigurations(),
                    pluginConvention.getConf2ScopeMappings());
        }

        configureUploadTasks(deployerFactory);
        configureUploadArchivesTask();

        PluginContainer plugins = project.getPlugins();

        //android application不进行兼容，因为插件发布的特殊需求
        try {
            if (plugins.hasPlugin("com.android.application")) {
                //插件发布的需求，当application应用时不映射configuration
//                Class appPluginClass = Class.forName("com.android.build.gradle.AppPlugin");
//                plugins.withType(appPluginClass, new Action<Plugin>() {
//                    public void execute(Plugin appPlugin) {
//                        configureAndroidScopeMappings(project.getConfigurations(), pluginConvention.getConf2ScopeMappings());
//                    }
//                });
            }
        }
        catch (Exception ex) {
        }

        //兼容android lib aar发布
        try {
            if (plugins.hasPlugin("com.android.library")) {
                Class libraryPluginClass = Class.forName("com.android.build.gradle.LibraryPlugin");
                plugins.withType(libraryPluginClass, new Action<Plugin>() {
                    public void execute(Plugin libraryPlugin) {
                        configureAndroidScopeMappings(project.getConfigurations(), pluginConvention.getConf2ScopeMappings());
                    }
                });
            }
        }
        catch (Exception ex) {
        }

        //兼容android test
        try {
            if (plugins.hasPlugin("com.android.test")) {
                Class testPluginClass = Class.forName("com.android.build.gradle.TestPlugin");
                plugins.withType(testPluginClass, new Action<Plugin>() {
                    public void execute(Plugin testPlugin) {
                        configureAndroidScopeMappings(project.getConfigurations(), pluginConvention.getConf2ScopeMappings());
                    }
                });
            }
        }
        catch (Exception ex) {
        }

        plugins.withType(JavaPlugin.class, new Action<JavaPlugin>() {
            public void execute(JavaPlugin javaPlugin) {
                configureJavaScopeMappings(project.getConfigurations(), pluginConvention.getConf2ScopeMappings());
            }
        });

        //兼容android和java，将其从JavaPlugin移到JavaBasePlugin
        plugins.withType(JavaBasePlugin.class, new Action<JavaBasePlugin>() {
            public void execute(JavaBasePlugin javaPlugin) {
                configureInstall(project);
            }
        });

        plugins.withType(WarPlugin.class, new Action<WarPlugin>() {
            public void execute(WarPlugin warPlugin) {
                configureWarScopeMappings(project.getConfigurations(), pluginConvention.getConf2ScopeMappings());
            }
        });

        try {
            //兼容gradle 4.0新增JavaLibraryPlugin
            Class javaLibraryPluginClass = Class.forName("org.gradle.api.plugins.JavaLibraryPlugin");
            //noinspection Convert2Lambda,unchecked
            plugins.withType(javaLibraryPluginClass, new Action() {
                public void execute(Object javaLibraryPlugin) {
                    configureJavaLibraryScopeMappings(project.getConfigurations(), pluginConvention.getConf2ScopeMappings());
                }
            });
        } catch (Exception e) {
        }

    }

    /**
     * afterEvaluate后调用
     */
    static List<Map<String, String>> getMavenDependencies(Project project) {
        List<Map<String, String>> dependencyList = new ArrayList<>()
        def mavenFactory = getMavenFactory(project)
        def mavenPluginConvention = getMavenPluginConvention(project, mavenFactory)
        configureAndroidScopeMappings(project.getConfigurations(), mavenPluginConvention.getConf2ScopeMappings());
        configureAndroidProvidedScopeMappings(project.getConfigurations(), mavenPluginConvention.getConf2ScopeMappings());
        def pomDependenciesConverter = mavenFactory.createPomDependenciesConverter()
        def dependencies = pomDependenciesConverter.convert(mavenPluginConvention.getConf2ScopeMappings(), project.getConfigurations());
        dependencies.each {
            //it->org.apache.maven.model.Dependency
            Map<String, String> dependencyMap = new HashMap<>()
            dependencyMap.put("group", it.getGroupId())
            dependencyMap.put("artifactId", it.getArtifactId())
            dependencyMap.put("version", it.getVersion())
            dependencyMap.put("scope", it.getScope())
            dependencyList.add(dependencyMap)
        }
        return dependencyList
    }

    static MavenPluginConvention getMavenPluginConvention(ProjectInternal project, MavenFactory mavenFactory) {
        return addConventionObject(project, mavenFactory);
    }

    static MavenFactory getMavenFactory(ProjectInternal project) {
        MavenFactory mavenFactory = null
        try {
            mavenFactory = project.getServices().get(MavenFactory.class);
        } catch (Exception e) {
            project.logger.info(e.getMessage())
        }
        if (mavenFactory == null) {
            mavenFactory = Class.forName("org.gradle.api.publication.maven.internal.DefaultMavenFactory").newInstance()
        }
        mavenFactory
    }

    private void configureUploadTasks(final DefaultDeployerFactory deployerFactory) {
        project.getTasks().withType(Upload.class, new Action<Upload>() {
            public void execute(Upload upload) {
                RepositoryHandler repositories = upload.getRepositories();
                DefaultRepositoryHandler handler = (DefaultRepositoryHandler) repositories;
                DefaultMavenRepositoryHandlerConvention repositoryConvention = new DefaultMavenRepositoryHandlerConvention(handler, deployerFactory);
                new DslObject(repositories).getConvention().getPlugins().put("maven", repositoryConvention);
            }
        });
    }

    private void configureUploadArchivesTask() {
        configurationActionContainer.add(new Action<Project>() {
            public void execute(Project project) {
                Upload uploadArchives = project.getTasks().withType(Upload.class).findByName(BasePlugin.UPLOAD_ARCHIVES_TASK_NAME);
                if (uploadArchives == null) {
                    return;
                }

                ConfigurationInternal configuration = (ConfigurationInternal) uploadArchives.getConfiguration();
                //compat for <=2.14.1
                def module = configuration.getModule();
                for (MavenResolver resolver : uploadArchives.getRepositories().withType(MavenResolver.class)) {
                    MavenPom pom = resolver.getPom();
                    ModuleVersionIdentifier publicationId = new DefaultModuleVersionIdentifier(
                            pom.getGroupId().equals("unknown") ? module.getGroup() : pom.getGroupId(),
                            pom.getArtifactId().equals("empty-project") ? module.getName() : pom.getArtifactId(),
                            pom.getVersion().equals("0") ? module.getVersion() : pom.getVersion()
                    );
                    try {
                        publicationRegistry.registerPublication(project.getPath(), new DefaultProjectPublication(publicationId));
                    } catch (Exception e) {
                        //compat for gradle 4.6
                        //noinspection UnnecessaryQualifiedReference
                        publicationRegistry.registerPublication(project.getPath(), new DefaultProjectPublication(org.gradle.internal.Describables.withTypeAndName("Maven repository", resolver.getName()), publicationId, true))

                    }
                }
            }
        });
    }

    static MavenPluginConvention addConventionObject(ProjectInternal project, MavenFactory mavenFactory) {
        MavenPluginConvention mavenConvention = new MavenPluginConvention(project, mavenFactory);
        Convention convention = project.getConvention();
        convention.getPlugins().put("maven", mavenConvention);
        return mavenConvention;
    }

    static void configureJavaScopeMappings(ConfigurationContainer configurations, Conf2ScopeMappingContainer mavenScopeMappings) {
        mavenScopeMappings.addMapping(COMPILE_PRIORITY, configurations.getByName("compile"),
                Conf2ScopeMappingContainer.COMPILE);
        mavenScopeMappings.addMapping(RUNTIME_PRIORITY, configurations.getByName("runtime"),
                Conf2ScopeMappingContainer.RUNTIME);
        //兼容gradle 4.0
        try {
            mavenScopeMappings.addMapping(RUNTIME_PRIORITY + 1, configurations.getByName("implementation"),
                    Conf2ScopeMappingContainer.RUNTIME);
        } catch (Exception e) {

        }

        mavenScopeMappings.addMapping(TEST_COMPILE_PRIORITY, configurations.getByName("testCompile"),
                Conf2ScopeMappingContainer.TEST);
        mavenScopeMappings.addMapping(TEST_RUNTIME_PRIORITY, configurations.getByName("testRuntime"),
                Conf2ScopeMappingContainer.TEST);

        //兼容gradle 4.0
        try {
            mavenScopeMappings.addMapping(TEST_RUNTIME_PRIORITY, configurations.getByName("testImplementation"),
                    Conf2ScopeMappingContainer.TEST);
        } catch (Exception e) {

        }

    }

    static void configureJavaLibraryScopeMappings(ConfigurationContainer configurations, Conf2ScopeMappingContainer mavenScopeMappings) {
        //兼容gradle 4.0
        try {
            mavenScopeMappings.addMapping(COMPILE_PRIORITY + 1, configurations.getByName("api"),
                    Conf2ScopeMappingContainer.COMPILE);
        } catch (Exception e) {

        }

    }

    static void configureWarScopeMappings(ConfigurationContainer configurations, Conf2ScopeMappingContainer mavenScopeMappings) {
        mavenScopeMappings.addMapping(PROVIDED_COMPILE_PRIORITY, configurations.getByName("providedCompile"),
                Conf2ScopeMappingContainer.PROVIDED);
        mavenScopeMappings.addMapping(PROVIDED_RUNTIME_PRIORITY, configurations.getByName("providedRuntime"),
                Conf2ScopeMappingContainer.PROVIDED);
    }

    static void configureInstall(Project project) {
        Upload installUpload = project.getTasks().create(INSTALL_TASK_NAME, Upload.class);
        Configuration configuration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION);
        installUpload.setConfiguration(configuration);
        MavenRepositoryHandlerConvention repositories = new DslObject(installUpload.getRepositories()).getConvention().getPlugin(MavenRepositoryHandlerConvention.class);
        repositories.mavenInstaller();
        installUpload.setDescription("Installs the 'archives' artifacts into the local Maven repository.");
    }

    static void configureAndroidScopeMappings(ConfigurationContainer configurations, Conf2ScopeMappingContainer mavenScopeMappings) {
        mavenScopeMappings.addMapping(COMPILE_PRIORITY, configurations.getByName("compile"),
                Conf2ScopeMappingContainer.COMPILE);

        //兼容gradle 4.0 和android gradle plugin 3.0.0
        try {
            mavenScopeMappings.addMapping(COMPILE_PRIORITY + 1, configurations.getByName("api"),
                    Conf2ScopeMappingContainer.COMPILE);
        } catch (Exception e) {
        }

        //兼容gradle 4.0 和android gradle plugin 3.0.0
        try {
            mavenScopeMappings.addMapping(RUNTIME_PRIORITY, configurations.getByName("implementation"),
                    Conf2ScopeMappingContainer.RUNTIME);
        } catch (Exception e) {
        }

        mavenScopeMappings.addMapping(TEST_COMPILE_PRIORITY, configurations.getByName("testCompile"),
                Conf2ScopeMappingContainer.TEST);

        //兼容gradle 4.0 和android gradle plugin 3.0.0
        try {
            mavenScopeMappings.addMapping(TEST_RUNTIME_PRIORITY, configurations.getByName("testImplementation"),
                    Conf2ScopeMappingContainer.TEST);
        } catch (Exception e) {
        }

    }

    static void configureAndroidProvidedScopeMappings(ConfigurationContainer configurations, Conf2ScopeMappingContainer mavenScopeMappings) {
        try {
            mavenScopeMappings.addMapping(PROVIDED_COMPILE_PRIORITY, configurations.getByName("provided"),
                    Conf2ScopeMappingContainer.PROVIDED);
        } catch (Exception e) {
        }

        try {
            mavenScopeMappings.addMapping(PROVIDED_COMPILE_PRIORITY + 1, configurations.getByName("compileOnly"),
                    Conf2ScopeMappingContainer.PROVIDED);
        } catch (Exception e) {
        }

        try {
            mavenScopeMappings.addMapping(PROVIDED_COMPILE_PRIORITY + 2, configurations.getByName("providedAar"),
                    Conf2ScopeMappingContainer.PROVIDED);
        } catch (Exception e) {
        }

        try {
            mavenScopeMappings.addMapping(PROVIDED_COMPILE_PRIORITY + 3, configurations.getByName("bundleApiProvided"),
                    Conf2ScopeMappingContainer.PROVIDED);
        } catch (Exception e) {
        }

    }
}

