package io.github.lizhangqu

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.jfrog.bintray.gradle.BintrayPlugin
import org.gradle.api.*
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.plugins.ProjectReportsPlugin
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.reporting.dependencies.HtmlDependencyReportTask
import org.gradle.api.reporting.dependencies.internal.HtmlDependencyReporter
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.util.GFileUtils
import io.github.lizhangqu.release.ReleasePlugin

import java.text.SimpleDateFormat
import java.util.regex.Matcher

/**
 * maven发布插件和bintray发布插件
 */
class PublishPlugin extends BasePropertiesPlugin {

    public static final String LOG_PREFIX = "[PublishPlugin]"

    @Override
    void apply(Project project) {
        super.apply(project)
        //checkTask
        checkTask(project)
        //createExtension
        createExtension(project)
        //addExtension
        addExtension(project)
        //compatible for java 8
        compatibleJava8(project)
        //force Java/JavaDoc encode with UTF-8
        utf8WithJavaCompile(project)
        //add plugin
        applyThirdPlugin(project)
        //javaDoc javaSource
        configJavaDocAndJavaSource(project)
        //install task
        configInstall(project)
        //uploadArchives task
        configUploadArchives(project)
        //publishing
        configPublishing(project)
        //bintrayUpload task
        configBintray(project)
        //task dependency
        configTaskDependency(project)
        //release plugin
        configReleasePlugin(project)
        //uploadRelease uploadSnapshot
        configTask(project)
        //add prefix task
        addPrefixTask(project)

    }

