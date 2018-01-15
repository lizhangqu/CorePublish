package org.gradle.api.tasks

import org.gradle.StartParameter
import org.gradle.api.Project

/**
 * 多module结构上传脚本抽象，uploadProjects必须配置在releasePublish前面，uploadProjects设置在前的会优先被发布，releasePublish值必须被设置
 * 版本号命名规范必须为POM_module名.replaceAll('-','_').toUpperCase()_VERSION
 */
//task uploadSnapshot(type: MultiProjectUpload) {
//    uploadProjects = ['libray', 'library1']
//    releasePublish = false
//}
//task uploadRelease(type: MultiProjectUpload) {
//    uploadProjects = ['libray', 'library1']
//    releasePublish = true
//}
class MultiProjectUpload extends GradleBuild {
    @Input
    Boolean releasePublish = null
    @Input
    List<String> uploadProjects = null

    MultiProjectUpload() {
        this.setGroup('upload')
        this.setDescription("multi project upload task, using gradlew :${getName()}")
    }

    void setReleasePublish(Boolean releasePublish) {
        this.releasePublish = releasePublish
        this.startParameter = getProjectStartParameter(project, releasePublish)
        this.tasks = getProjectUploadTasks(project, releasePublish)
    }

    void releasePublish(Boolean releasePublish) {
        this.setReleasePublish(releasePublish)
    }

    void setUploadProjects(List<String> projectNames) {
        if (projectNames != null && projectNames.size() > 0) {
            if (this.uploadProjects == null) {
                this.uploadProjects = new ArrayList<>()
            }
            for (String projectName : projectNames) {
                if (projectName != null && !this.uploadProjects.contains(projectName)) {
                    this.uploadProjects.add(projectName)
                }
            }
        }
    }

    void setUploadProjects(String... projectNames) {
        if (projectNames != null) {
            this.setUploadProjects(projectNames.toList())
        }
    }

    void uploadProjects(String... projectNames) {
        if (projectNames != null) {
            this.setUploadProjects(projectNames.toList())
        }
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    String getProjectVersion(Project project, Properties properties, boolean isRelease, String key) {
        Map<String, String> startParameterProjectProperties = project.getGradle().getStartParameter().getProjectProperties()
        if (startParameterProjectProperties.containsKey("version")) {
            return startParameterProjectProperties.get("version")
        }
        if (properties == null) {
            return null
        }
        String value = properties.getProperty(key)
        if (value == null) {
            return null
        }
        return isRelease ? value.replaceAll('-SNAPSHOT', '') : value
    }

    Map<String, String> getProjectVersions(Project project, boolean isRelease) {
        Properties properties = new Properties()
        File file = project.rootProject.file('gradle.properties')
        properties.load(file.newDataInputStream())
        Map<String, String> projectProperties = new HashMap<>()

        if (this.uploadProjects != null) {
            this.uploadProjects.each {
                String key = "POM_${it.replaceAll('-', '_')}_VERSION".toUpperCase()
                String value = getProjectVersion(project, properties, isRelease, key)
                if (value != null) {
                    projectProperties.put(key, value)
                }
            }
        }
        return projectProperties
    }

    StartParameter getProjectStartParameter(Project project, boolean isRelease) {
        StartParameter startParameterInstance = project.getGradle()
                .getStartParameter()
                .newInstance()
        Map<String, String> projectProperties = startParameterInstance.projectProperties

        Map<String, String> projectVersions = getProjectVersions(project, isRelease)
        projectVersions.each {
            String key, String value ->
                if (!projectProperties.containsKey(key)) {
                    projectProperties.put(key, value)
                }
        }

        return startParameterInstance
    }

    @SuppressWarnings(["GrMethodMayBeStatic", "GroovyUnusedDeclaration"])
    String getProjectUploadTask(Project project, String projectName, boolean isRelease) {
        return "${projectName.startsWith(Project.PATH_SEPARATOR) ? projectName : "$Project.PATH_SEPARATOR$projectName"}${projectName.endsWith(Project.PATH_SEPARATOR) ? '' : Project.PATH_SEPARATOR}${isRelease ? 'uploadRelease' : 'uploadSnapshot'}" as String
    }

    List<String> getProjectUploadTasks(Project project, boolean isRelease) {
        List<String> projectTasks = new ArrayList<>()
        if (this.uploadProjects != null) {
            this.uploadProjects.each {
                String taskName = getProjectUploadTask(project, it, isRelease)
                if (taskName != null) {
                    projectTasks.add(taskName)
                }
            }
        }
        return projectTasks
    }

    String readPropertyFromLocalProperties(String key, String defaultValue) {
        Properties properties = new Properties()
        File localFile = project.rootProject.file('local.properties')
        if (localFile.exists()) {
            properties.load(localFile.newDataInputStream())
        }
        return (properties != null && properties.containsKey(key)) ? properties.getProperty(key, defaultValue) : defaultValue
    }

    String readPropertyFromProject(String key, String defaultValue) {
        return project.hasProperty(key) ? project.ext[key] : readPropertyFromLocalProperties(key, defaultValue)
    }

    String readPropertyFromProject(String key) {
        return project.hasProperty(key) ? project.ext[key] : readPropertyFromLocalProperties(key, 'true')
    }

}
