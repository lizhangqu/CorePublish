package io.github.lizhangqu

import com.jfrog.bintray.gradle.BintrayPlugin
import net.researchgate.release.ReleasePlugin
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.AndroidMavenPlugin
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.jvm.tasks.Jar
import org.gradle.util.NameMatcher

import java.util.concurrent.TimeUnit
import java.util.regex.Matcher

/**
 * maven发布插件和bintray发布插件
 */
class CorePublishPlugin implements Plugin<Project> {
    private boolean enableJavadoc = false
    private boolean enablePomCoordinate = false
    private Properties properties = new Properties()

    @Override
    void apply(Project project) {
        def taskNames = project.gradle.startParameter.getTaskNames()
        if (taskNames != null && taskNames.contains("uploadArchives")) {
            throw new GradleException("please use uploadSnapshot or uploadRelease instead. uploadArchives is forbidden")
        }
        if (taskNames != null && taskNames.contains("bintrayUpload")) {
            throw new GradleException("please use uploadBintray instead. bintrayUpload is forbidden")
        }
        //resolutionStrategy
        configResolutionStrategy(project)
        //read local properties
        loadLocalProperties(project)
        //loadSwitchValue
        loadSwitchValue(project)
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
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    def loadSwitchValue(Project project) {
        def enableDoc = getEnableJavadoc(project)
        if (enableDoc != null && enableDoc.equalsIgnoreCase("true")) {
            enableJavadoc = true
        }

        project.logger.error("${project.path} enableJavadoc:${enableJavadoc}")

        def enablePOM = getEnableCoordinate(project)
        if (enablePOM != null && enablePOM.equalsIgnoreCase("true")) {
            enablePomCoordinate = true
        }

        project.logger.error("${project.path} enablePomCoordinate:${enablePomCoordinate}")

        if (enablePomCoordinate) {
            def pomGroupId = this.getPomGroupId(project)
            def pomArtifactId = this.getPomArtifactId(project)
            def pomVersion = this.getPomVersion(project)

            project.group = pomGroupId
            project.archivesBaseName = pomArtifactId
            project.version = pomVersion
        }
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    def configResolutionStrategy(Project project) {
        project.configurations.all {
            it.resolutionStrategy.cacheDynamicVersionsFor(5, TimeUnit.MINUTES)
            it.resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
        }
    }

    def compatibleJava8(Project project) {
        if (JavaVersion.current().isJava8Compatible()) {
            project.tasks.withType(Javadoc) {
                options.addStringOption('Xdoclint:none', '-quiet')
            }
        }
    }

    def utf8WithJavaCompile(Project project) {
        project.tasks.withType(JavaCompile) {
            options.encoding = "UTF-8"
        }

        project.tasks.withType(Javadoc) {
            options.encoding = "utf-8"
        }
    }

    def applyThirdPlugin(Project project) {
        applyPluginIfNotApply(project, MavenPublishPlugin.class)
        applyPluginIfNotApply(project, BintrayPlugin.class)
        applyPluginIfNotApply(project, AndroidMavenPlugin.class)
        applyPluginIfNotApply(project, ReleasePlugin.class)
    }


    def configJavaDocAndJavaSource(Project project) {
        // Android libraries
        if (project.hasProperty("android")) {

            def androidJavadocs = project.task(type: Javadoc, 'androidJavadocs') {
                source = project.android.sourceSets.main.java.srcDirs
                classpath += project.configurations.compile
                classpath += project.configurations.provided
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
                if (enableJavadoc) {
                    archives androidJavaDocsJar
                }

            }

        } else {

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
                if (enableJavadoc) {
                    archives javaDocJar
                }
            }

        }
    }


    @SuppressWarnings("UnnecessaryQualifiedReference")
    def configInstall(Project project) {
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
            }
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
            }
        }
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    def configPublishing(Project project) {
        project.publishing {
            repositories {
                if (isReleaseBuild(project)) {
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
                    name = pomArtifactId
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
                if (installTask) {
                    uploadArchivesTask.dependsOn installTask
                }
            }

            if (bintrayUploadTask) {
                if (installTask) {
                    //let's bintrayUpload depensOn install
                    bintrayUploadTask.dependsOn installTask
                }
                //reset bintrayUpload task group form other to upload
                bintrayUploadTask.group = 'upload'
                if (!bintrayUser || !bintrayKey) {
                    bintrayUploadTask.enabled = false
                    project.logger.error("bintrayUpload is disabled because you don't provider bintrayUser and bintrayKey")
                }
            }

            //must use this way, if direct find the task we can't get the task
            project.tasks.all { Task task ->
                if (task.name.equalsIgnoreCase('generatePomFileForMavenPublication')) {
                    task.dependsOn project.tasks.getByName('assemble')
                }
            }

        }
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def configReleasePlugin(Project project) {
        //auto version
        project.ext.'release.useAutomaticVersion' = true
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
            tagTemplate = '${name}/${version}'
            versionPropertyFile = 'gradle.properties'
            versionProperties = []
            versionKey = null
            buildTasks = ['build']
            versionPatterns = [
                    /(\d+)([^\d]*$)/: { Matcher m, Project p -> m.replaceAll("${(m[0][1] as int) + 1}${m[0][2]}") }
            ]
            git {
                requireBranch = 'master'
                pushToRemote = 'origin'
                pushToBranchPrefix = ''
                commitVersionFileOnly = false
            }
        }
    }

    //必须静态，否则无法共享
    static Boolean isBintrayUpload = null

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
            List<String> taskNames = project.gradle.startParameter.taskNames
            if (taskNames != null && taskNames.size() > 0) {
                String startTaskName = taskNames.get(0)
                project.logger.error("${project.path} startTaskName:${startTaskName}")
                NameMatcher matcher = new NameMatcher()
                String actualName = matcher.find(startTaskName, project.tasks.asMap.keySet())
                project.logger.error("${project.path} actualName:${actualName}")

                //isBintrayUpload逻辑比较变态，不是特别熟悉gradle的话勿动
                if (isBintrayUpload == null) {
                    if (actualName != null) {
                        if (actualName.equalsIgnoreCase("uploadBintray") || actualName.equalsIgnoreCase("bintrayUpload")) {
                            isBintrayUpload = true
                        }
                    } else {
                        if (startTaskName != null && startTaskName.equalsIgnoreCase("uploadBintray") || startTaskName.equalsIgnoreCase("bintrayUpload")) {
                            isBintrayUpload = true
                        }
                    }
                } else {
                    if (startTaskName.contains("createScmAdapter")) {
                        //not handle
                        //moduleName:createScmAdapter
                    } else if (startTaskName.contains("beforeReleaseBuild")) {
                        //not handle
                        //moduleName:moduleName:beforeReleaseBuild
                    } else {
                        if (actualName != null) {
                            if (!(actualName.equalsIgnoreCase("uploadBintray") || actualName.equalsIgnoreCase("bintrayUpload"))) {
                                isBintrayUpload = false
                            } else if (actualName.equalsIgnoreCase("uploadBintray") || actualName.equalsIgnoreCase("bintrayUpload")) {
                                isBintrayUpload = true
                            }
                        } else {
                            if (!(startTaskName != null && startTaskName.equalsIgnoreCase("uploadBintray") || startTaskName.equalsIgnoreCase("bintrayUpload"))) {
                                isBintrayUpload = false
                            } else if (startTaskName != null && startTaskName.equalsIgnoreCase("uploadBintray") || startTaskName.equalsIgnoreCase("bintrayUpload")) {
                                isBintrayUpload = true
                            }
                        }
                    }

                }
            }

            project.logger.error("${project.path} isBintrayUpload:${isBintrayUpload}")

            if (isBintrayUpload != null && isBintrayUpload) {
                project.afterReleaseBuild.dependsOn project.bintrayUpload
            } else {
                project.afterReleaseBuild.dependsOn project.uploadArchives
                def checkSnapshotVersion = project.task('checkSnapshotVersion') {
                    setGroup('upload')
                    doLast {
                        def pomVersion = this.getPomVersion(project)
                        if (pomVersion && !pomVersion.toLowerCase().contains("-snapshot")) {
                            throw new GradleException("SNAPSHOT build must contains -SNAPSHOT in version")
                        }
                    }
                }
                uploadSnapshot.dependsOn checkSnapshotVersion
                project.install.mustRunAfter checkSnapshotVersion
                project.uploadArchives.mustRunAfter checkSnapshotVersion
            }
        }
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
        return pomVersion && (!pomVersion.toLowerCase().contains("snapshot"))
    }

    def loadLocalProperties(Project project) {
        try {
            File localFile = project.rootProject.file('local.properties')
            if (localFile.exists()) {
                properties.load(localFile.newDataInputStream())
            }
        } catch (Exception e) {
            println("load local properties failed msg:${e.message}")
        }
    }

    def readPropertyFromLocalProperties(Project project, String key, String defaultValue) {
        readPropertyFromLocalPropertiesOrThrow(project, key, defaultValue, true)
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    def readPropertyFromLocalPropertiesOrThrow(Project project, String key, String defaultValue, boolean throwIfNull) {
        def property = properties != null ? properties.getProperty(key, defaultValue) : defaultValue
        if (property == null && throwIfNull) {
            throw new GradleException("you must config ${key} in properties. Like config project.ext.${key} , add ${key} in gradle.properties or add ${key} in local.properties which locates on root project dir")
        }
        return property
    }

    def getReleaseRepositoryUrl(Project project) {
        return project.hasProperty('RELEASE_REPOSITORY_URL') ? project.ext.RELEASE_REPOSITORY_URL : readPropertyFromLocalPropertiesOrThrow(project, 'RELEASE_REPOSITORY_URL', null, false)
    }

    def getSnapshotRepositoryUrl(Project project) {
        return project.hasProperty('SNAPSHOT_REPOSITORY_URL') ? project.ext.SNAPSHOT_REPOSITORY_URL : readPropertyFromLocalPropertiesOrThrow(project, 'SNAPSHOT_REPOSITORY_URL', null, false)
    }

    def getReleaseRepositoryUsername(Project project) {
        return project.hasProperty('RELEASE_REPOSITORY_USERNAME') ? project.ext.RELEASE_REPOSITORY_USERNAME : readPropertyFromLocalPropertiesOrThrow(project, 'RELEASE_REPOSITORY_USERNAME', null, false)
    }

    def getReleaseRepositoryPassword(Project project) {
        return project.hasProperty('RELEASE_REPOSITORY_PASSWORD') ? project.ext.RELEASE_REPOSITORY_PASSWORD : readPropertyFromLocalPropertiesOrThrow(project, 'RELEASE_REPOSITORY_PASSWORD', null, false)
    }

    def getSnapshotRepositoryUsername(Project project) {
        return project.hasProperty('SNAPSHOT_REPOSITORY_USERNAME') ? project.ext.SNAPSHOT_REPOSITORY_USERNAME : readPropertyFromLocalPropertiesOrThrow(project, 'SNAPSHOT_REPOSITORY_USERNAME', null, false)
    }

    def getSnapshotRepositoryPassword(Project project) {
        return project.hasProperty('SNAPSHOT_REPOSITORY_PASSWORD') ? project.ext.SNAPSHOT_REPOSITORY_PASSWORD : readPropertyFromLocalPropertiesOrThrow(project, 'SNAPSHOT_REPOSITORY_PASSWORD', null, false)
    }

    def getPomGroupId(Project project) {
        return project.hasProperty('PROJECT_POM_GROUP_ID') ? project.ext.PROJECT_POM_GROUP_ID : readPropertyFromLocalProperties(project, 'PROJECT_POM_GROUP_ID', null)
    }

    def getPomArtifactId(Project project) {
        return project.hasProperty('PROJECT_POM_ARTIFACT_ID') ? project.ext.PROJECT_POM_ARTIFACT_ID : readPropertyFromLocalProperties(project, 'PROJECT_POM_ARTIFACT_ID', null)
    }

    def getPomVersion(Project project) {
        return project.hasProperty('PROJECT_POM_VERSION') ? project.ext.PROJECT_POM_VERSION : readPropertyFromLocalProperties(project, 'PROJECT_POM_VERSION', null)
    }

    def getPomWebsiteUrl(Project project) {
        return project.hasProperty('POM_WEBSITE_URL') ? project.ext.POM_WEBSITE_URL : readPropertyFromLocalPropertiesOrThrow(project, 'POM_WEBSITE_URL', null, false)
    }

    def getPomVcsUrl(Project project) {
        return project.hasProperty('POM_VCS_URL') ? project.ext.POM_VCS_URL : readPropertyFromLocalPropertiesOrThrow(project, 'POM_VCS_URL', null, false)
    }

    def getPomIssueUrl(Project project) {
        return project.hasProperty('POM_ISSUE_URL') ? project.ext.POM_ISSUE_URL : readPropertyFromLocalPropertiesOrThrow(project, 'POM_ISSUE_URL', null, false)
    }

    def getPomLicense(Project project) {
        return project.hasProperty('POM_LICENSE') ? project.ext.POM_LICENSE : readPropertyFromLocalPropertiesOrThrow(project, 'POM_LICENSE', null, false)
    }

    def getPomLicenseUrl(Project project) {
        return project.hasProperty('POM_LICENSE_URL') ? project.ext.POM_LICENSE_URL : readPropertyFromLocalPropertiesOrThrow(project, 'POM_LICENSE_URL', null, false)
    }

    def getPomDeveloperId(Project project) {
        return project.hasProperty('POM_DEVELOPER_ID') ? project.ext.POM_DEVELOPER_ID : readPropertyFromLocalPropertiesOrThrow(project, 'POM_DEVELOPER_ID', null, false)
    }

    def getPomDeveloperName(Project project) {
        return project.hasProperty('POM_DEVELOPER_NAME') ? project.ext.POM_DEVELOPER_NAME : readPropertyFromLocalPropertiesOrThrow(project, 'POM_DEVELOPER_NAME', null, false)
    }

    def getPomDeveloperEmail(Project project) {
        return project.hasProperty('POM_DEVELOPER_EMAIL') ? project.ext.POM_DEVELOPER_EMAIL : readPropertyFromLocalPropertiesOrThrow(project, 'POM_DEVELOPER_EMAIL', null, false)
    }

    def getPomDescription(Project project) {
        return project.hasProperty('POM_DESCRIPTION') ? project.ext.POM_DESCRIPTION : readPropertyFromLocalPropertiesOrThrow(project, 'POM_DESCRIPTION', null, false)
    }

    def getBintrayUser(Project project) {
        return project.hasProperty('BINTRAY_USER') ? project.ext.BINTRAY_USER : readPropertyFromLocalPropertiesOrThrow(project, 'BINTRAY_USER', null, false)
    }

    def getBintrayKey(Project project) {
        return project.hasProperty('BINTRAY_APIKEY') ? project.ext.BINTRAY_APIKEY : readPropertyFromLocalPropertiesOrThrow(project, 'BINTRAY_APIKEY', null, false)
    }

    def getEnableJavadoc(Project project) {
        return project.hasProperty('POM_ENABLE_JAVADOC') ? project.ext.POM_ENABLE_JAVADOC : readPropertyFromLocalPropertiesOrThrow(project, 'POM_ENABLE_JAVADOC', 'false', false)
    }

    def getEnableCoordinate(Project project) {
        return project.hasProperty('POM_ENABLE_COORDINATE') ? project.ext.POM_ENABLE_COORDINATE : readPropertyFromLocalPropertiesOrThrow(project, 'POM_ENABLE_COORDINATE', 'false', false)
    }
}
