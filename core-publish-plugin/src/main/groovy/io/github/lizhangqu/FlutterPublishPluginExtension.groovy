package io.github.lizhangqu

import org.gradle.api.Project

class FlutterPublishPluginExtension {
    Project project

    File targetDir
    File pubspecFile

    FlutterPublishPluginExtension(Project project) {
        this.project = project
        this.targetDir = project.file('../../outputs/android/release')
        this.pubspecFile = project.file('../../pubspec.yaml')
    }
}
