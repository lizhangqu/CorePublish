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

import java.util.concurrent.TimeUnit
import java.util.regex.Matcher

/**
 * maven发布插件和bintray发布插件
 */
class CorePublishPlugin implements Plugin<Project> {
    private static boolean enableJavadoc = false

    static Properties properties = new Properties()

    @Override
    void apply(Project project) {
        def taskNames = project.gradle.startParameter.getTaskNames()
        if (taskNames != null && taskNames.contains("uploadArchives")) {
            throw new GradleException("please use uploadSnapshot or uploadRelease instead. uploadArchives is forbidden")
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
        //pom poi
        configPomPOI(project)
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

    static def loadSwitchValue(Project project) {
        def enableDoc = getEnableJavadoc(project)
        if (enableDoc != null && enableDoc.equalsIgnoreCase("true")) {
            enableJavadoc = true
        }
    }

    static def configResolutionStrategy(Project project) {
        project.configurations.all {
            it.resolutionStrategy.cacheDynamicVersionsFor(5, TimeUnit.MINUTES)
            it.resolutionStrategy.cacheChangingModulesFor(0, TimeUnit.SECONDS)
        }
    }

    static def compatibleJava8(Project project) {
        if (JavaVersion.current().isJava8Compatible()) {
            project.tasks.withType(Javadoc) {
                options.addStringOption('Xdoclint:none', '-quiet')
            }
        }
    }

    static def utf8WithJavaCompile(Project project) {
        project.tasks.withType(JavaCompile) {
            options.encoding = "UTF-8"
        }

        project.tasks.withType(Javadoc) {
            options.encoding = "utf-8"
        }
    }

    static def configPomPOI(Project project) {
        def pomGroupId = getPomGroupId(project)
        def pomArtifactId = getPomArtifactId(project)
        def pomVersion = getPomVersion(project)

        project.group = pomGroupId
        project.archivesBaseName = pomArtifactId
        project.version = pomVersion
    }

    static def applyThirdPlugin(Project project) {
        applyPluginIfNotApply(project, MavenPublishPlugin.class)
        applyPluginIfNotApply(project, BintrayPlugin.class)
        applyPluginIfNotApply(project, AndroidMavenPlugin.class)
        applyPluginIfNotApply(project, ReleasePlugin.class)
    }


    static def configJavaDocAndJavaSource(Project project) {
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
                    archives javaDocJarJar
                }
            }

        }
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    static def configInstall(Project project) {
        project.install {
            repositories {
                mavenInstaller {
                    pom.project {
                        // This generates POM.xml with proper parameters
                        groupId = project.group
                        artifactId = project.archivesBaseName
                        version = project.version

                        name project.archivesBaseName

                        def pomWebsiteUrl = CorePublishPlugin.getPomWebsiteUrl(project)
                        if (pomWebsiteUrl) {
                            url pomWebsiteUrl
                        }

                        def pomVcsUrl = CorePublishPlugin.getPomVcsUrl(project)
                        if (pomVcsUrl) {
                            scm {
                                url pomVcsUrl
                                connection pomVcsUrl
                                developerConnection pomVcsUrl
                            }
                        }

                        def pomLicense = CorePublishPlugin.getPomLicense(project)
                        def pomLicenseUrl = CorePublishPlugin.getPomLicenseUrl(project)
                        if (pomLicense && pomLicenseUrl) {
                            licenses {
                                license {
                                    name pomLicense
                                    url pomLicenseUrl
                                }
                            }
                        }

                        def pomDeveloperId = CorePublishPlugin.getPomDeveloperId(project)
                        def pomDeveloperName = CorePublishPlugin.getPomDeveloperName(project)
                        def pomDeveloperEmail = CorePublishPlugin.getPomDeveloperEmail(project)
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
    static def configUploadArchives(Project project) {
        project.uploadArchives {
            repositories {
                mavenDeployer {
                    pom.groupId = project.group
                    pom.artifactId = project.archivesBaseName
                    pom.version = project.version

                    def releaseRepositoryUrl = CorePublishPlugin.getReleaseRepositoryUrl(project)
                    if (releaseRepositoryUrl) {
                        repository(url: releaseRepositoryUrl) {
                            def releaseRepositoryUsername = CorePublishPlugin.getReleaseRepositoryUsername(project)
                            def releaseRepositoryPassword = CorePublishPlugin.getReleaseRepositoryPassword(project)
                            if (releaseRepositoryUsername && releaseRepositoryPassword) {
                                authentication(userName: releaseRepositoryUsername, password: releaseRepositoryPassword)
                            }
                        }
                    }

                    def snapshotRepositoryUrl = CorePublishPlugin.getSnapshotRepositoryUrl(project)
                    if (snapshotRepositoryUrl) {
                        snapshotRepository(url: snapshotRepositoryUrl) {
                            def snapshotRepositoryUsername = CorePublishPlugin.getSnapshotRepositoryUsername(project)
                            def snapshotRepositoryPassword = CorePublishPlugin.getSnapshotRepositoryPassword(project)
                            if (snapshotRepositoryUsername && snapshotRepositoryPassword) {
                                authentication(userName: snapshotRepositoryUsername, password: snapshotRepositoryPassword)
                            }
                        }
                    }

                    pom.project {
                        name project.archivesBaseName

                        def pomWebsiteUrl = CorePublishPlugin.getPomWebsiteUrl(project)
                        if (pomWebsiteUrl) {
                            url pomWebsiteUrl
                        }

                        def pomVcsUrl = CorePublishPlugin.getPomVcsUrl(project)
                        if (pomVcsUrl) {
                            scm {
                                url pomVcsUrl
                                connection pomVcsUrl
                                developerConnection pomVcsUrl
                            }
                        }

                        def pomLicense = CorePublishPlugin.getPomLicense(project)
                        def pomLicenseUrl = CorePublishPlugin.getPomLicenseUrl(project)
                        if (pomLicense && pomLicenseUrl) {
                            licenses {
                                license {
                                    name pomLicense
                                    url pomLicenseUrl
                                }
                            }
                        }

                        def pomDeveloperId = CorePublishPlugin.getPomDeveloperId(project)
                        def pomDeveloperName = CorePublishPlugin.getPomDeveloperName(project)
                        def pomDeveloperEmail = CorePublishPlugin.getPomDeveloperEmail(project)
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
    static def configPublishing(Project project) {
        project.publishing {
            repositories {
                if (isReleaseBuild(project)) {
                    def releaseRepositoryUrl = CorePublishPlugin.getReleaseRepositoryUrl(project)
                    if (releaseRepositoryUrl) {
                        maven {
                            url releaseRepositoryUrl
                            def releaseRepositoryUsername = CorePublishPlugin.getReleaseRepositoryUsername(project)
                            def releaseRepositoryPassword = CorePublishPlugin.getReleaseRepositoryPassword(project)
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
                    def snapshotRepositoryUrl = CorePublishPlugin.getSnapshotRepositoryUrl(project)
                    if (snapshotRepositoryUrl) {
                        maven {
                            url snapshotRepositoryUrl
                            def snapshotRepositoryUsername = CorePublishPlugin.getSnapshotRepositoryUsername(project)
                            def snapshotRepositoryPassword = CorePublishPlugin.getSnapshotRepositoryPassword(project)
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
                    groupId project.group
                    artifactId project.archivesBaseName
                    version project.version

                    //then need to config artifact
                    //like this in build.gradle
                    // publishing {
                    //     publications {
                    //         maven(MavenPublication) {
                    //             artifact "${project.buildDir}/outputs/apk/${project.archivesBaseName}-debug.ap"
                    //         }
                    //     }
                    // }

                }
            }
        }
    }

    @SuppressWarnings("UnnecessaryQualifiedReference")
    static def configBintray(Project project) {
        def bintrayUser = CorePublishPlugin.getBintrayUser(project)
        def bintrayKey = CorePublishPlugin.getBintrayKey(project)
        if (bintrayUser && bintrayKey) {
            project.bintray {
                user = bintrayUser
                key = bintrayKey
                configurations = ['archives']

                pkg {
                    repo = 'maven'
                    name = project.archivesBaseName
                    def pomDescription = CorePublishPlugin.getPomDescription(project)
                    if (pomDescription) {
                        desc = pomDescription
                    }
                    def pomLicense = CorePublishPlugin.getPomLicense(project)
                    if (pomLicense) {
                        licenses = ["${pomLicense}"]
                    }
                    def pomWebsiteUrl = CorePublishPlugin.getPomWebsiteUrl(project)
                    if (pomWebsiteUrl) {
                        websiteUrl = pomWebsiteUrl
                    }
                    def pomVcsUrl = CorePublishPlugin.getPomVcsUrl(project)
                    if (pomVcsUrl) {
                        vcsUrl = pomVcsUrl
                    }
                    def pomIssueUrl = CorePublishPlugin.getPomIssueUrl(project)
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
    static def configTaskDependency(Project project) {
        project.afterEvaluate {

            def bintrayUser = CorePublishPlugin.getBintrayUser(project)
            def bintrayKey = CorePublishPlugin.getBintrayKey(project)


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
    static def configReleasePlugin(Project project) {
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
        project.afterReleaseBuild.dependsOn project.uploadArchives
    }

    static def configTask(Project project) {
        def releaseTask = project.tasks.findByName('release')
        project.task(dependsOn: releaseTask, 'uploadRelease') {
            setGroup('upload')
        }

        def checkSnapshotVersion = project.task('checkSnapshotVersion') {
            setGroup('upload')
            doLast {
                if (project.version && !project.version.toString().toLowerCase().contains("-snapshot")) {
                    throw new GradleException("SNAPSHOT build must contains -SNAPSHOT in version")
                }
            }
        }
        def uploadSnapshot = project.task(dependsOn: project.uploadArchives, 'uploadSnapshot') {
            setGroup('upload')
        }

        uploadSnapshot.dependsOn checkSnapshotVersion
        project.install.mustRunAfter checkSnapshotVersion
        project.uploadArchives.mustRunAfter checkSnapshotVersion
    }

    static def applyPluginIfNotApply(Project project, Class<? extends Plugin> pluginClazz) {
        PluginContainer pluginManager = project.getPlugins()
        if (pluginManager.hasPlugin(pluginClazz)) {
            return
        }
        pluginManager.apply(pluginClazz)
    }

    static def isReleaseBuild(Project project) {
        def pomVersion = PublishPlugin.getPomVersion(project)
        return pomVersion && (!pomVersion.toLowerCase().contains("snapshot"))
    }

    static def loadLocalProperties(Project project) {
        try {
            File localFile = project.rootProject.file('local.properties')
            if (localFile.exists()) {
                properties.load(localFile.newDataInputStream())
            }
        } catch (Exception e) {
            println("load local properties failed msg:${e.message}")
        }
    }

    static def readPropertyFromLocalProperties(Project project, String key, String defaultValue) {
        readPropertyFromLocalPropertiesOrThrow(project, key, defaultValue, true)
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    static
    def readPropertyFromLocalPropertiesOrThrow(Project project, String key, String defaultValue, boolean throwIfNull) {
        def property = properties != null ? properties.getProperty(key, defaultValue) : defaultValue
        if (property == null && throwIfNull) {
            throw new GradleException("you must config ${key} in properties. Like config project.ext.${key} , add ${key} in gradle.properties or add ${key} in local.properties which locates on root project dir")
        }
        return property
    }

    static def getReleaseRepositoryUrl(Project project) {
        return project.hasProperty('RELEASE_REPOSITORY_URL') ? project.ext.RELEASE_REPOSITORY_URL : readPropertyFromLocalPropertiesOrThrow(project, 'RELEASE_REPOSITORY_URL', null, false)
    }

    static def getSnapshotRepositoryUrl(Project project) {
        return project.hasProperty('SNAPSHOT_REPOSITORY_URL') ? project.ext.SNAPSHOT_REPOSITORY_URL : readPropertyFromLocalPropertiesOrThrow(project, 'SNAPSHOT_REPOSITORY_URL', null, false)
    }

    static def getReleaseRepositoryUsername(Project project) {
        return project.hasProperty('RELEASE_REPOSITORY_USERNAME') ? project.ext.RELEASE_REPOSITORY_USERNAME : readPropertyFromLocalPropertiesOrThrow(project, 'RELEASE_REPOSITORY_USERNAME', null, false)
    }

    static def getReleaseRepositoryPassword(Project project) {
        return project.hasProperty('RELEASE_REPOSITORY_PASSWORD') ? project.ext.RELEASE_REPOSITORY_PASSWORD : readPropertyFromLocalPropertiesOrThrow(project, 'RELEASE_REPOSITORY_PASSWORD', null, false)
    }

    static def getSnapshotRepositoryUsername(Project project) {
        return hasProperty('SNAPSHOT_REPOSITORY_USERNAME') ? project.ext.SNAPSHOT_REPOSITORY_USERNAME : readPropertyFromLocalPropertiesOrThrow(project, 'SNAPSHOT_REPOSITORY_USERNAME', null, false)
    }

    static def getSnapshotRepositoryPassword(Project project) {
        return project.hasProperty('SNAPSHOT_REPOSITORY_PASSWORD') ? project.ext.SNAPSHOT_REPOSITORY_PASSWORD : readPropertyFromLocalPropertiesOrThrow(project, 'SNAPSHOT_REPOSITORY_PASSWORD', null, false)
    }

    static def getPomGroupId(Project project) {
        return project.hasProperty('PROJECT_POM_GROUP_ID') ? project.ext.PROJECT_POM_GROUP_ID : readPropertyFromLocalProperties(project, 'PROJECT_POM_GROUP_ID', null)
    }

    static def getPomArtifactId(Project project) {
        return project.hasProperty('PROJECT_POM_ARTIFACT_ID') ? project.ext.PROJECT_POM_ARTIFACT_ID : readPropertyFromLocalProperties(project, 'PROJECT_POM_ARTIFACT_ID', null)
    }

    static def getPomVersion(Project project) {
        return project.hasProperty('PROJECT_POM_VERSION') ? project.ext.PROJECT_POM_VERSION : readPropertyFromLocalProperties(project, 'PROJECT_POM_VERSION', null)
    }

    static def getPomWebsiteUrl(Project project) {
        return project.hasProperty('POM_WEBSITE_URL') ? project.ext.POM_WEBSITE_URL : readPropertyFromLocalPropertiesOrThrow(project, 'POM_WEBSITE_URL', null, false)
    }

    static def getPomVcsUrl(Project project) {
        return project.hasProperty('POM_VCS_URL') ? project.ext.POM_VCS_URL : readPropertyFromLocalPropertiesOrThrow(project, 'POM_VCS_URL', null, false)
    }

    static def getPomIssueUrl(Project project) {
        return project.hasProperty('POM_ISSUE_URL') ? project.ext.POM_ISSUE_URL : readPropertyFromLocalPropertiesOrThrow(project, 'POM_ISSUE_URL', null, false)
    }

    static def getPomLicense(Project project) {
        return project.hasProperty('POM_LICENSE') ? project.ext.POM_LICENSE : readPropertyFromLocalPropertiesOrThrow(project, 'POM_LICENSE', null, false)
    }

    static def getPomLicenseUrl(Project project) {
        return project.hasProperty('POM_LICENSE_URL') ? project.ext.POM_LICENSE_URL : readPropertyFromLocalPropertiesOrThrow(project, 'POM_LICENSE_URL', null, false)
    }

    static def getPomDeveloperId(Project project) {
        return project.hasProperty('POM_DEVELOPER_ID') ? project.ext.POM_DEVELOPER_ID : readPropertyFromLocalPropertiesOrThrow(project, 'POM_DEVELOPER_ID', null, false)
    }

    static def getPomDeveloperName(Project project) {
        return project.hasProperty('POM_DEVELOPER_NAME') ? project.ext.POM_DEVELOPER_NAME : readPropertyFromLocalPropertiesOrThrow(project, 'POM_DEVELOPER_NAME', null, false)
    }

    static def getPomDeveloperEmail(Project project) {
        return project.hasProperty('POM_DEVELOPER_EMAIL') ? project.ext.POM_DEVELOPER_EMAIL : readPropertyFromLocalPropertiesOrThrow(project, 'POM_DEVELOPER_EMAIL', null, false)
    }

    static def getPomDescription(Project project) {
        return project.hasProperty('POM_DESCRIPTION') ? project.ext.POM_DESCRIPTION : readPropertyFromLocalPropertiesOrThrow(project, 'POM_DESCRIPTION', null, false)
    }

    static def getBintrayUser(Project project) {
        return project.hasProperty('BINTRAY_USER') ? project.ext.BINTRAY_USER : readPropertyFromLocalPropertiesOrThrow(project, 'BINTRAY_USER', null, false)
    }

    static def getBintrayKey(Project project) {
        return project.hasProperty('BINTRAY_APIKEY') ? project.ext.BINTRAY_APIKEY : readPropertyFromLocalPropertiesOrThrow(project, 'BINTRAY_APIKEY', null, false)
    }

    static def getEnableJavadoc(Project project) {
        return project.hasProperty('POM_ENABLE_JAVADOC') ? project.ext.POM_ENABLE_JAVADOC : readPropertyFromLocalPropertiesOrThrow(project, 'POM_ENABLE_JAVADOC', 'false', false)
    }
}
