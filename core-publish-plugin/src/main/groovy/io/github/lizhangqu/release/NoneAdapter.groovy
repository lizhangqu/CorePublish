package io.github.lizhangqu.release

import org.gradle.api.Project

class NoneAdapter extends BaseScmAdapter {

    class NoneConfig {

    }

    NoneAdapter(Project project, Map<String, Object> attributes) {
        super(project, attributes)
    }

    @Override
    Object createNewConfig() {
        return new NoneConfig()
    }

    @Override
    boolean isSupported(File directory) {
        return true
    }

    @Override
    void init() {
        project.logger.error("init")
    }

    @Override
    void checkCommitNeeded() {
        project.logger.error("checkCommitNeeded")
    }

    @Override
    void checkUpdateNeeded() {
        project.logger.error("checkUpdateNeeded")
    }

    @Override
    void createReleaseTag(String message) {
        project.logger.error("createReleaseTag ${message}")
    }

    @Override
    void commit(String message) {
        project.logger.error("commit ${message}")
    }

    @Override
    void add(File file) {
        project.logger.error("add ${file}")
    }

    @Override
    void revert() {
        project.logger.error("revert")
    }

}
