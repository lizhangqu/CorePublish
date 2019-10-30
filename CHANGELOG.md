1.0.0
---------------
 
  - 支持maven上传
  - 支持bintray上传
  - 暂时不支持android gradle plugin 3.0 implementation 依赖
  
1.0.1
---------------
 
  - 升级android-maven-gradle-plugin版本到2.0

1.0.2
---------------
 
  - 升级android-maven-gradle-plugin版本降到1.5
  - 废弃uploadArchives
  
1.0.3
---------------
 
  - 修复SNAPSHOT版本判断错误的bug
  
1.0.4
---------------
 
  - 修复若干bug
  - 支持bintrayUpload release
 
1.0.5
---------------
 
  - 修复SNAPSHOT包上传失败
  - 支持主动设置project的group，archivesBaseName，version
  - 支持android gradle plugin 3.0.0
  
1.0.6
---------------
 
  - 修复静态变量导致的配置覆盖现象
  
1.0.7
---------------
 
  - 修复android gradle plugin 3.0.0 javadoc问题
  
1.0.8
---------------
 
  - 修复true/false转型失败
  
1.0.9
---------------
 
  - 1.0.8发布错误，重新发布
  
1.0.11
---------------
 
  - 修复发布依赖task依赖关系
  
1.0.12
---------------
 
  - 添加MavenPluginCompat，不使用AndroidMavenPlugin，支持gradle 2.10到gradle4.4
  - 添加nativeCompile插件
  - 不强制要求设置group,name,version
  - 修复javadoc，javasource配置执行的时机
  
1.2.0
---------------
 
  - 修复bintray发布bug

  
1.2.1
---------------
 
  - 修复native compile armeabi-v7a和arm64-v8a目录错误的bug
  
  
1.2.2
---------------
 
  - 支持tag模板group和archivesBaseName
  - 支持使用archivesBaseName代替name
  - 修改android默认buildTasks为assembleRelease
  
  
1.2.4
---------------
  - 修复release插件编译错误
  
  
1.2.5
---------------
  - 修复release插件bug，命令行版本和文件版本不一致不会产生提交
  - release插件默认带递增，publish插件中移除
  
1.2.6
---------------
  - 支持多module项目结构发布

  
1.2.7
---------------
  - 修复发布错误
  
1.2.9
---------------
  - 支持exclude依赖发布
  - 支持providedAar
  - 支持前缀task
  - 升级release插件，支持携带上一次发布的版本
  
1.3.0
---------------
  - 修复guava版本过高导致的api不兼容问题

1.3.8
---------------
  - 若干优化
  
1.3.9
---------------
  - 适配gradle 5.0
  
1.4.0
---------------
  - 增加健壮性
  - flutter发布支持
  
1.4.0
---------------
  - local.properties中属性的优先级提升
