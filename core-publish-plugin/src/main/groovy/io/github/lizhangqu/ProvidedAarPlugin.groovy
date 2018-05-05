package io.github.lizhangqu

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import java.lang.reflect.Field

/**
 * provided aar
 */
class ProvidedAarPlugin implements Plugin<Project> {
    Project project

    @Override
    void apply(Project project) {
        this.project = project
        providedAarCompat()
    }

    /**
     * 导出获得android gradle plugin插件的版本号，build.gradle中apply后可直接使用getAndroidGradlePluginVersionCompat()
     */
    @SuppressWarnings("GrMethodMayBeStatic")
    String getAndroidGradlePluginVersionCompat() {
        String version = null
        try {
            Class versionModel = Class.forName("com.android.builder.model.Version")
            def versionFiled = versionModel.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
            versionFiled.setAccessible(true)
            version = versionFiled.get(null)
        } catch (Exception e) {
            version = "unknown"
        }
        return version
    }

    /**
     * 在application 插件中开启providedAar功能
     */
    @SuppressWarnings("UnnecessaryQualifiedReference")
    void providedAarCompat() {
        if (!project.getPlugins().hasPlugin("com.android.application")) {
            return
        }
        if (project.getConfigurations().findByName("providedAar") != null) {
            return
        }
        Configuration providedConfiguration = project.getConfigurations().findByName("provided")
        Configuration compileOnlyConfiguration = project.getConfigurations().findByName("compileOnly")
        if (providedConfiguration == null && compileOnlyConfiguration == null) {
            return
        }
        Configuration providedAarConfiguration = project.getConfigurations().create("providedAar")
        String androidGradlePluginVersion = getAndroidGradlePluginVersionCompat()
        if (androidGradlePluginVersion.startsWith("1.3") || androidGradlePluginVersion.startsWith("1.5") || androidGradlePluginVersion.startsWith("2.") || androidGradlePluginVersion.startsWith("3.")) {
            //大于等于1.3.0的版本让provided继承providedAar，低于1.3.0的版本，手动提取aar中的jar添加依赖
            if (compileOnlyConfiguration != null) {
                compileOnlyConfiguration.extendsFrom(providedAarConfiguration)
            } else {
                providedConfiguration.extendsFrom(providedAarConfiguration)
            }
        }
        if (androidGradlePluginVersion.startsWith("0.") || androidGradlePluginVersion.startsWith("1.0") || androidGradlePluginVersion.startsWith("1.1") || androidGradlePluginVersion.startsWith("1.2")) {
            //不支持小于1.3.0的版本，不含1.3.0
            throw new GradleException("Not support version ${androidGradlePluginVersion}, android gradle plugin must >=1.3.0")
        } else {
            def android = project.getExtensions().getByName("android")
            android.applicationVariants.all { def variant ->
                if (androidGradlePluginVersion.startsWith("1.3") || androidGradlePluginVersion.startsWith("1.5") || androidGradlePluginVersion.startsWith("2.0") || androidGradlePluginVersion.startsWith("2.1") || androidGradlePluginVersion.startsWith("2.2") || androidGradlePluginVersion.startsWith("2.3") || androidGradlePluginVersion.startsWith("2.4")) {
                    //支持1.3.0+ ~ 2.4.0+，且低于2.5.0，支持传递依赖
                    def prepareDependenciesTask = project.tasks.findByName("prepare${variant.getName().capitalize()}Dependencies")
                    if (prepareDependenciesTask) {
                        def removeSyncIssues = {
                            try {
                                Class prepareDependenciesTaskClass = Class.forName("com.android.build.gradle.internal.tasks.PrepareDependenciesTask")
                                Field checkersField = prepareDependenciesTaskClass.getDeclaredField('checkers')
                                checkersField.setAccessible(true)
                                def checkers = checkersField.get(prepareDependenciesTask)
                                checkers.iterator().with { checkersIterator ->
                                    checkersIterator.each { dependencyChecker ->
                                        def syncIssues = dependencyChecker.syncIssues
                                        syncIssues.iterator().with { syncIssuesIterator ->
                                            syncIssuesIterator.each { syncIssue ->
                                                if (syncIssue.getType() == 7 && syncIssue.getSeverity() == 2) {
                                                    project.logger.info "[providedAar] WARNING: providedAar has been enabled in com.android.application you can ignore ${syncIssue}"
                                                    syncIssuesIterator.remove()
                                                }
                                            }
                                        }

                                        //兼容1.3.0~2.1.3版本，为了将provided的aar不参与打包，将isOptional设为true
                                        if (androidGradlePluginVersion.startsWith("1.3") || androidGradlePluginVersion.startsWith("1.5") || androidGradlePluginVersion.startsWith("2.0") || androidGradlePluginVersion.startsWith("2.1")) {
                                            def configurationDependencies = dependencyChecker.configurationDependencies
                                            List libraries = configurationDependencies.libraries
                                            libraries.each { library ->
                                                providedAarConfiguration.dependencies.each { providedDependency ->
                                                    String libName = library.getName()
                                                    if (libName.contains(providedDependency.group) && libName.contains(providedDependency.name) && libName.contains(providedDependency.version)) {
                                                        Field isOptionalField = library.getClass().getDeclaredField("isOptional")
                                                        Field modifiersField = Field.class.getDeclaredField("modifiers")
                                                        modifiersField.setAccessible(true)
                                                        modifiersField.setInt(isOptionalField, isOptionalField.getModifiers() & ~java.lang.reflect.Modifier.FINAL)
                                                        isOptionalField.setAccessible(true)
                                                        isOptionalField.setBoolean(library, true)
                                                        //为了递归调用可以引用，先声明再赋值
                                                        def fixDependencies = null
                                                        fixDependencies = { dependencies ->
                                                            dependencies.each { dependency ->
                                                                if (dependency.getClass() == library.getClass()) {
                                                                    isOptionalField.setBoolean(dependency, true)
                                                                    fixDependencies(dependency.dependencies)
                                                                }
                                                            }
                                                        }
                                                        fixDependencies(library.dependencies)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace()
                            }
                        }
                        if (androidGradlePluginVersion.startsWith("1.3") || androidGradlePluginVersion.startsWith("1.5") || androidGradlePluginVersion.startsWith("2.0") || androidGradlePluginVersion.startsWith("2.1")) {
                            //这里不处理as sync的时候会出错
                            def appPlugin = project.getPlugins().findPlugin("com.android.application")
                            def taskManager = appPlugin.getMetaClass().getProperty(appPlugin, "taskManager")
                            def dependencyManager = taskManager.getClass().getSuperclass().getMetaClass().getProperty(taskManager, "dependencyManager")
                            def extraModelInfo = dependencyManager.getMetaClass().getProperty(dependencyManager, "extraModelInfo")
                            Map<?, ?> syncIssues = extraModelInfo.getSyncIssues()
                            syncIssues.iterator().with { syncIssuesIterator ->
                                syncIssuesIterator.each { syncIssuePair ->
                                    if (syncIssuePair.getValue().getType() == 7 && syncIssuePair.getValue().getSeverity() == 2) {
                                        syncIssuesIterator.remove()
                                    }
                                }
                            }
                            //下面同2.2.0+处理
                            prepareDependenciesTask.configure removeSyncIssues
                        } else if (androidGradlePluginVersion.startsWith("2.2") || androidGradlePluginVersion.startsWith("2.3")) {
                            prepareDependenciesTask.configure removeSyncIssues
                        } else if (androidGradlePluginVersion.startsWith("2.4")) {
                            prepareDependenciesTask.doFirst removeSyncIssues
                        }
                    }
                } else if (androidGradlePluginVersion.startsWith("2.5") || androidGradlePluginVersion.startsWith("3.")) {
                    //支持2.5.0+ ~ 3.2.0+，支持传递依赖
                    def prepareBuildTask = project.tasks.findByName("pre${variant.getName().capitalize()}Build")
                    if (prepareBuildTask) {
                        boolean needRedirectAction = false
                        prepareBuildTask.actions.iterator().with { actionsIterator ->
                            actionsIterator.each { action ->
                                if (action.getActionClassName().contains("AppPreBuildTask")) {
                                    actionsIterator.remove()
                                    needRedirectAction = true
                                }
                            }
                        }
                        if (needRedirectAction) {
                            prepareBuildTask.doLast {
                                def compileManifests = null
                                def runtimeManifests = null
                                Class appPreBuildTaskClass = Class.forName("com.android.build.gradle.internal.tasks.AppPreBuildTask")
                                try {
                                    //3.0.0+
                                    Field compileManifestsField = appPreBuildTaskClass.getDeclaredField("compileManifests")
                                    Field runtimeManifestsField = appPreBuildTaskClass.getDeclaredField("runtimeManifests")
                                    compileManifestsField.setAccessible(true)
                                    runtimeManifestsField.setAccessible(true)
                                    compileManifests = compileManifestsField.get(prepareBuildTask)
                                    runtimeManifests = runtimeManifestsField.get(prepareBuildTask)
                                } catch (Exception e) {
                                    try {
                                        //2.5.0+
                                        Field variantScopeField = appPreBuildTaskClass.getDeclaredField("variantScope")
                                        variantScopeField.setAccessible(true)
                                        def variantScope = variantScopeField.get(prepareBuildTask)
                                        //noinspection UnnecessaryQualifiedReference
                                        compileManifests = variantScope.getArtifactCollection(com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.COMPILE_CLASSPATH, com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL, com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST)
                                        runtimeManifests = variantScope.getArtifactCollection(com.android.build.gradle.internal.publishing.AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH, com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactScope.ALL, com.android.build.gradle.internal.publishing.AndroidArtifacts.ArtifactType.MANIFEST)
                                    } catch (Exception e1) {
                                    }
                                }
                                try {
                                    Set<ResolvedArtifactResult> compileArtifacts = compileManifests.getArtifacts()
                                    Set<ResolvedArtifactResult> runtimeArtifacts = runtimeManifests.getArtifacts()

                                    Map<String, String> runtimeIds = new HashMap<>(runtimeArtifacts.size())

                                    def handleArtifact = { id, consumer ->
                                        if (id instanceof ProjectComponentIdentifier) {
                                            consumer(((ProjectComponentIdentifier) id).getProjectPath().intern(), "")
                                        } else if (id instanceof ModuleComponentIdentifier) {
                                            ModuleComponentIdentifier moduleComponentId = (ModuleComponentIdentifier) id
                                            consumer(
                                                    moduleComponentId.getGroup() + ":" + moduleComponentId.getModule(),
                                                    moduleComponentId.getVersion())
                                        } else {
                                            project.getLogger()
                                                    .warn(
                                                    "Unknown ComponentIdentifier type: "
                                                            + id.getClass().getCanonicalName())
                                        }
                                    }

                                    runtimeArtifacts.each { def artifact ->
                                        def runtimeId = artifact.getId().getComponentIdentifier()
                                        def putMap = { def key, def value ->
                                            runtimeIds.put(key, value)
                                        }
                                        handleArtifact(runtimeId, putMap)
                                    }

                                    compileArtifacts.each { def artifact ->
                                        final ComponentIdentifier compileId = artifact.getId().getComponentIdentifier()
                                        def checkCompile = { def key, def value ->
                                            String runtimeVersion = runtimeIds.get(key)
                                            if (runtimeVersion == null) {
                                                String display = compileId.getDisplayName()
                                                project.logger.info(
                                                        "[providedAar] WARNING: providedAar has been enabled in com.android.application you can ignore 'Android dependency '"
                                                                + display
                                                                + "' is set to compileOnly/provided which is not supported'")
                                            } else if (!runtimeVersion.isEmpty()) {
                                                // compare versions.
                                                if (!runtimeVersion.equals(value)) {
                                                    throw new RuntimeException(
                                                            String.format(
                                                                    "Android dependency '%s' has different version for the compile (%s) and runtime (%s) classpath. You should manually set the same version via DependencyResolution",
                                                                    key, value, runtimeVersion));
                                                }
                                            }
                                        }
                                        handleArtifact(compileId, checkCompile)
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
            }
        }

        //redirect warning log to info log
        def listenerBackedLoggerContext = project.getLogger().getMetaClass().getProperty(project.getLogger(), "context")
        def originalOutputEventListener = listenerBackedLoggerContext.getOutputEventListener()
        def originalOutputEventLevel = listenerBackedLoggerContext.getLevel()
        listenerBackedLoggerContext.setOutputEventListener({ def outputEvent ->
            def logLevel = originalOutputEventLevel.name()
            if (!("QUIET".equalsIgnoreCase(logLevel) || "ERROR".equalsIgnoreCase(logLevel))) {
                if ("WARN".equalsIgnoreCase(outputEvent.getLogLevel().name())) {
                    String message = outputEvent.getMessage()
                    //Provided dependencies can only be jars.
                    //provided dependencies can only be jars.
                    if (message != null && (message.contains("Provided dependencies can only be jars.") || message.contains("provided dependencies can only be jars. "))) {
                        project.logger.info(message)
                        return
                    }
                }
            }
            if (originalOutputEventListener != null) {
                originalOutputEventListener.onOutput(outputEvent)
            }
        })
    }

}
