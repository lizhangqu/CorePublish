package io.github.lizhangqu

import org.gradle.api.*

/**
 * 添加前缀task，默认不添加，如果配置了ENABLE_PREFIX_TASK=true，则添加
 */
class PrefixTaskPlugin implements Plugin<Project> {
    private Properties properties = new Properties()

    @Override
    void apply(Project project) {
        //read local properties
        loadLocalProperties(project)

        def enablePrefixTask = getEnablePrefixTask(project)
        if (enablePrefixTask != null && String.valueOf(enablePrefixTask).equalsIgnoreCase("true")) {
            project.afterEvaluate {
                String pomGroupId = getPomGroupId(project)
                String pomArchivesBaseName = getPomArtifactId(project)

                project.tasks.getNames().each {
                    def originalTask = project.tasks.findByName(it)
                    if (originalTask) {
                        def taskNameWithPrefix = "[${pomGroupId}-${pomArchivesBaseName}]${originalTask.name}"
                        def taskWithPrefix = project.tasks.findByName(taskNameWithPrefix)
                        if (taskWithPrefix == null) {
                            project.task(dependsOn: originalTask, taskNameWithPrefix) {
                                setGroup(originalTask.group)
                                setDescription(originalTask.description)
                            }
                        }
                    }
                }
            }
        }
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
        def property = (properties != null && properties.containsKey(key)) ? properties.getProperty(key, defaultValue) : defaultValue
        if (property == null && throwIfNull) {
            throw new GradleException("you must config ${key} in properties. Like config project.ext.${key} , add ${key} in gradle.properties or add ${key} in local.properties which locates on root project dir")
        }
        return property
    }

    def getEnablePrefixTask(Project project) {
        return project.hasProperty('ENABLE_PREFIX_TASK') ? project.ext.ENABLE_PREFIX_TASK : readPropertyFromLocalPropertiesOrThrow(project, 'ENABLE_PREFIX_TASK', 'true', false)
    }

    def getPomGroupId(Project project) {
        return project.hasProperty('PROJECT_POM_GROUP_ID') ? project.ext.PROJECT_POM_GROUP_ID : readPropertyFromLocalProperties(project, 'PROJECT_POM_GROUP_ID', project.group.toString())
    }

    def getPomArtifactId(Project project) {
        return project.hasProperty('PROJECT_POM_ARTIFACT_ID') ? project.ext.PROJECT_POM_ARTIFACT_ID : readPropertyFromLocalProperties(project, 'PROJECT_POM_ARTIFACT_ID', getDefaultPomArtifactId(project))
    }

    def getDefaultPomArtifactId(Project project) {
        if (project.hasProperty('archivesBaseName')) {
            return project.archivesBaseName.toString()
        }
        return project.name.toString()
    }
}
