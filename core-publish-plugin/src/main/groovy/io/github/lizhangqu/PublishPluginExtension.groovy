package io.github.lizhangqu

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

class PublishPluginExtension {
    Project project
    List<Dependency> exclude = new ArrayList<Dependency>()
    List<Dependency> force = new ArrayList<Dependency>()

    PublishPluginExtension(Project project) {
        this.project = project
    }

    void exclude(Object... dependencyNotations) {
        if (dependencyNotations != null && dependencyNotations.length > 0) {
            for (Object dependencyNotation : dependencyNotations) {
                if (dependencyNotation != null) {
                    Dependency dependency = project.getDependencies().create(dependencyNotation)
                    if (dependency != null && !exclude.contains(dependency)) {
                        exclude.add(dependency)
                    }
                }
            }
        }
    }

    boolean shouldExcludeDependency(String group, String name, String version) {
        if (exclude != null && exclude.size() > 0) {
            for (Dependency dependency : exclude) {
                if (dependency != null && dependency.getGroup() == group && dependency.getName() == name) {
                    return true
                }
            }
        }
        return false
    }

    void force(Object... dependencyNotations) {
        if (dependencyNotations != null && dependencyNotations.length > 0) {
            for (Object dependencyNotation : dependencyNotations) {
                if (dependencyNotation != null) {
                    Dependency dependency = project.getDependencies().create(dependencyNotation)
                    if (dependency != null && !force.contains(dependency)) {
                        force.add(dependency)
                    }
                }
            }
        }
    }

    Dependency shouldForceDependency(String group, String name, String version) {
        if (force != null && force.size() > 0) {
            for (Dependency dependency : force) {
                if (dependency != null && dependency.getGroup() == group && dependency.getName() == name) {
                    return dependency
                }
            }
        }
        return null
    }
}
