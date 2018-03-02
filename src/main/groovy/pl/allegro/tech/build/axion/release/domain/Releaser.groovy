package pl.allegro.tech.build.axion.release.domain

import com.github.zafarkhaja.semver.Version
import pl.allegro.tech.build.axion.release.domain.hooks.ReleaseHooksRunner
import pl.allegro.tech.build.axion.release.domain.logging.ReleaseLogger
import pl.allegro.tech.build.axion.release.domain.properties.Properties
import pl.allegro.tech.build.axion.release.domain.scm.ScmPushResult
import pl.allegro.tech.build.axion.release.domain.scm.ScmService

class Releaser {

    private final ReleaseLogger logger = ReleaseLogger.Factory.logger(Releaser)

    private final VersionService versionService

    private final ScmService repository

    private final ReleaseHooksRunner hooksRunner

    Releaser(VersionService versionService, ScmService repository, ReleaseHooksRunner hooksRunner) {
        this.versionService = versionService
        this.repository = repository
        this.hooksRunner = hooksRunner
    }

    Optional<String> release(Properties rules) {
        VersionContext versionContext = versionService.currentVersion(rules.version, rules.tag, rules.nextVersion)
        Version version = versionContext.version

        if (versionContext.snapshot) {
            String tagName = rules.tag.serialize(rules.tag, version.toString())

            hooksRunner.runPreReleaseHooks(rules.hooks, rules, versionContext, version)

            logger.quiet("Creating tag: $tagName")
            repository.tag(tagName)

            hooksRunner.runPostReleaseHooks(rules.hooks, rules, versionContext, version)
            return Optional.of(tagName)
        } else {
            logger.quiet("Working on released version ${version}, nothing to release")
            return Optional.empty()
        }
    }

    ScmPushResult releaseAndPush(Properties rules) {
        Optional<String> releasedTagName = release(rules)

        if (releasedTagName.isPresent()) {
            ScmPushResult result = pushRelease(releasedTagName.get())
            if (!result.success) {
                releasedTagName.ifPresent { rollbackRelease(it) }
            }
            return result
        } else {
            //TODO what kind of ScmPushResult should be returned if nothing is pushed?
            return new ScmPushResult(true, Optional.empty())
        }
    }

    ScmPushResult pushRelease(String releasedTagName) {
        return repository.push(releasedTagName)
    }

    private void rollbackRelease(String tagName) {
        logger.quiet("Removing tag: $tagName")
        repository.dropTag(tagName)
        logger.quiet("Tag $tagName removed")
    }
}
