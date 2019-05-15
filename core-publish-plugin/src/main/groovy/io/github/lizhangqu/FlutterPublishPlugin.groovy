package io.github.lizhangqu


import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication
import org.yaml.snakeyaml.Yaml

/**
 * flutter发布插件
 */
class FlutterPublishPlugin extends PublishPlugin {

    public static final String LOG_PREFIX = "[FlutterPublishPlugin]"

    @Override
    void apply(Project project) {
        super.apply(project)
        FlutterPublishPluginExtension flutterPublishPluginExtension = project.getExtensions().create("flutter", FlutterPublishPluginExtension.class, project)

        project.afterEvaluate {
            File destPublishDir = flutterPublishPluginExtension.getTargetDir()
            File pubspecFile = flutterPublishPluginExtension.getPubspecFile()
            if (destPublishDir == null) {
                throw new GradleException("targetDir == null, set it like this." +
                        "flutter{\n" +
                        "    targetDir project.file('../../outputs/android/release')\n" +
                        "}")
            }

            if (!destPublishDir.exists() || !destPublishDir.isDirectory()) {
                throw new GradleException("targetDir not exist or not directory, set it like this." +
                        "flutter{\n" +
                        "    targetDir project.file('../../outputs/android/release')\n" +
                        "}")
            }

            if (pubspecFile == null) {
                throw new GradleException("pubspecFile == null, set it like this." +
                        "flutter{\n" +
                        "    pubspecFile project.file('../../pubspec.yaml')\n" +
                        "}")
            }

            if (!pubspecFile.exists() || !pubspecFile.isFile()) {
                throw new GradleException("pubspecFile not exist or not file, set it like this." +
                        "flutter{\n" +
                        "    pubspecFile project.file('../../pubspec.yaml')\n" +
                        "}")
            }

            Yaml pubspecYaml = new Yaml()
            Map<String, Object> yamlConfig = (Map<String, Object>) pubspecYaml.load(pubspecFile.text)
            Map<String, String> androidConfig = yamlConfig.get("android")
            String yamlGroup = androidConfig?.get("group")
            String yamlName = androidConfig?.get("archivesBaseName")
            String yamlVersion = androidConfig?.get("version")
            if (androidConfig == null || yamlGroup == null || yamlName == null || yamlVersion == null) {
                throw new GradleException("android config error in pubspec.yaml, set it like this." +
                        "android:\n" +
                        "  group: io.github.lizhangqu.flutter\n" +
                        "  archivesBaseName: wdbuyer_module\n" +
                        "  version: 1.0.0-SNAPSHOT")
            }

            def uploadVersion = yamlVersion
            //如果外部传参为强制release，则去除SNAPSHOT
            boolean forceRelease = readPropertyFromProject(project, "flutter.release", "false")?.toBoolean()
            if (forceRelease) {
                uploadVersion = uploadVersion - '-SNAPSHOT' - '-snapshot'
                project.logger.error("forceRelease build ${uploadVersion}")
            }
            //如果外部传递了参数，则以外部参数为准，无视flutter.release参数
            def pomVersion = getPomVersion(project)
            if (pomVersion != null && pomVersion.length() > 0 && !pomVersion.equals('unspecified')) {
                uploadVersion = pomVersion
            }



            project.publishing {
                repositories {
                    if (uploadVersion && (!uploadVersion.toUpperCase().contains("SNAPSHOT"))) {
                        def releaseRepositoryUrl = super.getReleaseRepositoryUrl(project)
                        if (releaseRepositoryUrl) {
                            def existMaven = project.publishing.repositories.getByName('maven')
                            //不能重新创建，只能修改，否则会上传两次
                            if (existMaven) {
                                existMaven.url = releaseRepositoryUrl
                                def releaseRepositoryUsername = super.getReleaseRepositoryUsername(project)
                                def releaseRepositoryPassword = super.getReleaseRepositoryPassword(project)
                                if (releaseRepositoryUsername && releaseRepositoryPassword) {
                                    existMaven.credentials.username = releaseRepositoryUsername
                                    existMaven.credentials.password = releaseRepositoryPassword
                                }
                            } else {
                                maven {
                                    url releaseRepositoryUrl
                                    def releaseRepositoryUsername = super.getReleaseRepositoryUsername(project)
                                    def releaseRepositoryPassword = super.getReleaseRepositoryPassword(project)
                                    if (releaseRepositoryUsername && releaseRepositoryPassword) {
                                        credentials {
                                            username = releaseRepositoryUsername
                                            password = releaseRepositoryPassword
                                        }
                                    }
                                }
                            }
                        } else {
                            mavenLocal()
                        }
                    } else {
                        def snapshotRepositoryUrl = super.getSnapshotRepositoryUrl(project)
                        if (snapshotRepositoryUrl) {
                            def existMaven = project.publishing.repositories.getByName('maven')
                            //不能重新创建，只能修改，否则会上传两次
                            if (existMaven) {
                                existMaven.url = snapshotRepositoryUrl
                                def snapshotRepositoryUsername = super.getSnapshotRepositoryUsername(project)
                                def snapshotRepositoryPassword = super.getSnapshotRepositoryPassword(project)
                                if (snapshotRepositoryUsername && snapshotRepositoryPassword) {
                                    existMaven.credentials.username = snapshotRepositoryUsername
                                    existMaven.credentials.password = snapshotRepositoryPassword
                                }
                            } else {
                                maven {
                                    url snapshotRepositoryUrl
                                    def snapshotRepositoryUsername = super.getSnapshotRepositoryUsername(project)
                                    def snapshotRepositoryPassword = super.getSnapshotRepositoryPassword(project)
                                    if (snapshotRepositoryUsername && snapshotRepositoryPassword) {
                                        credentials {
                                            username = snapshotRepositoryUsername
                                            password = snapshotRepositoryPassword
                                        }
                                    }
                                }
                            }
                        } else {
                            mavenLocal()
                        }
                    }
                }

                publications {
                    List<File> flutterPluginAndEngineFile = new ArrayList<>()

                    destPublishDir.eachFileRecurse { File file ->
                        if (!file.getName().endsWith(".aar")) {
                            return
                        }

                        flutterPluginAndEngineFile.add(file)

                        "${file.getName().take(file.getName().lastIndexOf('.'))}"(MavenPublication) {
                            groupId = yamlGroup
                            artifactId = yamlName + "_" + file.getName().take(file.getName().lastIndexOf('.'))
                            version = uploadVersion

                            artifact(file) {
                                extension 'aar'
                            }
                        }
                    }

                    maven(MavenPublication) {
                        groupId yamlGroup
                        artifactId yamlName
                        version uploadVersion

                        pom.withXml {
                            final depsNode = asNode().appendNode('dependencies')
                            flutterPluginAndEngineFile.each { File pluginFile ->
                                def node = depsNode.appendNode('dependency')
                                node.appendNode('groupId', yamlGroup)
                                node.appendNode('artifactId', yamlName + "_" + pluginFile.getName().take(pluginFile.getName().lastIndexOf('.')))
                                node.appendNode('version', uploadVersion)
                                node.appendNode('scope', "compile")
                                node.appendNode('type', "aar")
                            }
                        }
                    }
                }
            }


            if (uploadVersion && (!uploadVersion.toUpperCase().contains("SNAPSHOT"))) {
                def publishTask = project.tasks.findByName("publish")
                File workding = pubspecFile.getParentFile()
                String tag = "android/${uploadVersion}"
                if (publishTask) {
                    publishTask.doLast {
                        project.logger.error("it's release build, update version now")
                        def shouldUpdateVersion = true
                        pubspecFile.withReader('UTF-8') { reader ->
                            def readerString = ""
                            def hasScanned = false
                            reader.eachLine {
                                if (it.trim().equals('android:')) {
                                    hasScanned = true
                                }
                                if (hasScanned && it.trim()?.startsWith("version: ")) {
                                    if (it.trim()?.equals("version: ${uploadVersion}".toString())) {
                                        shouldUpdateVersion = false
                                    }
                                    it = "  version: ${uploadVersion}"
                                    hasScanned = false
                                }
                                readerString <<= it
                                readerString << '\n'
                            }
                            pubspecFile.withWriter('UTF-8') { writer ->
                                writer.append(readerString)
                            }
                        }

                        //版本不一致时先提交
                        if (shouldUpdateVersion) {
                            project.logger.error("update pubspec.yaml android version pre tag.")
                            //git add
                            project.logger.error("git add for release")
                            project.exec {
                                it.workingDir(workding)
                                it.commandLine("git")
                                it.args("add")
                                it.args("${pubspecFile}")
                            }

                            //git commit
                            project.logger.error("git commit for release")
                            project.exec {
                                it.workingDir(workding)
                                it.commandLine("git")
                                it.args("commit")
                                it.args("-m")
                                it.args("[Gradle] Release Version For Android")
                            }
                        }

                        //打tag
                        project.logger.error("create tag for release ${tag}")
                        project.exec {
                            it.workingDir(workding)
                            it.commandLine("git")
                            it.args("tag")
                            it.args("-a")
                            it.args("${tag}")
                            it.args("-m")
                            it.args("[Gradle] Create Tag For Android")
                        }

                        //noinspection UnnecessaryQualifiedReference
                        Map<String, Closure<String>> patterns = [
                                // Increments last number: "2.5-SNAPSHOT" => "2.6-SNAPSHOT"
                                /(\d+)([^\d]*$)/: { java.util.regex.Matcher m, Project p -> m.replaceAll("${(m[0][1] as int) + 1}${m[0][2]}") }
                        ]

                        String nextVersion = uploadVersion
                        for (entry in patterns) {

                            String pattern = entry.key
                            Closure handler = entry.value
                            //noinspection UnnecessaryQualifiedReference
                            java.util.regex.Matcher matcher = nextVersion =~ pattern

                            if (matcher.find()) {
                                nextVersion = handler(matcher, project)

                                //让新版本永远为SNAPSHOT更新
                                if (!nextVersion.endsWith('-SNAPSHOT')) {
                                    nextVersion += '-SNAPSHOT'
                                }
                            }
                        }

                        //next version
                        project.logger.error("replace ${uploadVersion} with next version  ${nextVersion}")
                        pubspecFile.withReader('UTF-8') { reader ->
                            def readerString = ""
                            def hasScanned = false

                            reader.eachLine {
                                if (it.trim().equals('android:')) {
                                    hasScanned = true
                                }
                                if (hasScanned && it.trim()?.startsWith("version: ")) {
                                    it = "  version: ${nextVersion}"
                                    hasScanned = false
                                }
                                readerString <<= it
                                readerString << '\n'
                            }
                            pubspecFile.withWriter('UTF-8') { writer ->
                                writer.append(readerString)
                            }
                        }

                        //git add
                        project.logger.error("git add for next version")
                        project.exec {
                            it.workingDir(workding)
                            it.commandLine("git")
                            it.args("add")
                            it.args("${pubspecFile}")
                        }

                        //git commit
                        project.logger.error("git commit for next version")
                        project.exec {
                            it.workingDir(workding)
                            it.commandLine("git")
                            it.args("commit")
                            it.args("-m")
                            it.args("[Gradle] Next SNAPSHOT Version For Android")
                        }


                        String currentBranch = "git symbolic-ref --short -q HEAD".execute().text.trim()
                        //git push
                        project.logger.error("git push")
                        project.exec {
                            it.workingDir(workding)
                            it.commandLine("git")
                            it.args("push")
                            it.args("--set-upstream")
                            it.args("origin")
                            it.args("${currentBranch}")

                        }

                        //git push tag
                        project.logger.error("git push tag")
                        project.exec {
                            it.workingDir(workding)
                            it.commandLine("git")
                            it.args("push")
                            it.args("origin")
                            it.args("${tag}")
                        }
                    }
                }
            }
        }

    }

}
