package io.github.lizhangqu

import org.gradle.api.Project

/**
 * 添加前缀task，默认不添加，如果配置了ENABLE_PREFIX_TASK=true，则添加
 */
class PrefixTaskPlugin extends BasePropertiesPlugin {

    @Override
    void apply(Project project) {
        super.apply(project)
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

    private String getEnablePrefixTask(Project project) {
        return readPropertyFromProject(project, "ENABLE_PREFIX_TASK", 'false', false)
    }

    private String getPomGroupId(Project project) {
        return readPropertyFromProject(project, "PROJECT_POM_GROUP_ID", project.group.toString())
    }

    private String getPomArtifactId(Project project) {
        return readPropertyFromProject(project, "PROJECT_POM_ARTIFACT_ID", getDefaultPomArtifactId(project))
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private String getDefaultPomArtifactId(Project project) {
        if (project.hasProperty('archivesBaseName')) {
            return project.archivesBaseName.toString()
        }
        return project.name.toString()
    }
}
