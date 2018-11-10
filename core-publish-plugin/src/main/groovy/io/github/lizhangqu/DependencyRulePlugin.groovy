package io.github.lizhangqu

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedDependencyResult
import org.gradle.util.GFileUtils

/**
 * 依赖规则检测
 */
class DependencyRulePlugin extends BasePropertiesPlugin {

    @Override
    void apply(Project project) {
        super.apply(project)
        checkDependency(project)
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    private void checkDependency(Project project) {
        List<RuleItem> rules = getRules()
        if (rules == null || rules.size() == 0) {
            return
        }
        project.afterEvaluate {
            Map<Configuration, List<String>> dependencyExceptions = new HashMap<>()
            project.getConfigurations().all { Configuration configuration ->
                try {
                    //低版本没有此方法
                    if (!configuration.isCanBeResolved()) {
                        return
                    }
                } catch (Throwable e) {

                }

                //适配maven发布
                if (configuration.getName() == 'archives') {
                    return
                }
                //适配proguard插件
                if (configuration.getName() == 'remoteBundleCompile') {
                    return
                }
                //适配kotlin插件
                if (configuration.getName() == 'kotlinCompilerPluginClasspath') {
                    return
                }

                //此处会提前解析所有依赖，所以有部分依赖必须放过，如archives,remoteBundleCompile,kotlinCompilerPluginClasspath等
                configuration.getIncoming().getResolutionResult().getAllDependencies().each { dependencyResult ->
                    if (dependencyResult instanceof DefaultUnresolvedDependencyResult) {
                        return
                    }
                    if (dependencyResult instanceof DefaultResolvedDependencyResult) {
                        def module = dependencyResult.getSelected().getModuleVersion()
                        rules.each { RuleItem ruleItem ->
                            if (ruleItem.group == module.group && ruleItem.artifactId == module.name) {
                                String exception = checkVersionRule(ruleItem, module.version)
                                if (exception != null) {
                                    List<String> exceptions = dependencyExceptions.get(configuration)
                                    if (exceptions == null) {
                                        exceptions = new ArrayList<>()
                                        dependencyExceptions.put(configuration, exceptions)
                                    }
                                    exceptions.add("${exception} 依赖来自 ${dependencyResult}")
                                }
                            }
                        }
                    }
                }
            }

            if (dependencyExceptions != null && dependencyExceptions.size() > 0) {
                dependencyExceptions.each { key, List<String> list ->
                    project.logger.error("----------------------->${key}")
                    list.each { value ->
                        project.logger.error("${value}")
                    }
                }
                throw new GradleException("版本规则校验不满足，构建失败")
            }
        }
    }

    /**
     * 0代表相等，1代表version1大于version2，-1代表version1小于version2
     */
    int compareVersion(String version1, String version2) {
        try {
            if (version1.equals(version2)) {
                return 0
            }
            String[] version1Array = version1.split("\\.")
            String[] version2Array = version2.split("\\.")
            int index = 0
            int minLen = Math.min(version1Array.length, version2Array.length)
            int diff = 0
            while (index < minLen
                    && (diff = Integer.parseInt(version1Array[index])
                    - Integer.parseInt(version2Array[index])) == 0) {
                index++
            }
            if (diff != 0) {
                return diff > 0 ? 1 : -1
            }
            for (int i = index; i < version1Array.length; i++) {
                if (Integer.parseInt(version1Array[i]) > 0) {
                    return 1
                }
            }

            for (int i = index; i < version2Array.length; i++) {
                if (Integer.parseInt(version2Array[i]) > 0) {
                    return -1
                }
            }
        } catch (Exception e) {
            return -1
        }
        return 0
    }


    private String checkVersionRule(RuleItem ruleItem, String selectedVersion) {
        if ("=" == ruleItem.rule) {
            if (compareVersion(selectedVersion, ruleItem.version) != 0) {
                return "${ruleItem.group}:${ruleItem.artifactId}:${selectedVersion} 规则不满足，规则为强制要求版本号 ${ruleItem.rule} ${ruleItem.version} 描述：${(ruleItem.description == null || ruleItem.description.length() == 0) ? '无' : ruleItem.description}".toString()
            }
        } else if ("!=" == ruleItem.rule) {
            if (ruleItem.version == "*") {
                return "${ruleItem.group}:${ruleItem.artifactId}:${selectedVersion} 规则不满足，规则为强制要求版本号 ${ruleItem.rule} ${ruleItem.version}，即依赖必须强制移除. 描述：${(ruleItem.description == null || ruleItem.description.length() == 0) ? '无' : ruleItem.description}".toString()
            } else if (compareVersion(selectedVersion, ruleItem.version) == 0) {
                return "${ruleItem.group}:${ruleItem.artifactId}:${selectedVersion} 规则不满足，规则为强制要求版本号 ${ruleItem.rule} ${ruleItem.version} 描述：${(ruleItem.description == null || ruleItem.description.length() == 0) ? '无' : ruleItem.description}".toString()
            }
        } else if (">=" == ruleItem.rule) {
            boolean snapshot = false
            if (selectedVersion.contains("-SNAPSHOT")) {
                snapshot = true
            }
            int compareResult = compareVersion(selectedVersion - "-SNAPSHOT", ruleItem.version)
            if (compareResult < 0 || (compareResult == 0 && snapshot)) {
                return "${ruleItem.group}:${ruleItem.artifactId}:${selectedVersion} 规则不满足，规则为强制要求版本号 ${ruleItem.rule} ${ruleItem.version} 描述：${(ruleItem.description == null || ruleItem.description.length() == 0) ? '无' : ruleItem.description}".toString()
            }
        } else if (">" == ruleItem.rule) {
            boolean snapshot = false
            if (selectedVersion.contains("-SNAPSHOT")) {
                snapshot = true
            }
            int compareResult = compareVersion(selectedVersion - "-SNAPSHOT", ruleItem.version)
            if (compareResult <= 0 || (compareResult == 0 && snapshot)) {
                return "${ruleItem.group}:${ruleItem.artifactId}:${selectedVersion} 规则不满足，规则为强制要求版本号 ${ruleItem.rule} ${ruleItem.version} 描述：${(ruleItem.description == null || ruleItem.description.length() == 0) ? '无' : ruleItem.description}".toString()
            }
        }
        return null
    }

    private List<RuleItem> getRules() {
        File ruleFile = getRuleFile()
        if (ruleFile == null) {
            return null
        }

        Gson gson = new Gson()
        List<RuleItem> rules = gson.fromJson(ruleFile.text, new TypeToken<List<RuleItem>>() {
        }.getType())
        return rules
    }

    private File getRuleFile() {
        String rulePath = readPropertyFromProject(project, "rule", null, false)
        if (rulePath == null || rulePath.length() == 0) {
            return null
        }
        if (rulePath.startsWith("/")) {
            rulePath = "file://" + rulePath
        }
        File destFile = new File(project.buildDir, 'rule.json')
        URL url = new URL(rulePath)
        GFileUtils.copyURLToFile(url, destFile)
        if (!destFile.exists() || !destFile.isFile()) {
            throw new GradleException("版本号规则文件不存在")
        }
        return destFile
    }


    class RuleItem {
        public String group
        public String artifactId
        public String version
        public String rule
        public String description

        @Override
        public String toString() {
            return "RuleItem{" +
                    "group='" + group + '\'' +
                    ", artifactId='" + artifactId + '\'' +
                    ", version='" + version + '\'' +
                    ", rule='" + rule + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }
    }

}
