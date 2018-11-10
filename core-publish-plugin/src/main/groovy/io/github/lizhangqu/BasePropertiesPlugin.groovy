package io.github.lizhangqu

import org.codehaus.groovy.runtime.MethodClosure
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * 基础父类插件，抽象读取配置函数
 */
class BasePropertiesPlugin implements Plugin<Project> {
    protected Project project
    protected Properties properties = new Properties()

    @Override
    void apply(Project project) {
        this.project = project
        loadLocalProperties(project)
    }

    private void loadLocalProperties(Project project) {
        try {
            File localFile = project.rootProject.file('local.properties')
            if (localFile.exists()) {
                properties.load(localFile.newDataInputStream())
            }
        } catch (Exception e) {
            println("load local properties failed msg:${e.message}")
        }
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    protected String readPropertyFromProject(Project project, String key, String defaultValue) {
        return readPropertyFromProject(project, key, defaultValue, true)
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    protected String readPropertyFromProject(Project project, String key, String defaultValue, boolean throwIfNull) {
        return project.hasProperty(key) && !(project.ext[key] instanceof MethodClosure) ? project.ext[key] : readPropertyFromLocalPropertiesOrThrow(project, key, defaultValue, throwIfNull)
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    protected String readPropertyFromProject(Project project, String key) {
        return readPropertyFromProject(project, key, true)
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    protected String readPropertyFromProject(Project project, String key, boolean throwIfNull) {
        return project.hasProperty(key) && !(project.ext[key] instanceof MethodClosure) ? project.ext[key] : readPropertyFromLocalPropertiesOrThrow(project, key, null, throwIfNull)
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    private String readPropertyFromLocalProperties(Project project, String key, String defaultValue) {
        return readPropertyFromLocalPropertiesOrThrow(project, key, defaultValue, true)
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    private String readPropertyFromLocalPropertiesOrThrow(Project project, String key, String defaultValue, boolean throwIfNull) {
        def property = (properties != null && properties.containsKey(key)) ? properties.getProperty(key, defaultValue) : defaultValue
        if (property == null && throwIfNull) {
            throw new GradleException("you must config ${key} in properties. Like config project.ext.${key} , add ${key} in gradle.properties or add ${key} in local.properties which locates on root project dir")
        }
        return property
    }
}
