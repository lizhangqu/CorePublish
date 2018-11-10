package io.github.lizhangqu

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedDependency


/**
 * bundle与宿主依赖冲突解决
 */
class ResolveDependencyPlugin implements Plugin<Project> {
    private Project project
    private Map<String, Map<String, String>> configurationDependencyMap = new HashMap<>()

    @Override
    void apply(Project project) {
        this.project = project

        //宿主project path名
        String hostProjectPath = project.hasProperty("hostProject") ? project.ext["hostProject"] : null

        if (hostProjectPath == null || hostProjectPath.length() == 0) {
            throw new GradleException("hostProject 必须设置，如:app")
        }

        //校验宿主是否存在
        Project hostProject = project.project(hostProjectPath)
        if (hostProject == null) {
            //正常来说，走不到这里，gradle自己会抛异常
            throw new GradleException("hostProject 不存在，请确保配置正确，如:app")
        }

        //配置评估依赖宿主
        project.evaluationDependsOn(hostProjectPath)

        //收集宿主依赖
        collectHostConfigurationDependency(hostProject)

        //解决插件依赖冲突
        resolveBundleConfigurationDependency()


    }

    /**
     * 收集宿主依赖
     */
    private collectHostConfigurationDependency(Project hostProject) {
        hostProject.getConfigurations().all(new Action<Configuration>() {
            @Override
            void execute(Configuration configuration) {
                String configurationName = configuration.getName()
                Map<String, String> dependenciesMap = configurationDependencyMap.get(configurationName)
                if (dependenciesMap == null) {
                    dependenciesMap = new HashMap<>()
                    configurationDependencyMap.put(configurationName, dependenciesMap)
                }
                // 克隆一份 configuration，根据官方文档，克隆之后是 unResolve 的
                // 如果是 project依赖则忽略
                Configuration copyConfiguration = configuration.copyRecursive { def dependency ->
                    if (dependency instanceof ProjectDependency) {
                        return false
                    }
                    return true
                }
                // resolve 依赖，下载或者从缓存中解析依赖树，阻塞方法
                //noinspection UnnecessaryQualifiedReference
                try {
                    //gradle<=2.14.1没有此函数
                    copyConfiguration.setCanBeResolved(true)
                } catch (groovy.lang.MissingMethodException e) {

                }

                // 获取 宿主 中的依赖
                copyConfiguration.resolvedConfiguration.getFirstLevelModuleDependencies().each {
                    dfsGetDependencies(it, dependenciesMap)
                }
            }
        })
    }

    /**
     * 递归获取依赖
     */
    def dfsGetDependencies(ResolvedDependency dependency, Map<String, String> dependenciesMap) {
        String key = "${dependency.moduleGroup}:${dependency.moduleName}".toString().trim()
        String version = dependency.moduleVersion
        dependenciesMap.put(key, version)
        dependency.getChildren().each {
            dfsGetDependencies(it, dependenciesMap)
        }
    }

    /**
     * 重写依赖
     */
    private HashMap<String, String> rewriteConfigurationDependencies(String configurationName, HashMap<String, String> dependenciesMap) {
        if (dependenciesMap == null) {
            return dependenciesMap
        }

        //按优先级进行覆盖
        List<String> configurations = new ArrayList<>()
        configurations.addAll(Arrays.asList('compile', '_releaseCompile', '_releaseApk', 'releaseCompileClasspath', 'releaseRuntimeClasspath'))
        configurations.each {
            project.logger.info("rewrite configuration ${configurationName} with ${it}")
            Map<String, String> hostCompileDependenciesMap = configurationDependencyMap.get(it)
            if (hostCompileDependenciesMap != null && hostCompileDependenciesMap.size() > 0) {
                dependenciesMap.putAll(hostCompileDependenciesMap)
            }
        }
        return dependenciesMap

    }

