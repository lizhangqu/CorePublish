package io.github.lizhangqu

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.CopySpec
import org.gradle.api.file.FileCollection
import org.gradle.util.GFileUtils

class NativeCompilePlugin implements Plugin<Project> {
    //默认值
    static final String DEFAULT_CLASSIFIER = "armeabi"
    //支持的abi列表
    static
    final List<String> ABI = Arrays.asList("armeabi", "armeabi-v7a", "arm64-v8a", "x86", "x86_64", "mips", "mips64")
    String defaultClassifier = DEFAULT_CLASSIFIER

    private Properties properties = new Properties()

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

    @Override
    void apply(Project project) {
        def extension = project.getExtensions().findByName('android')
        if (extension == null) {
            return
        }
        //read local properties
        loadLocalProperties(project)
        defaultClassifier = getClassifier(project)
        def mainSourceSet = extension.getSourceSets().getByName(extension.getDefaultConfig().getName())
        Set<File> jniLibsDirs = mainSourceSet.getJniLibs().getSrcDirs()
        if (jniLibsDirs.size() == 0) {
            mainSourceSet.getJniLibs().srcDirs(project.file("src/main/jniLibs"))
        }
        File jniLibsDir = mainSourceSet.getJniLibs().getSrcDirs().toList().get(0)
        createConfiguration(project, 'nativeCompile', jniLibsDir)
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    def readPropertyFromLocalPropertiesOrThrow(Project project, String key, String defaultValue, boolean throwIfNull) {
        def property = properties != null ? properties.getProperty(key, defaultValue) : defaultValue
        if (property == null && throwIfNull) {
            throw new GradleException("you must config ${key} in properties. Like config project.ext.${key} , add ${key} in gradle.properties or add ${key} in local.properties which locates on root project dir")
        }
        return property
    }

    def getClassifier(Project project) {
        return project.hasProperty('native_compile_classifier') ? project.ext.native_compile_classifier : readPropertyFromLocalPropertiesOrThrow(project, 'native_compile_classifier', DEFAULT_CLASSIFIER, false)
    }

    @SuppressWarnings("GrMethodMayBeStatic")
    void createConfiguration(Project project, String configurationName, File jniLibsDir) {
        Configuration nativeCompileConfiguration = project.getConfigurations().create(configurationName) { Configuration nativeCompileConfiguration ->
            //禁止传递依赖
            nativeCompileConfiguration.setTransitive(false)
            nativeCompileConfiguration.resolutionStrategy {
                cacheChangingModulesFor(0, 'seconds')
                cacheDynamicVersionsFor(5, 'minutes')
            }
        }

        project.afterEvaluate {
            nativeCompileConfiguration.getDependencies().each { Dependency nativeDependency ->
                FileCollection collection = nativeCompileConfiguration.fileCollection(nativeDependency).filter { File file ->
                    boolean filter = file.getName().endsWith(".so")
                    if (!filter) {
                        project.logger.error("ignore file ${file} becaues extension is not .so")
                    }
                    //返回so文件
                    return filter
                }
                //遍历
                collection.files.each { File srcFile ->
                    //文件后缀
                    String suffix = srcFile.getName().substring(srcFile.getName().lastIndexOf("."))
                    String classifierSuffix = srcFile.getName().substring(srcFile.getName().lastIndexOf("-")) - "-"
                    //依赖classifier
                    String classifier = classifierSuffix - suffix
                    //如果classifier为空，则默认使用配置中的
                    if (classifier == null || classifier.length() == 0 || !ABI.contains(classifier)) {
                        classifier = defaultClassifier
                    }
                    //目标目录
                    File destDir = new File(jniLibsDir, classifier)
                    //目标文件名
                    String destFileName = "${nativeDependency.getName().startsWith('lib') ? '' : 'lib'}${nativeDependency.getName()}.so"
                    //删除旧文件
                    GFileUtils.deleteQuietly(new File(destDir, destFileName))
                    //拷贝源文件到目标文件
                    project.copy { CopySpec copySpec ->
                        copySpec.from(srcFile)
                        copySpec.into(destDir)
                        copySpec.rename(srcFile.getName(), destFileName)
                    }
                }
            }
        }
    }
}