    @SuppressWarnings("GrMethodMayBeStatic")
    def checkTask(Project project) {
        def taskNames = project.gradle.startParameter.getTaskNames()
        if (taskNames != null && taskNames.contains("uploadArchives")) {
            throw new GradleException("please use uploadSnapshot or uploadRelease instead. uploadArchives is forbidden")
        }
        if (taskNames != null && taskNames.contains("bintrayUpload")) {
            throw new GradleException("please use uploadBintray instead. bintrayUpload is forbidden")
        }
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    def createExtension(Project project) {
        project.getExtensions().create("pom", PublishPluginExtension.class, project)
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    def addExtension(Project project) {
        if (getEnableCoordinate(project) == 'true') {
            def pomGroupId = this.getPomGroupId(project)
            def pomArtifactId = this.getPomArtifactId(project)
            def pomVersion = this.getPomVersion(project)

            project.group = pomGroupId
            try {
                project.archivesBaseName = pomArtifactId
            } catch (MissingPropertyException e) {
            }

            project.version = pomVersion
        }
    }

    def compatibleJava8(Project project) {
        if (JavaVersion.current().isJava8Compatible()) {
            project.getTasks().withType(Javadoc) {
                options.addStringOption('Xdoclint:none', '-quiet')
            }
        }
    }

    def utf8WithJavaCompile(Project project) {
        project.getTasks().withType(JavaCompile) {
            options.encoding = "UTF-8"
        }

        project.getTasks().withType(Javadoc) {
            options.encoding = "UTF-8"
        }
    }

    def applyThirdPlugin(Project project) {
        applyPluginIfNotApply(project, DependencyRulePlugin.class)
        applyPluginIfNotApply(project, ProjectReportsPlugin.class)
        applyPluginIfNotApply(project, MavenPublishPlugin.class)
        applyPluginIfNotApply(project, BintrayPlugin.class)
        applyPluginIfNotApply(project, MavenPluginCompat.class)
        applyPluginIfNotApply(project, ReleasePlugin.class)
    }


    def configJavaDocAndJavaSource(Project project) {
        project.afterEvaluate {
            // Android libraries
            if (project.hasProperty("android")) {

                def androidJavadocs = project.task(type: Javadoc, 'androidJavadocs') {
                    source = project.android.sourceSets.main.java.srcDirs
                    classpath += project.configurations.compile
                    classpath += project.files("${project.android.getBootClasspath().join(File.pathSeparator)}")
                    failOnError false
                }

                def androidJavaDocsJar = project.task(type: Jar, dependsOn: androidJavadocs, 'androidJavaDocsJar') {
                    classifier = 'javadoc'
                    from project.androidJavadocs.getDestinationDir()
                }

                def androidSourcesJar = project.task(type: Jar, 'androidSourcesJar') {
                    classifier = 'sources'
                    from project.android.sourceSets.main.java.srcDirs
                }

                project.artifacts {
                    archives androidSourcesJar

                    if (getEnableJavadoc(project) == 'true') {
                        archives androidJavaDocsJar
                    }

                }

            } else {

                try {
                    def javaSourcesJar = project.task(type: Jar, dependsOn: project.classes, 'javaSourcesJar') {
                        classifier = 'sources'
                        from project.sourceSets.main.allSource
                    }

                    def javaDocJar = project.task(type: Jar, dependsOn: project.javadoc, 'javaDocJar') {
                        classifier = 'javadoc'
                        from project.javadoc.getDestinationDir()
                    }

                    project.artifacts {
                        archives javaSourcesJar
                        if (getEnableJavadoc(project) == 'true') {
                            archives javaDocJar
                        }
                    }
                } catch (Exception e) {
                    project.logger.error("Maybe Not Java Project, Just Ignore It.")
                }

            }
        }
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    def configInstall(Project project) {
        try {
            project.install {
                repositories {
                    mavenInstaller {
                        pom.project {
                            def pomGroupId = this.getPomGroupId(project)
                            def pomArtifactId = this.getPomArtifactId(project)
                            def pomVersion = this.getPomVersion(project)

                            // This generates POM.xml with proper parameters
                            groupId = pomGroupId
                            artifactId = pomArtifactId
                            version = pomVersion

                            name pomArtifactId
                            description this.getBuildDescription(project)

                            def pomWebsiteUrl = this.getPomWebsiteUrl(project)
                            if (pomWebsiteUrl) {
                                url pomWebsiteUrl
                            }

                            def pomVcsUrl = this.getPomVcsUrl(project)
                            if (pomVcsUrl) {
                                scm {
                                    url pomVcsUrl
                                    connection pomVcsUrl
                                    developerConnection pomVcsUrl
                                }
                            }

                            def pomLicense = this.getPomLicense(project)
                            def pomLicenseUrl = this.getPomLicenseUrl(project)
                            if (pomLicense && pomLicenseUrl) {
                                licenses {
                                    license {
                                        name pomLicense
                                        url pomLicenseUrl
                                    }
                                }
                            }

                            def pomDeveloperId = this.getPomDeveloperId(project)
                            def pomDeveloperName = this.getPomDeveloperName(project)
                            def pomDeveloperEmail = this.getPomDeveloperEmail(project)
                            if (pomDeveloperId || pomDeveloperName || pomDeveloperEmail) {
                                developers {
                                    developer {
                                        if (pomDeveloperId) {
                                            id pomDeveloperId
                                        }
                                        if (pomDeveloperName) {
                                            name pomDeveloperName
                                        }
                                        if (pomDeveloperEmail) {
                                            email pomDeveloperEmail
                                        }
                                    }
                                }
                            }
                        }
                        pom {
                            whenConfigured { p ->
                                p.dependencies = p.dependencies.findAll { dependency ->
                                    PublishPluginExtension publishPluginExtension = project.getExtensions().findByType(PublishPluginExtension.class)
                                    return !publishPluginExtension.shouldExcludeDependency(dependency.groupId, dependency.artifactId, dependency.version)
                                }
                                p.dependencies.each { dependency ->
                                    PublishPluginExtension publishPluginExtension = project.getExtensions().findByType(PublishPluginExtension.class)
                                    def force = publishPluginExtension.shouldForceDependency(dependency.groupId, dependency.artifactId, dependency.version)
                                    if (force != null && force.version != null && force.version.length() > 0) {
                                        dependency.version = force.version
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            String msg = e.getMessage()
            if (msg?.contains("Could not find method install()")) {
                project.logger.error("Can't Find `install` Method, Just Ignore it.")
                return
            }
            throw e
        }
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    def configUploadArchives(Project project) {
        project.uploadArchives {
            repositories {
                mavenDeployer {
                    def pomGroupId = this.getPomGroupId(project)
                    def pomArtifactId = this.getPomArtifactId(project)
                    def pomVersion = this.getPomVersion(project)

                    pom.groupId = pomGroupId
                    pom.artifactId = pomArtifactId
                    pom.version = pomVersion

                    def releaseRepositoryUrl = this.getReleaseRepositoryUrl(project)
                    if (releaseRepositoryUrl) {
                        repository(url: releaseRepositoryUrl) {
                            def releaseRepositoryUsername = this.getReleaseRepositoryUsername(project)
                            def releaseRepositoryPassword = this.getReleaseRepositoryPassword(project)
                            if (releaseRepositoryUsername && releaseRepositoryPassword) {
                                authentication(userName: releaseRepositoryUsername, password: releaseRepositoryPassword)
                            }
                        }
                    }

                    def snapshotRepositoryUrl = this.getSnapshotRepositoryUrl(project)
                    if (snapshotRepositoryUrl) {
                        snapshotRepository(url: snapshotRepositoryUrl) {
                            def snapshotRepositoryUsername = this.getSnapshotRepositoryUsername(project)
                            def snapshotRepositoryPassword = this.getSnapshotRepositoryPassword(project)
                            if (snapshotRepositoryUsername && snapshotRepositoryPassword) {
                                authentication(userName: snapshotRepositoryUsername, password: snapshotRepositoryPassword)
                            }
                        }
                    }

                    pom.project {
                        name pomArtifactId
                        description this.getBuildDescription(project)

                        def pomWebsiteUrl = this.getPomWebsiteUrl(project)
                        if (pomWebsiteUrl) {
                            url pomWebsiteUrl
                        }

                        def pomVcsUrl = this.getPomVcsUrl(project)
                        if (pomVcsUrl) {
                            scm {
                                url pomVcsUrl
                                connection pomVcsUrl
                                developerConnection pomVcsUrl
                            }
                        }

                        def pomLicense = this.getPomLicense(project)
                        def pomLicenseUrl = this.getPomLicenseUrl(project)
                        if (pomLicense && pomLicenseUrl) {
                            licenses {
                                license {
                                    name pomLicense
                                    url pomLicenseUrl
                                }
                            }
                        }

                        def pomDeveloperId = this.getPomDeveloperId(project)
                        def pomDeveloperName = this.getPomDeveloperName(project)
                        def pomDeveloperEmail = this.getPomDeveloperEmail(project)
                        if (pomDeveloperId || pomDeveloperName || pomDeveloperEmail) {
                            developers {
                                developer {
                                    if (pomDeveloperId) {
                                        id pomDeveloperId
                                    }
                                    if (pomDeveloperName) {
                                        name pomDeveloperName
                                    }
                                    if (pomDeveloperEmail) {
                                        email pomDeveloperEmail
                                    }
                                }
                            }
                        }
                    }

                }
                mavenDeployer.pom.whenConfigured { p ->
                    p.dependencies = p.dependencies.findAll { dependency ->
                        PublishPluginExtension publishPluginExtension = project.getExtensions().findByType(PublishPluginExtension.class)
                        return !publishPluginExtension.shouldExcludeDependency(dependency.groupId, dependency.artifactId, dependency.version)
                    }
                    p.dependencies.each { dependency ->
                        PublishPluginExtension publishPluginExtension = project.getExtensions().findByType(PublishPluginExtension.class)
                        def force = publishPluginExtension.shouldForceDependency(dependency.groupId, dependency.artifactId, dependency.version)
                        if (force != null && force.version != null && force.version.length() > 0) {
                            dependency.version = force.version
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    def configPublishing(Project project) {
        project.publishing {
            repositories {
                if (this.isReleaseBuild(project)) {
                    def releaseRepositoryUrl = this.getReleaseRepositoryUrl(project)
                    if (releaseRepositoryUrl) {
                        maven {
                            url releaseRepositoryUrl
                            def releaseRepositoryUsername = this.getReleaseRepositoryUsername(project)
                            def releaseRepositoryPassword = this.getReleaseRepositoryPassword(project)
                            if (releaseRepositoryUsername && releaseRepositoryPassword) {
                                credentials {
                                    username = releaseRepositoryUsername
                                    password = releaseRepositoryPassword
                                }
                            }
                        }
                    } else {
                        mavenLocal()
                    }
                } else {
                    def snapshotRepositoryUrl = this.getSnapshotRepositoryUrl(project)
                    if (snapshotRepositoryUrl) {
                        maven {
                            url snapshotRepositoryUrl
                            def snapshotRepositoryUsername = this.getSnapshotRepositoryUsername(project)
                            def snapshotRepositoryPassword = this.getSnapshotRepositoryPassword(project)
                            if (snapshotRepositoryUsername && snapshotRepositoryPassword) {
                                credentials {
                                    username = snapshotRepositoryUsername
                                    password = snapshotRepositoryPassword
                                }
                            }
                        }
                    } else {
                        mavenLocal()
                    }
                }
            }

            publications {
                maven(MavenPublication) {
                    def pomGroupId = this.getPomGroupId(project)
                    def pomArtifactId = this.getPomArtifactId(project)
                    def pomVersion = this.getPomVersion(project)

                    groupId pomGroupId
                    artifactId pomArtifactId
                    version pomVersion

                    //then need to config artifact
                    //like this in build.gradle
                    //publishing {
                    //    publications {
                    //        maven(MavenPublication) {
                    //            artifact "${project.buildDir}/outputs/apk/${project.name}-debug.ap"
                    //        }
                    //    }
                    //}

                }
            }
        }
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    def configBintray(Project project) {
        def bintrayUser = this.getBintrayUser(project)
        def bintrayKey = this.getBintrayKey(project)
        if (bintrayUser && bintrayKey) {
            project.bintray {
                user = bintrayUser
                key = bintrayKey
                configurations = ['archives']

                def pomGroupId = this.getPomGroupId(project)
                def pomArtifactId = this.getPomArtifactId(project)
                def pomVersion = this.getPomVersion(project)

                project.group = pomGroupId
                project.archivesBaseName = pomArtifactId
                project.version = pomVersion

                pkg {
                    repo = 'maven'
                    name = "${pomGroupId}:${pomArtifactId}"
                    def bintrayOrganization = this.getBintrayOrganization(project)
                    if (bintrayOrganization) {
                        userOrg = bintrayOrganization
                    }
                    def pomDescription = this.getPomDescription(project)
                    if (pomDescription) {
                        desc = pomDescription
                    }
                    def pomLicense = this.getPomLicense(project)
                    if (pomLicense) {
                        licenses = ["${pomLicense}"]
                    }
                    def pomWebsiteUrl = this.getPomWebsiteUrl(project)
                    if (pomWebsiteUrl) {
                        websiteUrl = pomWebsiteUrl
                    }
                    def pomVcsUrl = this.getPomVcsUrl(project)
                    if (pomVcsUrl) {
                        vcsUrl = pomVcsUrl
                    }
                    def pomIssueUrl = this.getPomIssueUrl(project)
                    if (pomIssueUrl) {
                        issueTrackerUrl = pomIssueUrl
                    }
                    publicDownloadNumbers = true
                    publish = true
                }
            }
        }
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    def configTaskDependency(Project project) {
        project.afterEvaluate {

            def bintrayUser = this.getBintrayUser(project)
            def bintrayKey = this.getBintrayKey(project)


            def installTask = project.tasks.findByName("install")
            def uploadArchivesTask = project.tasks.findByName("uploadArchives")
            def bintrayUploadTask = project.tasks.findByName("bintrayUpload")

            if (installTask) {
                //reset install task group form other to upload
                installTask.group = 'upload'
            }
            if (uploadArchivesTask) {
                //reset uploadArchives task group form other to upload
                uploadArchivesTask.group = 'upload'
            }

            if (uploadArchivesTask) {
                File dependenciesJsonFile = new File(project.buildDir, "dependencies.json")
                File dependenciesTxtFile = new File(project.buildDir, "dependencies.txt")
                File mavenJsonFile = new File(project.buildDir, "maven.json")
                HtmlDependencyReportTask htmlDependencyReportTask = project.task("componentDependencyJSON", type: HtmlDependencyReportTask)
                HtmlDependencyReporter reporter
                try {
                    reporter = new HtmlDependencyReporter(htmlDependencyReportTask.getVersionSelectorScheme(), htmlDependencyReportTask.getVersionComparator())
                } catch (Exception e) {
                    reporter = new HtmlDependencyReporter(htmlDependencyReportTask.getVersionSelectorScheme(), htmlDependencyReportTask.getVersionComparator(), htmlDependencyReportTask.getVersionParser())
                }

                DependencyReportTask dependencyReportTask = project.task("componentDependency", type: DependencyReportTask)
                dependencyReportTask.doLast {
                    Gson gson = new GsonBuilder().setPrettyPrinting().create()
                    String render = reporter.renderer.render(project)
                    Map dependenciesMap = gson.fromJson(render, Map.class)

                    GFileUtils.deleteQuietly(dependenciesJsonFile)
                    GFileUtils.writeFile(gson.toJson(dependenciesMap), dependenciesJsonFile)
                    project.logger.lifecycle "See the report at: file://${dependenciesJsonFile}"
                }
                Set<Configuration> configurations = project.getConfigurations().findAll { Configuration configuration ->
                    return true
                }
                dependencyReportTask.setConfigurations(configurations)
                dependencyReportTask.setOutputFile(dependenciesTxtFile)
                uploadArchivesTask.dependsOn dependencyReportTask

                if (installTask) {
                    uploadArchivesTask.dependsOn installTask
                    installTask.finalizedBy dependencyReportTask
                }

                uploadArchivesTask.doLast {
                    File pomDir = new File(project.buildDir, "poms")
                    List<Map<String, String>> poms = new ArrayList<>()
                    pomDir.eachFileRecurse { File pomFile ->
                        def pomNodes = new XmlParser().parse(pomFile)
                        Map<String, String> mavenMap = new HashMap<>()
                        mavenMap.put("group", pomNodes.get("groupId").text())
                        mavenMap.put("artifactId", pomNodes.get("artifactId").text())
                        mavenMap.put("version", pomNodes.get("version").text())

                        List<Map<String, String>> dependencies = new ArrayList<>()
                        mavenMap.put("dependencies", dependencies)
                        def dependenciesNode = pomNodes.get("dependencies")
                        dependenciesNode.each {
                            it.each {
                                Map<String, String> dependencyMap = new HashMap<>()
                                dependencyMap.put("group", it.get("groupId").text())
                                dependencyMap.put("artifactId", it.get("artifactId").text())
                                dependencyMap.put("version", it.get("version").text())
                                dependencyMap.put("scope", it.get("scope").text())
                                dependencies.add(dependencyMap)
                            }
                        }
                        if (dependencies.size() == 0 && project.getPlugins().hasPlugin("com.android.application")) {
                            dependencies.addAll(MavenPluginCompat.getMavenDependencies(project))
                        }
                        poms.add(mavenMap)
                    }

                    Gson gson = new GsonBuilder().setPrettyPrinting().create()
                    GFileUtils.deleteQuietly(mavenJsonFile)
                    GFileUtils.writeFile(gson.toJson(poms), mavenJsonFile)


                    poms.each { Map<String, String> item ->
                        String group = item.get("group")
                        String artifactId = item.get("artifactId")

                        File reportDir = new File(project.rootProject.buildDir, "report/${group}/${artifactId}")
                        GFileUtils.mkdirs(reportDir)

                        GFileUtils.copyFile(dependenciesJsonFile, new File(reportDir, dependenciesJsonFile.getName()))
                        GFileUtils.copyFile(dependenciesTxtFile, new File(reportDir, dependenciesTxtFile.getName()))
                        GFileUtils.copyFile(mavenJsonFile, new File(reportDir, mavenJsonFile.getName()))

                        File bundleJsonFile = new File(project.buildDir, "bundle.json")
                        if (bundleJsonFile.exists() && bundleJsonFile.isFile()) {
                            GFileUtils.copyFile(bundleJsonFile, new File(reportDir, bundleJsonFile.getName()))
                        }
                    }
                }
            }

            if (bintrayUploadTask) {
                if (installTask) {
                    //let's bintrayUpload depensOn install
                    bintrayUploadTask.dependsOn installTask
                }
                //reset bintrayUpload task group form other to upload
                bintrayUploadTask.group = 'upload'
                if (bintrayUser == null || bintrayKey == null) {
                    bintrayUploadTask.enabled = false
                    project.logger.info("${LOG_PREFIX} ${project.path} bintrayUpload is disabled because you don't provider bintrayUser and bintrayKey")
                }
            }

            //must use this way, if direct find the task we can't get the task
            project.tasks.all { Task task ->
                if (task.name.equalsIgnoreCase('generatePomFileForMavenPublication')) {
                    if (project.hasProperty("android")) {
                        def assembleRelease = project.tasks.findByName('assembleRelease')
                        if (assembleRelease) {
                            task.dependsOn assembleRelease
                        }
                    } else {
                        def assemble = project.tasks.findByName('assemble')
                        if (assemble) {
                            task.dependsOn assemble
                        }
                    }
                }
                if (task.name.equalsIgnoreCase('install')) {
                    if (project.hasProperty("android")) {
                        def assembleRelease = project.tasks.findByName('assembleRelease')
                        if (assembleRelease) {
                            task.dependsOn assembleRelease
                        }
                    } else {
                        def assemble = project.tasks.findByName('assemble')
                        if (assemble) {
                            task.dependsOn assemble
                        }
                    }
                }
            }

        }
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def configReleasePlugin(Project project) {
        project.release {
            failOnCommitNeeded = true
            failOnPublishNeeded = true
            failOnSnapshotDependencies = true
            failOnUnversionedFiles = true
            failOnUpdateNeeded = true
            revertOnFail = true
            preCommitText = ''
            preTagCommitMessage = '[Gradle Release Plugin] - pre tag commit: '
            tagCommitMessage = '[Gradle Release Plugin] - creating tag: '
            newVersionCommitMessage = '[Gradle Release Plugin] - new version commit: '
            if (project.hasProperty("archivesBaseName")) {
                tagTemplate = '${archivesBaseName}/${version}'
            } else {
                tagTemplate = '${name}/${version}'
            }
            versionPropertyFile = 'gradle.properties'
            versionProperties = []
            versionKey = null
            if (project.hasProperty("android")) {
                buildTasks = ['assembleRelease']
            } else {
                buildTasks = ['build']
            }
            versionPatterns = [
                    /(\d+)([^\d]*$)/: { Matcher m, Project p -> m.replaceAll("${(m[0][1] as int) + 1}${m[0][2]}") }
            ]
            git {
                requireBranch = '(master|master-dev|release|(dync_deploy/.*?))'
                pushToRemote = 'origin'
                pushToBranchPrefix = ''
                commitVersionFileOnly = false
            }
        }
    }

    //必须静态，否则无法共享


    @SuppressWarnings("UnnecessaryQualifiedReference")
    def configTask(Project project) {
        def releaseTask = project.tasks.findByName('release')
        project.task(dependsOn: releaseTask, 'uploadBintray') {
            setGroup('upload')
        }
        project.task(dependsOn: releaseTask, 'uploadRelease') {
            setGroup('upload')
        }
        def uploadSnapshot = project.task(dependsOn: project.uploadArchives, 'uploadSnapshot') {
            setGroup('upload')
        }

        project.afterEvaluate {
            String isBintrayUpload = readPropertyFromProject(project, "release.bintray", "false")

            project.logger.info("${LOG_PREFIX} ${project.path} isBintrayUpload:${isBintrayUpload}")

            if (isBintrayUpload == 'true') {
                project.afterReleaseBuild.dependsOn project.bintrayUpload
            } else {
                project.afterReleaseBuild.dependsOn project.uploadArchives
                def checkSnapshotVersion = project.task('checkSnapshotVersion') {
                    setGroup('upload')
                    doLast {
                        def pomVersion = this.getPomVersion(project)
                        if (pomVersion && !pomVersion?.toUpperCase()?.contains("-SNAPSHOT")) {
                            throw new GradleException("SNAPSHOT build must contains -SNAPSHOT in version")
                        }
                    }
                }
                uploadSnapshot.dependsOn checkSnapshotVersion
                try {
                    project.install.mustRunAfter checkSnapshotVersion
                } catch (Exception e) {
                    String msg = e.getMessage()
                    if (msg?.contains("Could not get unknown property 'install'")) {
                        project.logger.error("Can't Get `install` Property, Just Ignore it.")
                        return
                    }
                    throw e
                }
                try {
                    project.uploadArchives.mustRunAfter checkSnapshotVersion
                } catch (Exception e) {
                    String msg = e.getMessage()
                    if (msg?.contains("Could not get unknown property 'uploadArchives'")) {
                        project.logger.error("Can't Get `uploadArchives` Property, Just Ignore it.")
                        return
                    }
                    throw e
                }
            }
        }
    }

    def addPrefixTask(Project project) {
        applyPluginIfNotApply(project, PrefixTaskPlugin.class)
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    def applyPluginIfNotApply(Project project, Class<? extends Plugin> pluginClazz) {
        PluginContainer pluginManager = project.getPlugins()
        if (pluginManager.hasPlugin(pluginClazz)) {
            return
        }
        pluginManager.apply(pluginClazz)
    }

    //whether contains snapshot in version
    @SuppressWarnings("UnnecessaryQualifiedReference")
    def isReleaseBuild(Project project) {
        def pomVersion = this.getPomVersion(project)
        return pomVersion && (!pomVersion.toUpperCase().contains("SNAPSHOT"))
    }

    def getReleaseRepositoryUrl(Project project) {
        return readPropertyFromProject(project, "RELEASE_REPOSITORY_URL", null, true)
    }

    def getSnapshotRepositoryUrl(Project project) {
        return readPropertyFromProject(project, "SNAPSHOT_REPOSITORY_URL", null, true)
    }

    def getReleaseRepositoryUsername(Project project) {
        return readPropertyFromProject(project, "RELEASE_REPOSITORY_USERNAME", null, true)
    }

    def getReleaseRepositoryPassword(Project project) {
        return readPropertyFromProject(project, "RELEASE_REPOSITORY_PASSWORD", null, true)
    }

    def getSnapshotRepositoryUsername(Project project) {
        return readPropertyFromProject(project, "SNAPSHOT_REPOSITORY_USERNAME", null, true)
    }

    def getSnapshotRepositoryPassword(Project project) {
        return readPropertyFromProject(project, "SNAPSHOT_REPOSITORY_PASSWORD", null, true)
    }

    def getPomGroupId(Project project) {
        return readPropertyFromProject(project, "PROJECT_POM_GROUP_ID", project.group.toString(), true)
    }

    def getPomArtifactId(Project project) {
        return readPropertyFromProject(project, "PROJECT_POM_ARTIFACT_ID", getDefaultPomArtifactId(project), true)
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    def getDefaultPomArtifactId(Project project) {
        if (project.hasProperty('archivesBaseName')) {
            return project.archivesBaseName.toString()
        }
        return project.name.toString()
    }

    def getPomVersion(Project project) {
        return readPropertyFromProject(project, "PROJECT_POM_VERSION", project.version.toString(), true)
    }

    def getPomWebsiteUrl(Project project) {
        return readPropertyFromProject(project, "POM_WEBSITE_URL", null, false)
    }

    def getPomVcsUrl(Project project) {
        return readPropertyFromProject(project, "POM_VCS_URL", null, false)
    }

    def getPomIssueUrl(Project project) {
        return readPropertyFromProject(project, "POM_ISSUE_URL", null, false)
    }

    def getPomLicense(Project project) {
        return readPropertyFromProject(project, "POM_LICENSE", null, false)
    }

    def getPomLicenseUrl(Project project) {
        return readPropertyFromProject(project, "POM_LICENSE_URL", null, false)
    }

    def getPomDeveloperId(Project project) {
        return readPropertyFromProject(project, "POM_DEVELOPER_ID", null, false)
    }

    def getPomDeveloperName(Project project) {
        return readPropertyFromProject(project, "POM_DEVELOPER_NAME", null, false)
    }

    def getPomDeveloperEmail(Project project) {
        return readPropertyFromProject(project, "POM_DEVELOPER_EMAIL", null, false)
    }

    def getPomDescription(Project project) {
        return readPropertyFromProject(project, "POM_DESCRIPTION", null, false)
    }

    def getBuildDescription(Project project) {
        Map<String, String> envMap = System.getenv()
        String jobName = envMap.get("JOB_NAME")
        String buildNumber = envMap.get("BUILD_NUMBER")
        String gitCommit = envMap.get("GIT_COMMIT")
        String userName = System.getProperty("user.name")
        String osName = System.getProperty("os.name")
        String osVersion = System.getProperty("os.version")
        String osArch = System.getProperty("os.arch")
        String javaVersion = System.getProperty("java.version")
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"))
        String buildTime = simpleDateFormat.format(new Date())
        String gradleVersion = project.gradle.gradleVersion
        String agpVersion = getAndroidGradlePluginVersionCompat()
        return "[${osName}]-[${osVersion}]-[${osArch}]-[${javaVersion}]-[${userName}]-[${gradleVersion}]-[${agpVersion!=null?agpVersion:'unknown-agp-version'}]-[${buildTime}]-[${gitCommit != null ? gitCommit : 'unknown-git'}]-[${jobName != null ? jobName : 'unknown-build-job'}]-[${buildNumber != null ? buildNumber : 'unknown-build-number'}]"
    }

    def getAndroidGradlePluginVersionCompat() {
        String version = null
        try {
            Class versionModel = Class.forName("com.android.builder.model.Version")
            def versionFiled = versionModel.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
            versionFiled.setAccessible(true)
            version = versionFiled.get(null)
        } catch (Exception e) {
        }
        return version
    }

    def getBintrayUser(Project project) {
        return readPropertyFromProject(project, "BINTRAY_USER", null, false)
    }

    def getBintrayKey(Project project) {
        return readPropertyFromProject(project, "BINTRAY_APIKEY", null, false)
    }

    def getBintrayOrganization(Project project) {
        return readPropertyFromProject(project, "BINTRAY_ORGANIZATION", null, false)
    }

    def getEnableJavadoc(Project project) {
        return readPropertyFromProject(project, "POM_ENABLE_JAVADOC", 'false', false)
    }

    def getEnableCoordinate(Project project) {
        return readPropertyFromProject(project, "POM_ENABLE_COORDINATE", 'true', false)
    }
}