    /**
     * 解决宿主依赖冲突
     */
    private resolveBundleConfigurationDependency() {
        project.getConfigurations().all(new Action<Configuration>() {
            @Override
            void execute(Configuration configuration) {
                String configurationName = configuration.getName()

                Map<String, String> dependenciesMap = configurationDependencyMap.get(configurationName)

                Map<String, String> hostDependenciesMap = new HashMap<>()
                if (dependenciesMap != null && dependenciesMap.size() > 0) {
                    hostDependenciesMap.putAll(dependenciesMap)
                }

                //如果是provided/providedAar依赖，则使用宿主其他可选依赖覆盖
                if (configurationName == 'provided' || configurationName == 'providedAar' || configurationName == 'compileOnly') {
                    hostDependenciesMap = rewriteConfigurationDependencies(configurationName, hostDependenciesMap)
                }

                //_releaseCompile如果无依赖，则覆盖，这个问题是由plugin-zeus的localBundleCompile造成，会出现循环配置依赖
                if (configurationName.startsWith('_') && configurationName.endsWith('Compile') && (hostDependenciesMap == null || hostDependenciesMap.size() == 0)) {
                    hostDependenciesMap = rewriteConfigurationDependencies(configurationName, hostDependenciesMap)
                }
                //同上
                if (configurationName.endsWith('CompileClasspath') && (hostDependenciesMap == null || hostDependenciesMap.size() == 0)) {
                    hostDependenciesMap = rewriteConfigurationDependencies(configurationName, hostDependenciesMap)
                }
                //同上
                if (configurationName.endsWith('RuntimeClasspath') && (hostDependenciesMap == null || hostDependenciesMap.size() == 0)) {
                    hostDependenciesMap = rewriteConfigurationDependencies(configurationName, hostDependenciesMap)
                }



                if (hostDependenciesMap == null || hostDependenciesMap.size() == 0) {
                    //只有有依赖的时候才打印，否则忽略
                    configuration.resolutionStrategy.eachDependency {
                        project.logger.error("宿主缺少 configuration: ${configurationName} 使用插件依赖 ${it.target.group}:${it.target.name}:${it.target.version}")
                    }
                    return
                }

                printlnResolvedDependency(configurationName, configuration, hostDependenciesMap)

                //强制指定bundle版本
                configuration.resolutionStrategy.eachDependency {
                    String key = "${it.target.group}:${it.target.name}".toString().trim()
                    def hostVersion = hostDependenciesMap.get(key)
                    if (hostVersion != null && hostVersion != it.target.version) {
                        // 根据宿主更新 bundle 中的依赖版本
                        project.logger.info("更新插件 ${project.name} ${configurationName} configuration 依赖: ${key}:${it.target.version} -> $hostVersion")
                        it.useVersion(hostVersion)
                    }
                }
            }
        })
    }

    private void printlnResolvedDependency(String configurationName, Configuration configuration, Map<String, String> hostDependenciesMap) {
        Configuration copyConfiguration = configuration.copyRecursive()
        //noinspection UnnecessaryQualifiedReference
        try {
            //gradle<=2.14.1没有此函数
            copyConfiguration.setCanBeResolved(true)
        } catch (groovy.lang.MissingMethodException e) {

        }


        Set<String> resolvedDependencies = new HashSet<>()
        copyConfiguration.resolvedConfiguration.getFirstLevelModuleDependencies().each {
            dfsPrintlnResolvedDependencies(configurationName, it, hostDependenciesMap, resolvedDependencies)
        }
    }

    private void dfsPrintlnResolvedDependencies(String configurationName, ResolvedDependency dependency, Map<String, String> hostDependenciesMap, Set<String> resolvedDependencies) {
        String key = "${dependency.moduleGroup}:${dependency.moduleName}".toString().trim()
        String version = dependency.moduleVersion
        def hostVersion = hostDependenciesMap.get(key)
        if (hostVersion != null && hostVersion != version && !resolvedDependencies.contains(key)) {
            resolvedDependencies.add(key)
            project.logger.lifecycle("更新插件 ${project.name} ${configurationName} configuration 依赖: ${key}:${version} -> ${hostVersion}")
        }
        dependency.getChildren().each {
            dfsPrintlnResolvedDependencies(configurationName, it, hostDependenciesMap, resolvedDependencies)
        }
    }
}


